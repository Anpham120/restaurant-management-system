package vn.bnn.rms.payment;

public enum Confirmation {
    /** Confirmed by a verified SePay webhook. */
    AUTO,
    /** Confirmed by a cashier or manager (cash, or a transfer checked in the bank app). */
    MANUAL
}
