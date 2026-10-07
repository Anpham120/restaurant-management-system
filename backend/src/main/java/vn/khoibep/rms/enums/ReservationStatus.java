package vn.khoibep.rms.enums;

/** BR-42: only a booking still waiting for its guests can change. */
public enum ReservationStatus {
    BOOKED("đang chờ khách tới"),
    SEATED("đã nhận khách"),
    CANCELLED("đã huỷ"),
    NO_SHOW("ghi không tới");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
