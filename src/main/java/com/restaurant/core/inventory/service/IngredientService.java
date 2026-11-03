package com.restaurant.core.inventory.service;

import java.util.List;

import com.restaurant.core.inventory.dto.IngredientDtos.IngredientRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientResponse;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeResponse;

/** Danh mục nguyên liệu và loại bếp. Owner: Đỗ Tuấn Anh. */
public interface IngredientService {

    List<IngredientResponse> getAllIngredients(boolean activeOnly);

    IngredientResponse getIngredientById(long id);

    IngredientResponse createIngredient(IngredientRequest r);

    IngredientResponse updateIngredient(long id, IngredientRequest r);

    List<KitchenTypeResponse> getAllKitchenTypes(Long ingredientId, boolean activeOnly);

    KitchenTypeResponse getKitchenTypeById(long id);

    KitchenTypeResponse createKitchenType(KitchenTypeRequest r);

    /** Không đổi nguyên liệu gốc và handlingType sau khi tạo (lô bếp, công thức đã tham chiếu). */
    KitchenTypeResponse updateKitchenType(long id, KitchenTypeRequest r);
}
