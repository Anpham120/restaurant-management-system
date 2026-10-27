package com.restaurant.core.finance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(long id, LocalDate businessDate, String category, String description, BigDecimal amount,
                              String paymentMethod, String fundSource, Long stockReceiptId, long recordedById, Instant paidAt,
                              Instant createdAt, String requestKey, String status, String voidReason, Long voidedById,
                              Instant voidedAt) {
}
