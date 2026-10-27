package com.restaurant.core.order.dto;

/** currentSessionId/currentOrderId có giá trị khi bàn đang có phiên OPEN. active = false: bàn ngừng sử dụng. */
public record TableResponse(long id, String code, int capacity, String status, boolean active, Long currentSessionId,
                            Long currentOrderId) {
}
