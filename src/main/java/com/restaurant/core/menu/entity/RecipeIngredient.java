package com.restaurant.core.menu.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Bảng recipe_ingredients: lượng một loại nguyên liệu bếp cho một suất. */
@Entity
@Table(name = "recipe_ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipe_id", nullable = false)
    private Long recipeId;

    @Column(name = "kitchen_ingredient_type_id", nullable = false)
    private Long kitchenIngredientTypeId;

    @Column(name = "quantity_per_serving", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityPerServing;

    public RecipeIngredient(long recipeId, long kitchenIngredientTypeId, BigDecimal quantityPerServing) {
        this.recipeId = recipeId;
        this.kitchenIngredientTypeId = kitchenIngredientTypeId;
        this.quantityPerServing = quantityPerServing;
    }
}
