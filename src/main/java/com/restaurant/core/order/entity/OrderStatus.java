package com.restaurant.core.order.entity;

/** orders.status: OPEN → CLOSED (đã có hóa đơn kết thúc) hoặc CANCELLED (phiên không phát sinh tiền). */
public enum OrderStatus {
    OPEN, CLOSED, CANCELLED
}
