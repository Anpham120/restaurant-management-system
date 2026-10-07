package vn.khoibep.rms.enums;

/** BR-47: the delivery app an order came from. */
public enum Channel {
    GRABFOOD("GrabFood"),
    SHOPEEFOOD("ShopeeFood");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    /** As staff and the stock history write it. */
    public String label() {
        return label;
    }
}
