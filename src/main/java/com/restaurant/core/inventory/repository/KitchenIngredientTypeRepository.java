package com.restaurant.core.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.restaurant.core.inventory.entity.KitchenIngredientType;

public interface KitchenIngredientTypeRepository extends JpaRepository<KitchenIngredientType, Long> {

    /** Loại bếp DIRECT đang dùng của một nguyên liệu gốc (để xuất thẳng cho bếp). */
    @Query("""
            SELECT t FROM KitchenIngredientType t
             WHERE t.rawIngredientId = :ingredientId AND t.handlingType = 'DIRECT' AND t.active = true""")
    Optional<KitchenIngredientType> findActiveDirectTypeOfIngredient(@Param("ingredientId") Long ingredientId);
}
