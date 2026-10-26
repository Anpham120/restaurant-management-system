package com.restaurant.core.order.dto;

/** currentSessionId/currentOrderId có giá trị khi bàn đang có phiên OPEN. */
public record TableResponse(long id, String code, int capacity, String status, Long currentSessionId, Long currentOrderId) {
}
