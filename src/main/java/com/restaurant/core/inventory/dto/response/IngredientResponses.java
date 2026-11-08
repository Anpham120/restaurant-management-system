package com.restaurant.core.inventory.dto.response;

import java.math.BigDecimal;

/** Danh mục nguyên liệu thô và loại nguyên liệu bếp (DIRECT dùng thẳng / PREPARED sau sơ chế). */
public final class IngredientResponses {

    private IngredientResponses() {
    }

    public record IngredientResponse(long id, String name, String baseUnit, BigDecimal minQuantity, boolean expiryTracked,
                                     boolean active) {
    }

    public record KitchenTypeResponse(long id, long rawIngredientId, String rawIngredientName, String baseUnit, String name,
                                      String handlingType, Integer shelfLifeHours, BigDecimal minimumQuantity, boolean active) {
    }
}
