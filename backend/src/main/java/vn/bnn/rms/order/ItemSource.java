package vn.bnn.rms.order;

public enum ItemSource {
    /** Entered by a waiter; goes straight to the kitchen. */
    STAFF,
    /** Sent by a guest from the table QR page; waits for staff confirmation (BR-10). */
    GUEST
}
