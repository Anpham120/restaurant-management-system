package com.restaurant.core.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dòng món. status theo state machine của DB: PENDING → ACCEPTED → COOKING → READY → SERVED; REJECTED; CANCELLED.
 * statusReason: lý do bếp từ chối / lý do hủy (bắt buộc trong order_item_events), null với trạng thái khác.
 */
public record OrderItemResponse(long id, long orderId, long batchId, long dailyMenuItemId, String itemName, int quantity,
                                BigDecimal unitPrice, String status, boolean hasStockException, String note,
                                Instant submittedAt, String tableCode, String statusReason) {
}
