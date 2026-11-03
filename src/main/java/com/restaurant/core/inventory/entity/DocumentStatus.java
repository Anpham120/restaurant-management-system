package com.restaurant.core.inventory.entity;

/** Trạng thái phiếu nhập, kiểm kê, sơ chế, hoàn trả, hủy hàng: DRAFT (nháp) → CONFIRMED (đã ghi sổ) hoặc CANCELLED. */
public enum DocumentStatus {
    DRAFT, CONFIRMED, CANCELLED
}
