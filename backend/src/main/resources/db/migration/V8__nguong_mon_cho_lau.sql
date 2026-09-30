-- P1-06 (FR-07.4, FR-11.3, BR-28): after how many minutes in the kitchen a dish is shown as late.
-- The default keeps the settings row valid.

ALTER TABLE restaurant_settings
    ADD COLUMN wait_alert_minutes INTEGER NOT NULL DEFAULT 15 CHECK (wait_alert_minutes BETWEEN 1 AND 120);
