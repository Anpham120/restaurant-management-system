package vn.bnn.rms.inventory;

public enum MovementType {
    /** Goods received. */
    IN,
    /** Used or spoiled. */
    OUT,
    /** Stock count: the change is counted minus recorded. */
    ADJUST
}
