package vn.khoibep.rms.order.enums;

/** BR-35: only APPLIED adjustments take money off the bill; PENDING ones wait for a manager. */
public enum AdjustmentStatus {
    PENDING,
    APPLIED,
    REJECTED,
    CANCELLED
}
