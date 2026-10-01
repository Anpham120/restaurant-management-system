package vn.bnn.rms.inventory.enums;

public enum MovementType {
    /** Goods received. */
    IN,
    /** Used or spoiled. */
    OUT,
    /** Stock count: the change is counted minus recorded. */
    ADJUST
}
