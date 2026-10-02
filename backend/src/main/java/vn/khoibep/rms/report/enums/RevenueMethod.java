package vn.khoibep.rms.report.enums;

/**
 * How revenue came in (BR-21): the two ways of paying at the counter, deposits taken off bills (BR-42), and the
 * delivery apps that took the money of their orders (BR-47).
 */
public enum RevenueMethod {
    CASH,
    BANK_TRANSFER,
    DEPOSIT,
    GRABFOOD,
    SHOPEEFOOD
}
