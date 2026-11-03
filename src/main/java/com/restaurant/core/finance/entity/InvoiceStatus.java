package com.restaurant.core.finance.entity;

/**
 * invoices.status: DRAFT (đang lập) → PAYMENT_PENDING (đã chốt, chờ thu) → PAID;
 * hoặc UNCOLLECTED (khách bỏ về), WAIVED (miễn thu).
 */
public enum InvoiceStatus {
    DRAFT, PAYMENT_PENDING, PAID, UNCOLLECTED, WAIVED
}
