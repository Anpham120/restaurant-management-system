package com.restaurant.core.finance.dto;

import java.math.BigDecimal;

import com.restaurant.core.finance.strategy.PaymentMethod;

/**
 * paymentId có khi CASH đã thu; paymentReference + qrImageUrl có khi QR đang chờ chuyển khoản (WAITING_TRANSFER).
 * amountDue là số phải thu do DB lấy từ hóa đơn. qrImageUrl null nếu chưa cấu hình tài khoản nhận (SEPAY_BANK_*).
 */
public record PaymentResponse(long invoiceId, PaymentMethod method, String status, Long paymentId, String paymentReference,
                              BigDecimal amountDue, String qrImageUrl) {
}
