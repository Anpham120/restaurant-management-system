package com.restaurant.core.finance.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.restaurant.core.common.validation.WholeVnd;

/** reason bắt buộc khi tiền đếm lệch tiền dự kiến; revisionReason bắt buộc khi đối soát lại cùng ngày (DB CHECK). */
public record ReconcileRequest(@NotNull LocalDate businessDate,
                               @NotNull @PositiveOrZero @WholeVnd BigDecimal openingCash,
                               @NotNull @PositiveOrZero @WholeVnd BigDecimal actualCash,
                               @Size(max = 255) String reason, @Size(max = 255) String revisionReason) {
}
