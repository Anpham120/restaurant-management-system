package com.restaurant.core.inventory.entity;

/** stock_reservations.status: ACTIVE (đang giữ) → CONSUMED (đã nấu) hoặc RELEASED (trả lại). */
public enum ReservationStatus {
    ACTIVE, CONSUMED, RELEASED
}
