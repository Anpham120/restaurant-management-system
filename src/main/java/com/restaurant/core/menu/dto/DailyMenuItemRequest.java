package com.restaurant.core.menu.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** quotaTotal: null = không giới hạn suất, 0 = hết suất (QĐ-1). Dùng công thức hiện hành của món. */
public record DailyMenuItemRequest(@NotNull @Positive Long menuItemId, @PositiveOrZero Integer quotaTotal) {
}
