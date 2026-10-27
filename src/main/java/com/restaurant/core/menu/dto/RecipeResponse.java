package com.restaurant.core.menu.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecipeResponse(long id, long menuItemId, int version, boolean current, List<Line> ingredients) {

    public record Line(long kitchenIngredientTypeId, String typeName, String baseUnit, BigDecimal quantityPerServing) {
    }
}
