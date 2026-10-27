package com.restaurant.core.menu.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Đổi giá không ảnh hưởng món đã gọi (order_items chụp giá lúc gửi món). active mặc định true. */
public record MenuItemRequest(@NotNull @Positive Long categoryId, @NotBlank @Size(max = 100) String name,
                              @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal price,
                              @Size(max = 255) String imageUrl, Boolean active) {
}
