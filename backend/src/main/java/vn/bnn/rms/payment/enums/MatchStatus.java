package vn.bnn.rms.payment.enums;

public enum MatchStatus {
    MATCHED,
    /** Money arrived but no pending payment fits it; a cashier must look (BR-16). */
    UNMATCHED,
    /** Outgoing transfer, not relevant. */
    IGNORED
}
