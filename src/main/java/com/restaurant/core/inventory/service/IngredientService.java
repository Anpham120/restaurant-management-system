package com.restaurant.core.inventory.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientResponse;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeResponse;
import com.restaurant.core.inventory.repository.IngredientRepository;

/** Danh mục nguyên liệu và loại bếp. Owner: Đỗ Tuấn Anh. */
@Service
public class IngredientService {

    private final IngredientRepository repo;

    public IngredientService(IngredientRepository repo) {
        this.repo = repo;
    }

    public List<IngredientResponse> ingredients(boolean activeOnly) {
        return repo.findIngredients(activeOnly);
    }

    public IngredientResponse ingredient(long id) {
        return repo.findIngredient(id).orElseThrow(() -> notFound("Không tìm thấy nguyên liệu " + id));
    }

    public IngredientResponse createIngredient(IngredientRequest r) {
        return ingredient(repo.insertIngredient(r.name().strip(), r.baseUnit().strip(), orZero(r.minQuantity()),
                r.expiryTracked() == null || r.expiryTracked(), r.active() == null || r.active()));
    }

    public IngredientResponse updateIngredient(long id, IngredientRequest r) {
        if (repo.updateIngredient(id, r.name().strip(), r.baseUnit().strip(), orZero(r.minQuantity()), r.expiryTracked() == null || r.expiryTracked(),
                r.active() == null || r.active()) == 0) {
            throw notFound("Không tìm thấy nguyên liệu " + id);
        }
        return ingredient(id);
    }

    public List<KitchenTypeResponse> types(Long ingredientId, boolean activeOnly) {
        return repo.findTypes(ingredientId, activeOnly);
    }

    public KitchenTypeResponse type(long id) {
        return repo.findType(id).orElseThrow(() -> notFound("Không tìm thấy loại nguyên liệu bếp " + id));
    }

    public KitchenTypeResponse createType(KitchenTypeRequest r) {
        return type(repo.insertType(r.rawIngredientId(), r.name().strip(), r.handlingType(), r.shelfLifeHours(), orZero(r.minimumQuantity()),
                r.active() == null || r.active()));
    }

    /** Không đổi nguyên liệu gốc và handlingType sau khi tạo (lô bếp, công thức đã tham chiếu). */
    public KitchenTypeResponse updateType(long id, KitchenTypeRequest r) {
        KitchenTypeResponse current = type(id);
        if (current.rawIngredientId() != r.rawIngredientId() || !current.handlingType().equals(r.handlingType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IMMUTABLE_FIELD",
                    "Không được đổi nguyên liệu gốc hoặc cách dùng của loại bếp đã tạo");
        }
        repo.updateType(id, r.name().strip(), r.shelfLifeHours(), orZero(r.minimumQuantity()), r.active() == null || r.active());
        return type(id);
    }

    /** Cột min_quantity / minimum_quantity NOT NULL DEFAULT 0; is_expiry_tracked mặc định true. */
    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
