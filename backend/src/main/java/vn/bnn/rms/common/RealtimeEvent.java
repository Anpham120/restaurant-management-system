package vn.bnn.rms.common;

/**
 * Small change notice pushed over STOMP after commit. Clients refetch over REST when they receive it.
 *
 * @param guestToken QR token of the table, so the guest page of that table is notified too; never sent to clients
 */
public record RealtimeEvent(String type, Long orderId, Long tableId, String guestToken) {

    public static final String ORDER_CHANGED = "ORDER_CHANGED";
    public static final String PAYMENT_PAID = "PAYMENT_PAID";
    public static final String MENU_CHANGED = "MENU_CHANGED";
    public static final String TABLES_CHANGED = "TABLES_CHANGED";
    public static final String BANK_TRANSACTION = "BANK_TRANSACTION";

    /** What clients actually receive. */
    public record Message(String type, Long orderId, Long tableId) {
    }

    public Message toMessage() {
        return new Message(type, orderId, tableId);
    }
}
