package vn.bnn.rms.order.enums;

/** Dish status. BR-07: it only moves forward, or to CANCELLED before it is served. */
public enum ItemStatus {
    PENDING("Chờ xác nhận"),
    WAITING("Chờ làm"),
    COOKING("Đang làm"),
    READY("Xong"),
    SERVED("Đã ra"),
    CANCELLED("Đã huỷ");

    private final String label;

    ItemStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canMoveTo(ItemStatus next) {
        return switch (this) {
            case PENDING -> next == WAITING || next == CANCELLED;
            case WAITING -> next == COOKING || next == CANCELLED;
            case COOKING -> next == READY || next == CANCELLED;
            case READY -> next == SERVED || next == CANCELLED;
            case SERVED, CANCELLED -> false;
        };
    }

    /** BR-12: confirmed and not cancelled dishes are charged. */
    public boolean isBillable() {
        return this != PENDING && this != CANCELLED;
    }
}
