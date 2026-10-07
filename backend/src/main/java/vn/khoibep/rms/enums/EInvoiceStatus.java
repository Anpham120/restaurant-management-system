package vn.khoibep.rms.enums;

/** BR-46: where an e-invoice stands; worked out from when it was exported and whether it has a number. */
public enum EInvoiceStatus {
    /** Waiting to be exported. */
    PENDING,
    /** In an exported file, not yet issued. */
    EXPORTED,
    /** Issued on MISA under a symbol and number. */
    ISSUED
}
