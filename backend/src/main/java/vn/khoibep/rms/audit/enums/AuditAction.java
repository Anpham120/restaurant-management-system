package vn.khoibep.rms.audit.enums;

/** The sensitive actions written to the audit log (BR-34). */
public enum AuditAction {
    /** A dish was cancelled, or a guest's dish refused; the previous status says which. */
    ITEM_CANCELLED,
    /** A cashier confirmed a transfer by hand. */
    MANUAL_CONFIRMATION,
    /** The price of a dish on the menu changed. */
    PRICE_CHANGED
}
