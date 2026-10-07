package vn.khoibep.rms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import vn.khoibep.rms.entity.InventoryItem;
import vn.khoibep.rms.entity.RecipeLine;
import vn.khoibep.rms.enums.MovementType;

/** Dish recipes and what dishes used from stock (FR-09.8 → FR-09.10). */
public final class RecipeDtos {

    private RecipeDtos() {
    }

    /** BR-38: more than zero of the ingredient's unit, for one portion. */
    public record RecipeLineRequest(@NotNull Long inventoryItemId,
                                    @NotNull @DecimalMin(value = "0", inclusive = false)
                                    @Digits(integer = 9, fraction = 3) BigDecimal quantity) {
    }

    /** The whole recipe of a dish; no lines takes the recipe away. */
    public record RecipeRequest(@NotNull @Size(max = 30) List<@Valid RecipeLineRequest> lines) {
    }

    public record RecipeLineDto(Long inventoryItemId, String itemName, String unit, BigDecimal quantity) {
        public static RecipeLineDto from(RecipeLine l) {
            return new RecipeLineDto(l.getItem().getId(), l.getItem().getName(), l.getItem().getUnit(), l.getQuantity());
        }
    }

    public record RecipeDto(Long menuItemId, List<RecipeLineDto> lines) {
        public static RecipeDto from(Long menuItemId, List<RecipeLine> lines) {
            return new RecipeDto(menuItemId, lines.stream().map(RecipeLineDto::from).toList());
        }
    }

    /**
     * FR-09.10, in the ingredient's unit: what dishes used, net of what came back, and what was moved out by hand,
     * both positive when stock left; what stock counts changed, negative when the count found less.
     */
    public record UsageDto(Long inventoryItemId, String name, String unit, BigDecimal used, BigDecimal removed,
                           BigDecimal adjusted) {
        public static UsageDto from(InventoryItem item, Map<MovementType, BigDecimal> totals) {
            return new UsageDto(item.getId(), item.getName(), item.getUnit(),
                    totals.getOrDefault(MovementType.SALE, BigDecimal.ZERO).negate(),
                    totals.getOrDefault(MovementType.OUT, BigDecimal.ZERO).negate(),
                    totals.getOrDefault(MovementType.ADJUST, BigDecimal.ZERO));
        }
    }
}
