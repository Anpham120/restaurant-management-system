package com.restaurant.core.order.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Một lượt gọi món. requestKey do client sinh MỘT lần cho mỗi lần bấm "Gửi" (UUID) và gửi lại y nguyên khi retry:
 * cùng khóa → DB trả đợt cũ (replayed = true), không ghi trùng.
 */
public record SubmitBatchRequest(
        @NotBlank @Size(max = 64) String requestKey,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull Item> items) {

    public record Item(@NotNull @Positive Long dailyMenuItemId, @NotNull @Positive Integer quantity, @Size(max = 255) String note) {
    }
}
