package com.restaurant.core.order.dto;

/** rc_open_session tạo phiên và đơn hàng (1–1) trong cùng một lần gọi. */
public record OpenSessionResponse(long sessionId, long tableId, long orderId) {
}
