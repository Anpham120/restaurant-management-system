package com.restaurant.core.finance.entity;

/** payment_requests.status. REQUESTED/WAITING_TRANSFER là yêu cầu đang mở. */
public enum PaymentRequestStatus {
    REQUESTED, WAITING_TRANSFER, PAID, CANCELLED;

    public boolean isOpen() {
        return this == REQUESTED || this == WAITING_TRANSFER;
    }
}
