package vn.khoibep.rms.enums;

/** BR-46: what a line of an e-invoice is. */
public enum LineKind {
    /** Dishes sold. */
    GOODS,
    /** A dish given free or a discount on the bill: comes off the total. */
    DISCOUNT
}
