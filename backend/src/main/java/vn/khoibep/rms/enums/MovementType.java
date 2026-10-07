package vn.khoibep.rms.enums;

public enum MovementType {
    /** Goods received. */
    IN,
    /** Used or spoiled. */
    OUT,
    /** Stock count: the change is counted minus recorded. */
    ADJUST,
    /** Used by a dish sent to the kitchen (negative), or given back when it is cancelled before cooking (BR-38). */
    SALE
}
