package com.restaurant.core.inventory.entity;

/** Phiếu xuất cho bếp: DRAFT → ISSUED (kho đã trừ) → RECEIVED (bếp đã nhận); DRAFT/ISSUED có thể CANCELLED. */
public enum IssueStatus {
    DRAFT, ISSUED, RECEIVED, CANCELLED
}
