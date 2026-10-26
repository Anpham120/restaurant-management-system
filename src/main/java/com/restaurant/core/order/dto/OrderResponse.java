package com.restaurant.core.order.dto;

import java.util.List;

public record OrderResponse(long orderId, String status, SessionResponse session, List<OrderItemResponse> items) {
}
