package com.restaurant.core.menu.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.menu.dto.CategoryRequest;
import com.restaurant.core.menu.dto.CategoryResponse;
import com.restaurant.core.menu.dto.MenuItemRequest;
import com.restaurant.core.menu.dto.MenuItemResponse;
import com.restaurant.core.menu.dto.RecipeRequest;
import com.restaurant.core.menu.dto.RecipeResponse;
import com.restaurant.core.menu.repository.MenuCatalogRepository;

/** Danh mục, món, công thức. Owner: Lê Anh. */
@Service
public class MenuCatalogService {

    private final MenuCatalogRepository repo;

    public MenuCatalogService(MenuCatalogRepository repo) {
        this.repo = repo;
    }

    public List<CategoryResponse> categories(boolean activeOnly) {
        return repo.findCategories(activeOnly);
    }

    public CategoryResponse createCategory(CategoryRequest r) {
        long id = repo.insertCategory(r.name().strip(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        return repo.findCategory(id).orElseThrow();
    }

    public CategoryResponse updateCategory(long id, CategoryRequest r) {
        if (repo.updateCategory(id, r.name().strip(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active()) == 0) {
            throw notFound("Không tìm thấy danh mục " + id);
        }
        return repo.findCategory(id).orElseThrow();
    }

    public List<MenuItemResponse> items(Long categoryId, boolean activeOnly) {
        return repo.findItems(categoryId, activeOnly);
    }

    public MenuItemResponse item(long id) {
        return repo.findItem(id).orElseThrow(() -> notFound("Không tìm thấy món " + id));
    }

    public MenuItemResponse createItem(MenuItemRequest r) {
        return item(repo.insertItem(r.categoryId(), r.name().strip(), r.price(), r.imageUrl(), r.active() == null || r.active()));
    }

    public MenuItemResponse updateItem(long id, MenuItemRequest r) {
        if (repo.updateItem(id, r.categoryId(), r.name().strip(), r.price(), r.imageUrl(), r.active() == null || r.active()) == 0) {
            throw notFound("Không tìm thấy món " + id);
        }
        return item(id);
    }

    /**
     * Tạo công thức version mới và đặt làm hiện hành (nhiều câu lệnh → một transaction). Version cũ giữ nguyên để các
     * thực đơn ngày đã dùng nó không đổi; thực đơn ngày thêm món sau đó sẽ dùng version mới.
     */
    @Transactional
    public RecipeResponse createRecipe(long menuItemId, RecipeRequest r) {
        if (!repo.lockItem(menuItemId)) {
            throw notFound("Không tìm thấy món " + menuItemId);
        }
        int version = repo.nextRecipeVersion(menuItemId);
        repo.unsetCurrentRecipe(menuItemId);
        long recipeId = repo.insertRecipe(menuItemId, version);
        for (RecipeRequest.Ingredient i : r.ingredients()) {
            repo.insertRecipeIngredient(recipeId, i.kitchenIngredientTypeId(), i.quantityPerServing());
        }
        return new RecipeResponse(recipeId, menuItemId, version, true, repo.findRecipeLines(recipeId));
    }

    public List<RecipeResponse> recipes(long menuItemId) {
        item(menuItemId);
        return repo.findRecipes(menuItemId).stream()
                .map(h -> new RecipeResponse(h.id(), h.menuItemId(), h.version(), h.current(), repo.findRecipeLines(h.id())))
                .toList();
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
