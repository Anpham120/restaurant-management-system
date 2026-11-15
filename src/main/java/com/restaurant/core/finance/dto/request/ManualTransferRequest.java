package com.restaurant.core.finance.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.restaurant.core.common.validation.WholeVnd;

/**
 * Ghi nhận tay một chuyển khoản QR khi webhook bị mất. transactionCode = mã giao dịch SePay (cột "ID" trên trang
 * giao dịch SePay): nếu webhook đến muộn với cùng mã thì được coi là gửi lặp, không thu hai lần.
 */
public record ManualTransferRequest(@NotBlank @Size(max = 64) String transactionCode,
                                    @NotNull @Positive @WholeVnd BigDecimal amount,
                                    @NotBlank @Size(max = 200) String note) {
}
