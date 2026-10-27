package com.restaurant.core.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** reason bắt buộc khi tiền đếm lệch tiền dự kiến; revisionReason bắt buộc khi đối soát lại cùng ngày (DB CHECK). */
public record ReconcileRequest(@NotNull LocalDate businessDate,
                               @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal openingCash,
                               @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal actualCash,
                               @Size(max = 255) String reason, @Size(max = 255) String revisionReason) {
}
