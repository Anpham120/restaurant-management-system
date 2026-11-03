package com.restaurant.core.inventory.entity;

/** Lý do trả phần nguyên liệu đã giữ: món bị hủy, hoặc lô đang giữ bị đánh dấu không dùng được. */
public enum ReleaseReason {
    ITEM_CANCELLED, BATCH_UNUSABLE
}
