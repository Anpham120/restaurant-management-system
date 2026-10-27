package com.restaurant.core.inventory.controller;

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
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientResponse;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeResponse;
import com.restaurant.core.inventory.service.IngredientService;

/** Danh mục nguyên liệu và loại nguyên liệu bếp. Bảng ghi trực tiếp → @PreAuthorize là lớp kiểm quyền. Owner: Đỗ Tuấn Anh. */
@RestController
@RequestMapping("/api")
public class IngredientController {

    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @GetMapping("/ingredients")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<IngredientResponse>> ingredients(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.ingredients(activeOnly));
    }

    @GetMapping("/ingredients/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<IngredientResponse> ingredient(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.ingredient(id));
    }

    @PostMapping("/ingredients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<IngredientResponse> createIngredient(@Valid @RequestBody IngredientRequest request) {
        return ApiResponse.ok(service.createIngredient(request));
    }

    @PutMapping("/ingredients/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<IngredientResponse> updateIngredient(@PathVariable @Positive long id, @Valid @RequestBody IngredientRequest request) {
        return ApiResponse.ok(service.updateIngredient(id, request));
    }

    @GetMapping("/kitchen-ingredient-types")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<List<KitchenTypeResponse>> types(@RequestParam(required = false) @Positive Long ingredientId,
                                                        @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ApiResponse.ok(service.types(ingredientId, activeOnly));
    }

    @GetMapping("/kitchen-ingredient-types/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ApiResponse<KitchenTypeResponse> type(@PathVariable @Positive long id) {
        return ApiResponse.ok(service.type(id));
    }

    @PostMapping("/kitchen-ingredient-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<KitchenTypeResponse> createType(@Valid @RequestBody KitchenTypeRequest request) {
        return ApiResponse.ok(service.createType(request));
    }

    @PutMapping("/kitchen-ingredient-types/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<KitchenTypeResponse> updateType(@PathVariable @Positive long id, @Valid @RequestBody KitchenTypeRequest request) {
        return ApiResponse.ok(service.updateType(id, request));
    }
}
