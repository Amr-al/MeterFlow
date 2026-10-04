ALTER TABLE customer
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE customer
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE customer
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customer
    ADD CONSTRAINT chk_customer_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED'));

ALTER TABLE customer
    ADD CONSTRAINT chk_customer_name_not_blank
        CHECK (length(trim(name)) > 0);

ALTER TABLE customer
DROP COLUMN email;