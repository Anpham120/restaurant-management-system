package com.restaurant.core.menu.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.menu.dto.request.CategoryRequest;
import com.restaurant.core.menu.dto.response.CategoryResponse;
import com.restaurant.core.menu.dto.request.MenuItemRequest;
import com.restaurant.core.menu.dto.response.MenuItemResponse;
import com.restaurant.core.menu.dto.request.RecipeRequest;
import com.restaurant.core.menu.dto.response.RecipeResponse;
import com.restaurant.core.menu.entity.MenuCategory;
import com.restaurant.core.menu.entity.MenuItem;
import com.restaurant.core.menu.entity.Recipe;
import com.restaurant.core.menu.entity.RecipeIngredient;
import com.restaurant.core.menu.repository.MenuCatalogQueryRepository;
import com.restaurant.core.menu.repository.MenuCategoryRepository;
import com.restaurant.core.menu.repository.MenuItemRepository;
import com.restaurant.core.menu.repository.RecipeIngredientRepository;
import com.restaurant.core.menu.repository.RecipeRepository;
import com.restaurant.core.menu.service.MenuCatalogService;

@Service
@Transactional(readOnly = true)
public class MenuCatalogServiceImpl implements MenuCatalogService {

    private final MenuCatalogQueryRepository repo;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;

    public MenuCatalogServiceImpl(MenuCatalogQueryRepository repo, MenuCategoryRepository categoryRepository, MenuItemRepository menuItemRepository,
                                  RecipeRepository recipeRepository, RecipeIngredientRepository recipeIngredientRepository) {
        this.repo = repo;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
    }

    @Override
    public List<CategoryResponse> getAllCategories(boolean activeOnly) {
        return repo.findCategories(activeOnly);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest r) {
        MenuCategory category = categoryRepository.saveAndFlush(new MenuCategory(r.name().strip(), sortOrderOf(r), isActive(r.active())));
        return repo.findCategory(category.getId()).orElseThrow();
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(long id, CategoryRequest r) {
        MenuCategory category = categoryRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy danh mục " + id));
        category.changeDetails(r.name().strip(), sortOrderOf(r), isActive(r.active()));
        categoryRepository.flush();
        return repo.findCategory(id).orElseThrow();
    }

    @Override
    public List<MenuItemResponse> getMenuItems(Long categoryId, boolean activeOnly) {
        return repo.findItems(categoryId, activeOnly);
    }

    @Override
    public MenuItemResponse getMenuItemById(long id) {
        return repo.findItem(id).orElseThrow(() -> notFound("Không tìm thấy món " + id));
    }

    @Override
    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest r) {
        MenuItem item = menuItemRepository.saveAndFlush(new MenuItem(categoryRepository.getReferenceById(r.categoryId()), r.name().strip(), r.price(), r.imageUrl(), isActive(r.active())));
        return getMenuItemById(item.getId());
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(long id, MenuItemRequest r) {
        MenuItem item = menuItemRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy món " + id));
        item.changeDetails(categoryRepository.getReferenceById(r.categoryId()), r.name().strip(), r.price(), r.imageUrl(), isActive(r.active()));
        menuItemRepository.flush();
        return getMenuItemById(id);
    }

    /**
     * Tạo công thức version mới và đặt làm hiện hành (nhiều câu lệnh → một transaction). Version cũ giữ nguyên để các
     * thực đơn ngày đã dùng nó không đổi; thực đơn ngày thêm món sau đó sẽ dùng version mới.
     */
    @Override
    @Transactional
    public RecipeResponse createRecipe(long menuItemId, RecipeRequest r) {
        MenuItem menuItem = menuItemRepository.findByIdForUpdate(menuItemId).orElseThrow(() -> notFound("Không tìm thấy món " + menuItemId));
        int version = recipeRepository.findLatestVersion(menuItemId) + 1;
        recipeRepository.findByMenuItem_IdAndCurrentTrue(menuItemId).ifPresent(current -> {
            current.retire();
            recipeRepository.flush();
        });
        Recipe recipe = recipeRepository.save(new Recipe(menuItem, version));
        for (RecipeRequest.Ingredient i : r.ingredients()) {
            recipeIngredientRepository.save(new RecipeIngredient(recipe, i.kitchenIngredientTypeId(), i.quantityPerServing()));
        }
        recipeIngredientRepository.flush();
        return new RecipeResponse(recipe.getId(), menuItemId, version, true, repo.findRecipeLines(recipe.getId()));
    }

    @Override
    public List<RecipeResponse> getRecipesOfMenuItem(long menuItemId) {
        getMenuItemById(menuItemId);
        return repo.findRecipes(menuItemId).stream()
                .map(h -> new RecipeResponse(h.id(), h.menuItemId(), h.version(), h.current(), repo.findRecipeLines(h.id())))
                .toList();
    }

    private static int sortOrderOf(CategoryRequest r) {
        return r.sortOrder() == null ? 0 : r.sortOrder();
    }

    /** Không gửi trạng thái = đang dùng. */
    private static boolean isActive(Boolean active) {
        return active == null || active;
    }

    private static ApiException notFound(String message) {
        return ApiException.notFound(message);
    }
}
