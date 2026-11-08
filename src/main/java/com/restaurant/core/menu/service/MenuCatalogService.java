package com.restaurant.core.menu.service;

import java.util.List;

import com.restaurant.core.menu.dto.request.CategoryRequest;
import com.restaurant.core.menu.dto.response.CategoryResponse;
import com.restaurant.core.menu.dto.request.MenuItemRequest;
import com.restaurant.core.menu.dto.response.MenuItemResponse;
import com.restaurant.core.menu.dto.request.RecipeRequest;
import com.restaurant.core.menu.dto.response.RecipeResponse;

/** Danh mục, món, công thức. Owner: Lê Anh. */
public interface MenuCatalogService {

    List<CategoryResponse> getAllCategories(boolean activeOnly);

    CategoryResponse createCategory(CategoryRequest r);

    CategoryResponse updateCategory(long id, CategoryRequest r);

    List<MenuItemResponse> getMenuItems(Long categoryId, boolean activeOnly);

    MenuItemResponse getMenuItemById(long id);

    MenuItemResponse createMenuItem(MenuItemRequest r);

    MenuItemResponse updateMenuItem(long id, MenuItemRequest r);

    /**
     * Tạo công thức version mới và đặt làm hiện hành (nhiều câu lệnh → một transaction). Version cũ giữ nguyên để các
     * thực đơn ngày đã dùng nó không đổi; thực đơn ngày thêm món sau đó sẽ dùng version mới.
     */
    RecipeResponse createRecipe(long menuItemId, RecipeRequest r);

    List<RecipeResponse> getRecipesOfMenuItem(long menuItemId);
}
