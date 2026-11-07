# Entity Relationship Diagram

> Part of the [MeterFlow Technical Documentation](../README.md). Column-level detail is in [Database Design](database-design.md).
>
> Source: [`../diagrams/database-erd.mmd`](../diagrams/database-erd.mmd)

```mermaid
erDiagram
    customer ||--o{ subscription : "has (customer_id)"
    customer ||--o{ api_key : "owns (customer_id)"
    plan ||--o{ subscription : "is subscribed by (plan_id)"
    plan ||--o{ plan_entitlement : "grants (plan_id)"
    api_product ||--o{ plan_entitlement : "is granted by (api_product_id)"

    customer {
        BIGSERIAL id PK
        TEXT name "NOT NULL, CHECK trimmed length > 0"
        TEXT status "NOT NULL, default ACTIVE, CHECK ACTIVE or SUSPENDED"
        TIMESTAMPTZ created_at "NOT NULL"
        TIMESTAMPTZ updated_at "NOT NULL, default now"
        BIGINT version "NOT NULL, default 0"
    }

    api_product {
        BIGSERIAL id PK
        TEXT code UK "NOT NULL, CHECK not blank"
        TEXT display_name "NOT NULL, CHECK not blank"
        BOOLEAN active "NOT NULL, default true"
        TIMESTAMPTZ created_at "NOT NULL"
        TIMESTAMPTZ updated_at "NOT NULL"
        BIGINT version "NOT NULL, default 0"
    }

    plan {
        BIGSERIAL id PK
        TEXT code UK "NOT NULL, CHECK not blank"
        TEXT display_name "NOT NULL, CHECK not blank"
        INTEGER requests_per_minute "NOT NULL, CHECK > 0"
        BIGINT monthly_quota "NOT NULL, CHECK > 0"
        BOOLEAN active "NOT NULL, default true"
        TIMESTAMPTZ created_at "NOT NULL"
        TIMESTAMPTZ updated_at "NOT NULL"
        BIGINT version "NOT NULL, default 0"
    }

    plan_entitlement {
        BIGSERIAL id PK
        BIGINT plan_id FK "NOT NULL, UNIQUE with api_product_id"
        BIGINT api_product_id FK "NOT NULL"
        BOOLEAN allowed "NOT NULL, default true"
        BIGINT credit_cost "NOT NULL, default 0, CHECK >= 0"
        BIGINT version "NOT NULL, default 0"
    }

    subscription {
        BIGSERIAL id PK
        BIGINT customer_id FK "NOT NULL, partial UNIQUE where ACTIVE"
        BIGINT plan_id FK "NOT NULL"
        TEXT status "NOT NULL, CHECK ACTIVE, CANCELLED, EXPIRED"
        TIMESTAMPTZ started_at "NOT NULL"
        TIMESTAMPTZ current_period_start "NOT NULL"
        TIMESTAMPTZ current_period_end "NOT NULL, CHECK end > start"
        TIMESTAMPTZ created_at "NOT NULL"
        TIMESTAMPTZ updated_at "NOT NULL"
        BIGINT version "NOT NULL, default 0"
    }

    api_key {
        BIGSERIAL id PK
        BIGINT customer_id FK "NOT NULL, indexed"
        TEXT key_prefix "NOT NULL, indexed"
        TEXT key_hash UK "NOT NULL, SHA-256 hex"
        TEXT status "NOT NULL, CHECK ACTIVE or REVOKED"
        TIMESTAMPTZ created_at "NOT NULL"
        TIMESTAMPTZ revoked_at "nullable"
        BIGINT version "NOT NULL, default 0"
    }
```

## Relationship Explanation

| Relationship | Cardinality | Enforced by | JPA mapping | Business meaning |
|---|---|---|---|---|
| `customer` → `subscription` | 1 : 0..N (and at most **1 ACTIVE**) | `fk_subscription_customer` + `uq_subscription_active_per_customer` | `Subscription.customerId` (`Long`, no association) | A customer's subscription history. Only one subscription is current |
| `customer` → `api_key` | 1 : 0..N | `fk_api_key_customer` | `ApiKey.customerId` (`Long`) | A customer may hold several keys (for example, for rotation) |
| `plan` → `subscription` | 1 : 0..N | `fk_subscription_plan` | `Subscription.planId` (`Long`) | Which tier the customer is on |
| `plan` → `plan_entitlement` | 1 : 0..N | `fk_plan_entitlement_plan` | `@ManyToOne(LAZY) PlanEntitlement.plan` | Products available in the plan |
| `api_product` → `plan_entitlement` | 1 : 0..N | `fk_plan_entitlement_product` | `@ManyToOne(LAZY) PlanEntitlement.apiProduct` | Plans that include the product |
| `plan` ↔ `api_product` | N : M via `plan_entitlement` | `uq_plan_entitlement_plan_product` | — | Many-to-many with attributes (`allowed`, `credit_cost`) |

## Module Ownership of Tables

```mermaid
flowchart LR
    subgraph tenancy["tenancy module"]
        customer[(customer)]
    end
    subgraph catalog["catalog module"]
        api_product[(api_product)]
        plan[(plan)]
        plan_entitlement[(plan_entitlement)]
    end
    subgraph subscription["subscription module"]
        subscription_t[(subscription)]
    end
    subgraph credential["credential module"]
        api_key[(api_key)]
    end
    subscription_t -. "customer_id FK, ID only in Java" .-> customer
    subscription_t -. "plan_id FK, ID only in Java" .-> plan
    api_key -. "customer_id FK, ID only in Java" .-> customer
    plan_entitlement -- "JPA ManyToOne" --> plan
    plan_entitlement -- "JPA ManyToOne" --> api_product
```

**Explanation:** Each table belongs to exactly one module. Inside a module, JPA associations are used (solid lines). Across modules, the database keeps referential integrity with FKs, but the Java model holds only the ID (dotted lines). This keeps module boundaries explicit and prevents lazy loading from crossing modules.
