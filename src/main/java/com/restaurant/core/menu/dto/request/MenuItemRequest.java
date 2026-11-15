package com.restaurant.core.menu.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.restaurant.core.common.validation.WholeVnd;

/** Đổi giá không ảnh hưởng món đã gọi (order_items chụp giá lúc gửi món). active mặc định true. */
public record MenuItemRequest(@NotNull @Positive Long categoryId, @NotBlank @Size(max = 100) String name,
                              @NotNull @PositiveOrZero @WholeVnd BigDecimal price,
                              @Size(max = 255) String imageUrl, Boolean active) {
}
