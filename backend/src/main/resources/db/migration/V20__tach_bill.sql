-- P1-07: one order may be paid in several parts (split bill); it closes once they cover the bill (FR-08.12, BR-43).
-- Never taking more than the bill is checked by PaymentService while the order row is locked. Still one pending
-- transfer per order at a time (ux_payment_pending_order).
DROP INDEX ux_payment_paid_order;
