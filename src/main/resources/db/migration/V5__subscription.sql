CREATE TABLE subscription (
                              id BIGSERIAL PRIMARY KEY,

                              customer_id BIGINT NOT NULL,
                              plan_id BIGINT NOT NULL,

                              status TEXT NOT NULL DEFAULT 'ACTIVE',

                              started_at TIMESTAMPTZ NOT NULL,
                              current_period_start TIMESTAMPTZ NOT NULL,
                              current_period_end TIMESTAMPTZ NOT NULL,

                              created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              version BIGINT NOT NULL DEFAULT 0,

                              CONSTRAINT fk_subscription_customer
                                  FOREIGN KEY (customer_id)
                                      REFERENCES customer(id),

                              CONSTRAINT fk_subscription_plan
                                  FOREIGN KEY (plan_id)
                                      REFERENCES plan(id),

                              CONSTRAINT chk_subscription_status
                                  CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),

                              CONSTRAINT chk_subscription_period
                                  CHECK (current_period_end > current_period_start)
);

CREATE UNIQUE INDEX uq_subscription_active_per_customer
    ON subscription (customer_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_subscription_customer_status
    ON subscription (customer_id, status);