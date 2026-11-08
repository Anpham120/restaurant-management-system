package com.restaurant.core.inventory.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.restaurant.core.common.exception.ApiException;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.IngredientResponse;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeRequest;
import com.restaurant.core.inventory.dto.IngredientDtos.KitchenTypeResponse;
import com.restaurant.core.inventory.entity.Ingredient;
import com.restaurant.core.inventory.entity.KitchenIngredientType;
import com.restaurant.core.inventory.repository.IngredientQueryRepository;
import com.restaurant.core.inventory.repository.IngredientRepository;
import com.restaurant.core.inventory.repository.KitchenIngredientTypeRepository;
import com.restaurant.core.inventory.service.IngredientService;

@Service
@Transactional(readOnly = true)
public class IngredientServiceImpl implements IngredientService {

    private final IngredientQueryRepository repo;
    private final IngredientRepository ingredientRepository;
    private final KitchenIngredientTypeRepository kitchenTypeRepository;

    public IngredientServiceImpl(IngredientQueryRepository repo, IngredientRepository ingredientRepository,
                                 KitchenIngredientTypeRepository kitchenTypeRepository) {
        this.repo = repo;
        this.ingredientRepository = ingredientRepository;
        this.kitchenTypeRepository = kitchenTypeRepository;
    }

    @Override
    public List<IngredientResponse> getAllIngredients(boolean activeOnly) {
        return repo.findIngredients(activeOnly);
    }

    @Override
    public IngredientResponse getIngredientById(long id) {
        return repo.findIngredient(id).orElseThrow(() -> notFound("Không tìm thấy nguyên liệu " + id));
    }

    @Override
    @Transactional
    public IngredientResponse createIngredient(IngredientRequest r) {
        Ingredient ingredient = ingredientRepository.saveAndFlush(new Ingredient(r.name().strip(), r.baseUnit().strip(), orZero(r.minQuantity()),
                isTrueOrMissing(r.expiryTracked()), isTrueOrMissing(r.active())));
        return getIngredientById(ingredient.getId());
    }

    @Override
    @Transactional
    public IngredientResponse updateIngredient(long id, IngredientRequest r) {
        Ingredient ingredient = ingredientRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy nguyên liệu " + id));
        ingredient.changeDetails(r.name().strip(), r.baseUnit().strip(), orZero(r.minQuantity()), isTrueOrMissing(r.expiryTracked()),
                isTrueOrMissing(r.active()));
        ingredientRepository.flush();
        return getIngredientById(id);
    }

    @Override
    public List<KitchenTypeResponse> getAllKitchenTypes(Long ingredientId, boolean activeOnly) {
        return repo.findTypes(ingredientId, activeOnly);
    }

    @Override
    public KitchenTypeResponse getKitchenTypeById(long id) {
        return repo.findType(id).orElseThrow(() -> notFound("Không tìm thấy loại nguyên liệu bếp " + id));
    }

    @Override
    @Transactional
    public KitchenTypeResponse createKitchenType(KitchenTypeRequest r) {
        KitchenIngredientType type = kitchenTypeRepository.saveAndFlush(new KitchenIngredientType(ingredientRepository.getReferenceById(r.rawIngredientId()), r.name().strip(),
                r.handlingType(), r.shelfLifeHours(), orZero(r.minimumQuantity()), isTrueOrMissing(r.active())));
        return getKitchenTypeById(type.getId());
    }

    /** Không đổi nguyên liệu gốc và handlingType sau khi tạo (lô bếp, công thức đã tham chiếu). */
    @Override
    @Transactional
    public KitchenTypeResponse updateKitchenType(long id, KitchenTypeRequest r) {
        KitchenIngredientType type = kitchenTypeRepository.findById(id).orElseThrow(() -> notFound("Không tìm thấy loại nguyên liệu bếp " + id));
        if (!type.getRawIngredientId().equals(r.rawIngredientId()) || !type.getHandlingType().equals(r.handlingType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IMMUTABLE_FIELD",
                    "Không được đổi nguyên liệu gốc hoặc cách dùng của loại bếp đã tạo");
        }
        type.changeDetails(r.name().strip(), r.shelfLifeHours(), orZero(r.minimumQuantity()), isTrueOrMissing(r.active()));
        kitchenTypeRepository.flush();
        return getKitchenTypeById(id);
    }

    /** Không gửi cờ = bật (đang dùng, có theo dõi hạn dùng). */
    private static boolean isTrueOrMissing(Boolean flag) {
        return flag == null || flag;
    }

    /** Không gửi tồn tối thiểu = 0. */
    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
