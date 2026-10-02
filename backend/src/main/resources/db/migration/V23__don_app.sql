-- P4-04: orders from delivery apps, entered by hand (FR-21, BR-47).

-- The price of a dish on each app, VAT included; no row means the dish is not sold on that app.
CREATE TABLE menu_item_app_price (
    menu_item_id BIGINT      NOT NULL REFERENCES menu_item (id) ON DELETE CASCADE,
    channel      VARCHAR(20) NOT NULL CHECK (channel IN ('GRABFOOD', 'SHOPEEFOOD')),
    price        BIGINT      NOT NULL CHECK (price >= 0),
    PRIMARY KEY (menu_item_id, channel)
);

-- An app order is a takeaway with its channel and the code the app gave it, unique in the channel among the orders
-- not cancelled: entering it twice is refused, while a cancelled one can be entered again.
ALTER TABLE orders ADD COLUMN channel VARCHAR(20) CHECK (channel IN ('GRABFOOD', 'SHOPEEFOOD'));
ALTER TABLE orders ADD COLUMN app_order_code VARCHAR(40);
ALTER TABLE orders ADD CONSTRAINT ck_orders_app
    CHECK ((channel IS NULL) = (app_order_code IS NULL) AND (channel IS NULL OR type = 'TAKEAWAY'));
CREATE UNIQUE INDEX ux_orders_app_code ON orders (channel, app_order_code) WHERE channel IS NOT NULL AND status <> 'CANCELLED';

-- The app takes the guest's money: when the shipper picks the order up, it is paid by the app of its channel.
ALTER TABLE payment DROP CONSTRAINT payment_method_check;
ALTER TABLE payment ADD CONSTRAINT payment_method_check
    CHECK (method IN ('CASH', 'BANK_TRANSFER', 'GRABFOOD', 'SHOPEEFOOD'));
