package vn.khoibep.rms.enums;

public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    /** BR-47: the delivery app took the guest's money for an app order. */
    GRABFOOD,
    SHOPEEFOOD
}
