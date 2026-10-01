package vn.khoibep.rms.inventory.enums;

public enum MovementType {
    /** Goods received. */
    IN,
    /** Used or spoiled. */
    OUT,
    /** Stock count: the change is counted minus recorded. */
    ADJUST
}
