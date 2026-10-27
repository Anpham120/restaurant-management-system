package com.restaurant.core.menu.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.restaurant.core.common.response.ApiResponse;
import com.restaurant.core.menu.dto.CategoryRequest;
import com.restaurant.core.menu.dto.CategoryResponse;
import com.restaurant.core.menu.dto.MenuItemRequest;
import com.restaurant.core.menu.dto.MenuItemResponse;
import com.restaurant.core.menu.dto.RecipeRequest;
import com.restaurant.core.menu.dto.RecipeResponse;
import com.restaurant.core.menu.service.MenuCatalogService;

/** Danh mục món, món, công thức. Bảng ghi trực tiếp → @PreAuthorize là lớp kiểm quyền duy nhất. Owner: Lê Anh. */
@RestController
@RequestMapping("/api")
public class MenuCatalogController {

    private final MenuCatalogService service;

    public MenuCatalogController(MenuCatalogService service) {
        this.service = service;
    }

    @GetMapping("/menu-categories")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<List<CategoryResponse>> categories(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.categories(activeOnly));
    }

    @PostMapping("/menu-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(service.createCategory(request));
    }

    @PutMapping("/menu-categories/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable @Positive long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(service.updateCategory(id, request));
    }

    @GetMapping("/menu-items")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<List<MenuItemResponse>> items(@RequestParam(required = false) @Positive Long categoryId,
                                                     @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.items(categoryId, activeOnly));
    }

    @GetMapping("/menu-items/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER', 'KITCHEN', 'CASHIER')")
    public ApiResponse<MenuItemResponse> item(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.item(id));
    }

    @PostMapping("/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<MenuItemResponse> createItem(@Valid @RequestBody MenuItemRequest request) {
        return ApiResponse.ok(service.createItem(request));
    }

    @PutMapping("/menu-items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<MenuItemResponse> updateItem(@PathVariable @Positive long id, @Valid @RequestBody MenuItemRequest request) {
        return ApiResponse.ok(service.updateItem(id, request));
    }

    @GetMapping("/menu-items/{id}/recipes")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<RecipeResponse>> recipes(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.recipes(id));
    }

    /** Tạo version công thức mới (hiện hành). Không sửa công thức cũ. */
    @PostMapping("/menu-items/{id}/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<RecipeResponse> createRecipe(@PathVariable @Positive long id, @Valid @RequestBody RecipeRequest request) {
        return ApiResponse.ok(service.createRecipe(id, request));
    }
}
