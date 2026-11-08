package com.restaurant.core.menu.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Đổi hạn mức nhiều món một lần (một transaction). quotaTotal: null = không giới hạn, 0 = chưa mở bán / hết suất. */
public record QuotasRequest(@NotEmpty @Size(max = 500) List<@Valid @NotNull Line> items) {

    public record Line(@NotNull @Positive Long dailyMenuItemId, @PositiveOrZero Integer quotaTotal) {
    }
}
