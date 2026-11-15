package com.restaurant.core.order.dto.response;

/** Mở bàn tạo phiên và đơn hàng (1–1) trong cùng một transaction. */
public record OpenSessionResponse(long sessionId, long tableId, long orderId) {
}
