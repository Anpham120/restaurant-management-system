package com.restaurant.core.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Phiên phục vụ kèm đơn và hóa đơn (nếu có). invoiceStatus: DRAFT/PAYMENT_PENDING/PAID/UNCOLLECTED/WAIVED. */
public record SessionResponse(long sessionId, long tableId, String tableCode, String status, Instant openedAt, Instant closedAt,
                              long openedById, Long closedById, long orderId, String orderStatus,
                              Long invoiceId, String invoiceStatus, BigDecimal invoiceTotal) {
}
