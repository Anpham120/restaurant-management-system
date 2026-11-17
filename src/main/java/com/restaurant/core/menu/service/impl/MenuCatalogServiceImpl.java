package com.restaurant.core.menu.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.time.BusinessDateProvider;
import com.restaurant.core.menu.QuotaUsageCounter;
import com.restaurant.core.menu.entity.DailyMenu;
import com.restaurant.core.menu.entity.DailyMenuItem;
import com.restaurant.core.menu.repository.DailyMenuRepository;
import com.restaurant.core.menu.repository.DailyMenuItemRepository;
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
    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuItemRepository dailyMenuItemRepository;
    private final QuotaUsageCounter quotaUsageCounter;
    private final BusinessDateProvider clock;

    public MenuCatalogServiceImpl(MenuCatalogQueryRepository repo, MenuCategoryRepository categoryRepository, MenuItemRepository menuItemRepository,
                                  RecipeRepository recipeRepository, RecipeIngredientRepository recipeIngredientRepository,
                                  DailyMenuRepository dailyMenuRepository, DailyMenuItemRepository dailyMenuItemRepository,
                                  QuotaUsageCounter quotaUsageCounter, BusinessDateProvider clock) {
        this.dailyMenuRepository = dailyMenuRepository;
        this.dailyMenuItemRepository = dailyMenuItemRepository;
        this.quotaUsageCounter = quotaUsageCounter;
        this.clock = clock;
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
        MenuCategory category = categoryRepository.findById(id).orElseThrow(() -> notFoundError("Không tìm thấy danh mục " + id));
        category.changeDetails(r.name().strip(), sortOrderOf(r), isActive(r.active()));
        categoryRepository.flush();
        return repo.findCategory(id).orElseThrow();
    }

    @Override
    public List<MenuItemResponse> getMenuItems(Long categoryId, boolean activeOnly) {
        return repo.findMenuItems(categoryId, activeOnly);
    }

    @Override
    public MenuItemResponse getMenuItemById(long id) {
        return repo.findMenuItem(id).orElseThrow(() -> notFoundError("Không tìm thấy món " + id));
    }

    /**
     * Thêm món là bán được ngay: món mới có sẵn công thức phiên bản 1 không nguyên liệu (bán không trừ kho, hợp với
     * nước uống, món mua sẵn) và được đưa vào thực đơn hôm nay nếu thực đơn đang mở. Món cần trừ kho thì khai nguyên
     * liệu sau bằng một phiên bản công thức mới.
     */
    @Override
    @Transactional
    public MenuItemResponse createMenuItem(MenuItemRequest r) {
        MenuItem item = menuItemRepository.saveAndFlush(new MenuItem(categoryRepository.getReferenceById(r.categoryId()), r.name().strip(), r.price(), r.imageUrl(), isActive(r.active())));
        Recipe recipe = recipeRepository.saveAndFlush(new Recipe(item, 1));
        if (item.isActive()) {
            useOnTodayMenu(item, recipe, true);
        }
        return getMenuItemById(item.getId());
    }

    /**
     * Áp công thức hiện hành vào thực đơn hôm nay (nếu có và chưa đóng). Món đã có trong thực đơn và chưa ai gọi hôm nay
     * thì đổi sang công thức mới ngay; đã có người gọi thì giữ công thức cũ đến hết ngày (số nguyên liệu đã giữ theo
     * công thức cũ). addIfMissing: món chưa có trong thực đơn thì thêm, bán không giới hạn suất.
     */
    private void useOnTodayMenu(MenuItem item, Recipe recipe, boolean addIfMissing) {
        DailyMenu today = dailyMenuRepository.findByBusinessDate(clock.today()).filter(m -> !m.isClosed()).orElse(null);
        if (today == null) {
            return;
        }
        DailyMenuItem onMenu = dailyMenuItemRepository.findByDailyMenu_IdAndMenuItem_Id(today.getId(), item.getId()).orElse(null);
        if (onMenu == null) {
            if (addIfMissing) {
                dailyMenuItemRepository.saveAndFlush(new DailyMenuItem(today, item, recipe, null));
            }
        } else if (!quotaUsageCounter.hasBeenOrdered(onMenu.getId())) {
            onMenu.changeRecipe(recipe);
            dailyMenuItemRepository.flush();
        }
    }

    @Override
    @Transactional
    public MenuItemResponse updateMenuItem(long id, MenuItemRequest r) {
        MenuItem item = menuItemRepository.findById(id).orElseThrow(() -> notFoundError("Không tìm thấy món " + id));
        item.changeDetails(categoryRepository.getReferenceById(r.categoryId()), r.name().strip(), r.price(), r.imageUrl(), isActive(r.active()));
        menuItemRepository.flush();
        return getMenuItemById(id);
    }

    /**
     * Tạo công thức version mới và đặt làm hiện hành (nhiều câu lệnh → một transaction). Version cũ giữ nguyên để các
     * thực đơn ngày đã dùng nó không đổi. Thực đơn hôm nay dùng version mới ngay nếu món chưa có ai gọi hôm nay.
     */
    @Override
    @Transactional
    public RecipeResponse createRecipe(long menuItemId, RecipeRequest r) {
        MenuItem menuItem = menuItemRepository.findByIdForUpdate(menuItemId).orElseThrow(() -> notFoundError("Không tìm thấy món " + menuItemId));
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
        useOnTodayMenu(menuItem, recipe, false);
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

    private static ApiException notFoundError(String message) {
        return ApiException.notFound(message);
    }
}
