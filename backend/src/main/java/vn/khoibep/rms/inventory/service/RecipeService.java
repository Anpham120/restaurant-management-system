package vn.khoibep.rms.inventory.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.khoibep.rms.common.exception.ApiException;
import vn.khoibep.rms.inventory.dto.RecipeDtos.RecipeDto;
import vn.khoibep.rms.inventory.dto.RecipeDtos.RecipeLineRequest;
import vn.khoibep.rms.inventory.dto.RecipeDtos.RecipeRequest;
import vn.khoibep.rms.inventory.entity.InventoryItem;
import vn.khoibep.rms.inventory.entity.RecipeLine;
import vn.khoibep.rms.inventory.repository.InventoryItemRepository;
import vn.khoibep.rms.inventory.repository.RecipeLineRepository;
import vn.khoibep.rms.menu.entity.MenuItem;
import vn.khoibep.rms.menu.repository.MenuItemRepository;

/** FR-09.8, BR-38: what one portion of each dish takes from stock. */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeLineRepository recipes;
    private final MenuItemRepository menuItems;
    private final InventoryItemRepository items;

    /** Dishes that have a recipe, each with its ingredients by name. */
    @Transactional(readOnly = true)
    public List<RecipeDto> list() {
        Map<Long, List<RecipeLine>> byDish = recipes.findAllWithItems().stream()
                .collect(Collectors.groupingBy(r -> r.getMenuItem().getId(), LinkedHashMap::new, Collectors.toList()));
        return byDish.entrySet().stream().map(e -> RecipeDto.from(e.getKey(), e.getValue())).toList();
    }

    /** The whole recipe at once. Dishes already sent keep what they took (BR-38). */
    @Transactional
    public RecipeDto replace(Long menuItemId, RecipeRequest request) {
        MenuItem dish = menuItems.findById(menuItemId).orElseThrow(() -> ApiException.notFound("Không tìm thấy món"));
        List<Long> ids = request.lines().stream().map(RecipeLineRequest::inventoryItemId).toList();
        if (ids.stream().distinct().count() < ids.size()) {
            throw ApiException.badRequest("Mỗi nguyên liệu chỉ ghi một dòng trong định lượng");
        }
        Map<Long, InventoryItem> byId = items.findAllById(ids).stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));
        if (byId.size() < ids.size()) {
            throw ApiException.notFound("Không tìm thấy nguyên liệu");
        }
        recipes.deleteRecipe(menuItemId);
        List<RecipeLine> lines = request.lines().stream()
                .map(l -> new RecipeLine(dish, byId.get(l.inventoryItemId()), l.quantity()))
                .toList();
        recipes.saveAll(lines);
        return RecipeDto.from(menuItemId, lines);
    }
}
