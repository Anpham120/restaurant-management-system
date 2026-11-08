package com.restaurant.core.finance.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.restaurant.core.finance.strategy.PaymentMethod;

/** cashReceived chỉ dùng cho CASH. Số phải thu do DB tự lấy từ hóa đơn — client không gửi số tiền phải thu. */
public record StartPaymentRequest(@NotNull PaymentMethod method, @PositiveOrZero BigDecimal cashReceived) {
}
