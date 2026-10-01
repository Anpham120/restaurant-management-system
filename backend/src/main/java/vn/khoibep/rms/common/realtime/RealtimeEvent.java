package vn.khoibep.rms.common.realtime;

/**
 * Small change notice pushed over STOMP after commit. Clients refetch over REST when they receive it.
 *
 * @param guestToken QR token of the table, so the guest page of that table is notified too; never sent to clients
 * @param alert      set when staff screens should ring (FR-07.5), otherwise null
 */
public record RealtimeEvent(String type, Long orderId, Long tableId, String guestToken, Alert alert) {

    public static final String ORDER_CHANGED = "ORDER_CHANGED";
    public static final String PAYMENT_PAID = "PAYMENT_PAID";
    public static final String MENU_CHANGED = "MENU_CHANGED";
    public static final String TABLES_CHANGED = "TABLES_CHANGED";
    public static final String BANK_TRANSACTION = "BANK_TRANSACTION";
    public static final String REQUESTS_CHANGED = "REQUESTS_CHANGED";
    /** The SePay webhook started failing in a row, or works again (BR-32). */
    public static final String WEBHOOK_STATUS = "WEBHOOK_STATUS";
    /** A discount was asked for, approved, rejected or cancelled (FR-08.11). */
    public static final String ADJUSTMENTS_CHANGED = "ADJUSTMENTS_CHANGED";

    /** Why staff screens ring. Each screen decides which alerts it rings for. */
    public enum Alert {
        /** Dishes reached the kitchen: sent by staff, or guest dishes a waiter confirmed (BR-10). */
        NEW_DISHES,
        /** A guest sent dishes from the table QR code; they wait for a waiter. */
        GUEST_DISHES,
        /** The kitchen finished a dish. */
        DISH_READY,
        /** A guest called a waiter or asked for the bill (FR-06.6). */
        SERVICE_REQUEST
    }

    /** What clients actually receive. */
    public record Message(String type, Long orderId, Long tableId, Alert alert) {
    }

    public Message toMessage() {
        return new Message(type, orderId, tableId, alert);
    }
}
