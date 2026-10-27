package com.restaurant.core.menu.dto;

import java.math.BigDecimal;

/** currentRecipeVersion null khi món chưa có công thức (chưa đưa vào thực đơn ngày được). */
public record MenuItemResponse(long id, long categoryId, String categoryName, String name, BigDecimal price, String imageUrl,
                               boolean active, Integer currentRecipeVersion) {
}
