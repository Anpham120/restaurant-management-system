package com.restaurant.core.menu.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Định lượng theo base_unit của nguyên liệu gốc (QĐ-6). Mỗi lần gửi tạo một version mới và đặt làm hiện hành. */
public record RecipeRequest(@NotNull @Size(max = 50) List<@Valid @NotNull Ingredient> ingredients) {

    public record Ingredient(@NotNull @Positive Long kitchenIngredientTypeId,
                             @NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantityPerServing) {
    }
}
