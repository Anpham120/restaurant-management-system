package com.restaurant.core.order.dto;

/** Kết quả rc_accept_item: ACCEPTED, hoặc REJECTED kèm lý do do DB ghi (vượt hạn mức / thiếu nguyên liệu / món tạm dừng). */
public record AcceptItemResponse(String result, String reason, OrderItemResponse item) {
}
