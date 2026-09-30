-- P2-05 (FR-12, BR-22): employee profile and pay. The defaults keep existing rows valid.

ALTER TABLE employee
    ADD COLUMN phone    VARCHAR(20),
    ADD COLUMN hired_on DATE,
    ADD COLUMN left_on  DATE,
    ADD COLUMN pay_type VARCHAR(10) NOT NULL DEFAULT 'HOURLY' CHECK (pay_type IN ('HOURLY', 'MONTHLY')),
    ADD COLUMN pay_rate BIGINT      NOT NULL DEFAULT 0 CHECK (pay_rate >= 0);
