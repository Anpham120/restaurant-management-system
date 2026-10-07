package vn.khoibep.rms.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.util.DateRange;
import vn.khoibep.rms.dto.RecipeDtos.UsageDto;
import vn.khoibep.rms.entity.InventoryItem;
import vn.khoibep.rms.entity.StockMovement;
import vn.khoibep.rms.enums.MovementType;
import vn.khoibep.rms.repository.InventoryItemRepository;
import vn.khoibep.rms.repository.RecipeLineRepository.Portion;
import vn.khoibep.rms.repository.RecipeLineRepository;
import vn.khoibep.rms.repository.StockMovementRepository.Total;
import vn.khoibep.rms.repository.StockMovementRepository;

/** FR-09.9, FR-09.10, BR-38: dishes sent to the kitchen take their ingredients from stock by their recipes. */
@Service
@RequiredArgsConstructor
public class StockUsageService {

    /** One look covers at most about a quarter. */
    static final int MAX_DAYS = 92;

    /** A line of an order on its way to the kitchen; the note names the table, the order and the dish. */
    public record SentDish(Long orderItemId, Long menuItemId, int quantity, String note) {
    }

    private final RecipeLineRepository recipes;
    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final Clock clock;

    /** Joins the transaction that sends the dishes. A dish without a recipe takes nothing; stock may go below zero. */
    @Transactional
    public void use(List<SentDish> dishes, Long employeeId) {
        Map<Long, List<Portion>> recipeOf = recipes.findPortions(dishes.stream().map(SentDish::menuItemId).toList())
                .stream().collect(Collectors.groupingBy(Portion::getMenuItemId));
        Map<Long, InventoryItem> locked = lock(recipeOf.values().stream().flatMap(List::stream)
                .map(Portion::getInventoryItemId));
        for (SentDish dish : dishes) {
            for (Portion portion : recipeOf.getOrDefault(dish.menuItemId(), List.of())) {
                InventoryItem item = locked.get(portion.getInventoryItemId());
                BigDecimal used = portion.getQuantity().multiply(BigDecimal.valueOf(dish.quantity()));
                item.use(used);
                movements.save(StockMovement.forDish(item, used.negate(), dish.orderItemId(), dish.note(), employeeId));
            }
        }
    }

    /** A dish cancelled before cooking gives back what it took, whatever its recipe says now. */
    @Transactional
    public void giveBack(Long orderItemId, String note, Long employeeId) {
        Map<Long, BigDecimal> taken = movements.totalsForDish(orderItemId).stream()
                .filter(t -> t.getQuantityChange().signum() != 0)
                .collect(Collectors.toMap(Total::getInventoryItemId, Total::getQuantityChange));
        // What the dish took is a negative change, so using that amount puts it back.
        lock(taken.keySet().stream()).forEach((id, item) -> {
            item.use(taken.get(id));
            movements.save(StockMovement.forDish(item, taken.get(id).negate(), orderItemId, note, employeeId));
        });
    }

    /** Per ingredient, over Vietnam dates: what dishes used, what was moved out by hand, what counts changed. */
    @Transactional(readOnly = true)
    public List<UsageDto> usage(LocalDate from, LocalDate to) {
        DateRange.check(from, to, MAX_DAYS);
        ZoneId zone = clock.getZone();
        Map<Long, Map<MovementType, BigDecimal>> byItem = new HashMap<>();
        movements.totalsBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant(),
                        EnumSet.of(MovementType.SALE, MovementType.OUT, MovementType.ADJUST))
                .forEach(t -> byItem.computeIfAbsent(t.getInventoryItemId(), id -> new EnumMap<>(MovementType.class))
                        .put(t.getType(), t.getQuantityChange()));
        return items.findByIdInOrderByNameAsc(byItem.keySet()).stream()
                .map(item -> UsageDto.from(item, byItem.get(item.getId())))
                .toList();
    }

    /** In id order, so two orders sending dishes at once cannot wait on each other. */
    private Map<Long, InventoryItem> lock(Stream<Long> ids) {
        Map<Long, InventoryItem> locked = new TreeMap<>();
        ids.distinct().sorted().forEach(id -> locked.put(id, items.findByIdForUpdate(id).orElseThrow()));
        return locked;
    }
}
