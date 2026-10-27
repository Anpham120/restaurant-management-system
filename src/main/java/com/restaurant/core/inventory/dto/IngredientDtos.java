package com.restaurant.core.inventory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Danh mục nguyên liệu thô và loại nguyên liệu bếp (DIRECT dùng thẳng / PREPARED sau sơ chế). */
public final class IngredientDtos {

    private IngredientDtos() {
    }

    /** baseUnit: đơn vị nhỏ nhất dùng cho định lượng (g, ml, quả…). */
    public record IngredientRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String baseUnit,
                                    @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal minQuantity,
                                    Boolean expiryTracked, Boolean active) {
    }

    public record IngredientResponse(long id, String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked,
                                     boolean active) {
    }

    /** Mỗi nguyên liệu tối đa một loại DIRECT (DB). shelfLifeHours: hạn dùng tại bếp sau khi nhận/sơ chế. */
    public record KitchenTypeRequest(@NotNull @Positive Long rawIngredientId, @NotBlank @Size(max = 100) String name,
                                     @NotNull @Pattern(regexp = "DIRECT|PREPARED") String handlingType,
                                     @Positive Integer shelfLifeHours,
                                     @PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal minimumQuantity, Boolean active) {
    }

    public record KitchenTypeResponse(long id, long rawIngredientId, String rawIngredientName, String baseUnit, String name,
                                      String handlingType, Integer shelfLifeHours, BigDecimal minimumQuantity, boolean active) {
    }
}
