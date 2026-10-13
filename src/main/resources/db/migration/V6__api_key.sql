CREATE TABLE api_key (
         id BIGSERIAL PRIMARY KEY,

         customer_id BIGINT NOT NULL,

         key_prefix TEXT NOT NULL,
         key_hash TEXT NOT NULL UNIQUE,

         status TEXT NOT NULL DEFAULT 'ACTIVE',

         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
         revoked_at TIMESTAMPTZ,

         version BIGINT NOT NULL DEFAULT 0,

         CONSTRAINT fk_api_key_customer
             FOREIGN KEY (customer_id)
                 REFERENCES customer(id),

         CONSTRAINT chk_api_key_status
             CHECK (status IN ('ACTIVE', 'REVOKED')),

         CONSTRAINT chk_api_key_prefix_not_blank
             CHECK (length(trim(key_prefix)) > 0),

         CONSTRAINT chk_api_key_hash_not_blank
             CHECK (length(trim(key_hash)) > 0)
);

CREATE INDEX idx_api_key_customer
    ON api_key(customer_id);

CREATE INDEX idx_api_key_prefix
    ON api_key(key_prefix);