package com.restaurant.core.order.dto.response;

/** Kết quả bếp nhận món: ACCEPTED, hoặc REJECTED kèm lý do (vượt hạn mức / thiếu nguyên liệu / món tạm dừng). */
public record AcceptItemResponse(String result, String reason, OrderItemResponse item) {
}
