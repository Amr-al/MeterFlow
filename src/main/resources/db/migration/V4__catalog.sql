CREATE TABLE api_product (
                             id BIGSERIAL PRIMARY KEY,
                             code TEXT NOT NULL UNIQUE,
                             display_name TEXT NOT NULL,
                             active BOOLEAN NOT NULL DEFAULT TRUE,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             version BIGINT NOT NULL DEFAULT 0,

                             CONSTRAINT chk_api_product_code_not_blank
                                 CHECK (length(trim(code)) > 0),

                             CONSTRAINT chk_api_product_display_name_not_blank
                                 CHECK (length(trim(display_name)) > 0)
);

CREATE TABLE plan (
                      id BIGSERIAL PRIMARY KEY,
                      code TEXT NOT NULL UNIQUE,
                      display_name TEXT NOT NULL,
                      requests_per_minute INTEGER NOT NULL,
                      monthly_quota BIGINT NOT NULL,
                      active BOOLEAN NOT NULL DEFAULT TRUE,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      version BIGINT NOT NULL DEFAULT 0,

                      CONSTRAINT chk_plan_code_not_blank
                          CHECK (length(trim(code)) > 0),

                      CONSTRAINT chk_plan_display_name_not_blank
                          CHECK (length(trim(display_name)) > 0),

                      CONSTRAINT chk_plan_requests_per_minute_positive
                          CHECK (requests_per_minute > 0),

                      CONSTRAINT chk_plan_monthly_quota_positive
                          CHECK (monthly_quota > 0)
);

CREATE TABLE plan_entitlement (
                                  id BIGSERIAL PRIMARY KEY,

                                  plan_id BIGINT NOT NULL,
                                  api_product_id BIGINT NOT NULL,

                                  allowed BOOLEAN NOT NULL DEFAULT TRUE,
                                  credit_cost BIGINT NOT NULL DEFAULT 0,

                                  version BIGINT NOT NULL DEFAULT 0,

                                  CONSTRAINT fk_plan_entitlement_plan
                                      FOREIGN KEY (plan_id)
                                          REFERENCES plan(id),

                                  CONSTRAINT fk_plan_entitlement_product
                                      FOREIGN KEY (api_product_id)
                                          REFERENCES api_product(id),

                                  CONSTRAINT uq_plan_entitlement_plan_product
                                      UNIQUE (plan_id, api_product_id),

                                  CONSTRAINT chk_plan_entitlement_credit_cost
                                      CHECK (credit_cost >= 0)
);