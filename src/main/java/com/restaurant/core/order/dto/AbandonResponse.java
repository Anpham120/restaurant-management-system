package com.restaurant.core.order.dto;

/** invoiceId null khi phiên không có món phải thu và chưa có hóa đơn (đơn CANCELLED). */
public record AbandonResponse(long sessionId, Long invoiceId, String invoiceStatus) {
}
