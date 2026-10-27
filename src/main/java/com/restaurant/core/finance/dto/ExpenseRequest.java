package com.restaurant.core.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Khoản chi hoạt động. Ngày kinh doanh do DB suy từ paidAt (mốc 04:00). fundSource CASH_DRAWER (quỹ quầy) bắt buộc
 * paymentMethod CASH và được trừ khi đối soát tiền mặt. stockReceiptId: chi cho phiếu nhập (mỗi phiếu tối đa một khoản PAID).
 */
public record ExpenseRequest(@NotBlank @Size(max = 64) String requestKey,
                             @NotNull @Pattern(regexp = "INGREDIENT|SUPPLIES|UTILITY|RENT|MAINTENANCE|OTHER") String category,
                             @NotBlank @Size(max = 255) String description,
                             @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
                             @NotNull @Pattern(regexp = "CASH|BANK_TRANSFER") String paymentMethod,
                             @NotNull @Pattern(regexp = "CASH_DRAWER|OWNER|BANK") String fundSource,
                             @Positive Long stockReceiptId, Instant paidAt) {
}
