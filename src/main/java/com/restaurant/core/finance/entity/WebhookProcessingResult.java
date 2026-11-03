package com.restaurant.core.finance.entity;

/** Kết quả xử lý một giao dịch chuyển khoản báo về từ SePay. */
public enum WebhookProcessingResult {
    PROCESSED, MISMATCH, AFTER_CANCEL
}
