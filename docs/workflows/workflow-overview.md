# Business Workflows

> Part of the [MeterFlow Technical Documentation](../README.md). The detailed message-level diagrams are in [Sequence Diagrams](sequence-diagrams.md).

## Table of Contents

1. [Actors](#1-actors)
2. [End-to-End Business Flow](#2-end-to-end-business-flow)
3. [WF-1 Catalog Setup](#3-wf-1-catalog-setup)
4. [WF-2 Customer Onboarding](#4-wf-2-customer-onboarding)
5. [WF-3 Subscription Lifecycle](#5-wf-3-subscription-lifecycle)
6. [WF-4 API Key Lifecycle](#6-wf-4-api-key-lifecycle)
7. [WF-5 Metered API Call (Data Plane)](#7-wf-5-metered-api-call-data-plane)
8. [Workflow Gaps](#8-workflow-gaps)

---

## 1. Actors

| Actor | Description | Interacts through |
|---|---|---|
| **Operator** | Staff who manage the catalog and customers | `/admin/**`, `/customers/**` (unauthenticated today) |
| **Customer system** | The paying customer's software, calling metered APIs | `/v1/**` with `X-API-Key` |
| **MeterFlow** | This service | — |
| **PostgreSQL** | System of record | JDBC |

## 2. End-to-End Business Flow

```mermaid
flowchart LR
    subgraph setup["Operator: one-time catalog setup"]
        P1["Create API product<br/>POST /admin/api-products"] --> P2["Create plan<br/>POST /admin/plans"]
        P2 --> P3["Grant entitlement<br/>PUT /admin/plans/PLAN/entitlements/PRODUCT"]
    end
    subgraph onboard["Operator: per customer"]
        C1["Create customer<br/>POST /customers"] --> C2["Subscribe to plan<br/>POST /customers/ID/subscriptions"]
        C2 --> C3["Issue API key<br/>POST /admin/customers/ID/api-keys"]
        C3 --> C4["Hand raw key to customer<br/>out of band"]
    end
    subgraph use["Customer system: ongoing"]
        U1["POST /v1/usage<br/>X-API-Key"] --> U2{"authenticated?"}
        U2 -- no --> U3["401"]
        U2 -- yes --> U4{"product exists?"}
        U4 -- no --> U5["404 PRODUCT_NOT_FOUND"]
        U4 -- yes --> U6["200 customerId, productCode"]
    end
    setup --> onboard --> use
```

**Explanation:** The operator first builds the commercial catalog, then onboards each customer: create the customer, subscribe them, issue a key. After that the customer's own system calls the data plane on its own. The subscribe step and the key-issue step are independent, so they can run in either order, because nothing in the code requires a subscription before a key is issued.

## 3. WF-1 Catalog Setup

| Step | Actor action | System behaviour | Result |
|---|---|---|---|
| 1 | `POST /admin/api-products {"code":"sms","displayName":"SMS API"}` | `CatalogService.createApiProduct`: normalizes to `SMS`, checks for duplicates, inserts with `active=true` | 201. Row in `api_product` |
| 2 | `POST /admin/plans {"code":"starter",...}` | `CatalogService.createPlan`: same pattern | 201. Row in `plan` |
| 3 | `PUT /admin/plans/starter/entitlements/sms {"allowed":true,"creditCost":1000}` | `CatalogService.updatePlanEntitlement`: inserts the row, or updates it if the pair already exists | 200. Row in `plan_entitlement` |
| 4 (optional) | `PATCH /admin/plans/STARTER {"requestsPerMinute":120}` | Partial update by dirty checking | 200 |
| 5 (optional) | `PATCH /admin/api-products/SMS {"active":false}` | Deactivate. **Today this does not block `/v1/usage`** | 200 |

Decision points and errors: duplicate code → 409. Unknown plan or product on the first upsert → 404. Invalid numbers → 400.

## 4. WF-2 Customer Onboarding

| Step | Action | Class.method | DB effect |
|---|---|---|---|
| 1 | `POST /customers {"name":"Acme"}` | `CustomerController.create` → `CustomerService.createCustomer` | `INSERT customer (status='ACTIVE')` |
| 2 | `POST /customers/42/subscriptions {"planCode":"STARTER"}` | `SubscriptionService.subscribe` | `INSERT subscription (ACTIVE, period = now … now+1 month)` |
| 3 | `POST /admin/customers/42/api-keys` | `ApiKeyService.createApiKey` | `INSERT api_key (hash, prefix, ACTIVE)` |
| 4 | Operator gives the raw key to the customer | — | — |

## 5. WF-3 Subscription Lifecycle

```mermaid
flowchart TD
    S0["POST /customers/ID/subscriptions"] --> V1{"customer exists?"}
    V1 -- no --> E1["404 CUSTOMER_NOT_FOUND"]
    V1 -- yes --> V2{"customer ACTIVE?"}
    V2 -- no --> E2["400 CUSTOMER_NOT_ACTIVE"]
    V2 -- yes --> V3{"plan exists?"}
    V3 -- no --> E3["404 PLAN_NOT_FOUND"]
    V3 -- yes --> V4{"plan active?"}
    V4 -- no --> E4["400 PLAN_NOT_ACTIVE"]
    V4 -- yes --> V5{"existing ACTIVE subscription?"}
    V5 -- yes --> E5["409 SUBSCRIPTION_ALREADY_ACTIVE"]
    V5 -- no --> INS["INSERT subscription, saveAndFlush"]
    INS --> V6{"unique index violated by a concurrent request?"}
    V6 -- yes --> E5
    V6 -- no --> OK["201 ACTIVE subscription"]
    OK --> CAN["PATCH .../active/cancel"]
    CAN --> CX["status CANCELLED"]
    CX --> S0
```

**Explanation:** Every decision point in `SubscriptionService.subscribe` maps to one `ErrorCode`. The last check (`V6`) exists only for concurrent requests and is enforced by the database. After a cancellation the customer can subscribe again, to the same plan or a different one. **There is no plan-change endpoint**: changing plans means cancel followed by subscribe. There is also no automatic expiry when `current_period_end` passes.

## 6. WF-4 API Key Lifecycle

```mermaid
flowchart LR
    A["POST /admin/customers/ID/api-keys"] --> B{"customer exists?"}
    B -- no --> E["404 CUSTOMER_NOT_FOUND"]
    B -- yes --> G["generate mf_live_ key<br/>hash with SHA-256<br/>store hash + prefix"]
    G --> R["201: raw key returned ONCE"]
    R --> U["key used on /v1/**"]
    U --> D["DELETE /admin/customers/ID/api-keys/KEYID"]
    D --> O{"key exists and belongs to customer?"}
    O -- no --> E2["404 API_KEY_NOT_FOUND"]
    O -- yes --> X["status REVOKED, revoked_at set<br/>idempotent"]
    X --> U2["next /v1 call with this key: 401"]
```

**Key rotation**, as supported by the current API: issue a new key, switch the client over, then revoke the old key. A customer can hold several `ACTIVE` keys at once.

## 7. WF-5 Metered API Call (Data Plane)

**Current implementation:**

| Step | Component | Action | Failure |
|---|---|---|---|
| 1 | `FilterChainProxy` | Selects `apiSecurityFilterChain` for `/v1/**` | — |
| 2 | `ApiKeyAuthenticationFilter` | Reads `X-API-Key` | Missing → 401 |
| 3 | `ApiKeyAuthenticationProvider` | SHA-256, then `findByKeyHash`, then a status check | Unknown or revoked → 401 |
| 4 | `SecurityContextHolder` | Stores `customerId` | — |
| 5 | `MeteringController.recordUsage` | `@Valid` body. `CurrentCustomer.getCustomerId()` | Blank code → 400 |
| 6 | `MeteringService.process` → `CatalogFacade.getApiProduct` | Product lookup | Unknown → 404 |
| 7 | — | Returns `{customerId, productCode}` | — |
| 8 | Filter `finally` | Clears the security context | — |

**Target M3 flow [Roadmap]**, extending steps 6–7. This is not implemented:

```mermaid
flowchart TD
    A["authenticated customerId + productCode"] --> B{"customer ACTIVE?"}
    B -- no --> X1["reject"]
    B -- yes --> C{"product exists and active?"}
    C -- no --> X2["reject"]
    C -- yes --> D{"ACTIVE subscription?"}
    D -- no --> X3["reject"]
    D -- yes --> E{"entitlement for plan and product, allowed?"}
    E -- no --> X4["reject"]
    E -- yes --> F["resolve creditCost, milli-credits"]
    F --> G["200 response with cost"]
    G -. "M4" .-> H["atomic wallet debit + ledger entry"]
    G -. "M5" .-> I["monthly quota check within billing period"]
    G -. "M6" .-> J["idempotency key claim"]
```

The error codes and HTTP statuses for the "reject" branches have **not been defined** in code yet **[Unverified]**.

## 8. Workflow Gaps

| Gap | Effect |
|---|---|
| No suspend or reactivate customer endpoint | `SUSPENDED` can only be set directly in the DB |
| No subscription expiry or renewal | Subscriptions remain `ACTIVE` after `current_period_end` |
| No plan change | Requires cancel + subscribe |
| No delete operations | Products, plans and customers can only be deactivated (and customers not even that) |
| No usage recording | Calls to `/v1/usage` leave no trace in the DB (**[Roadmap]** M4–M7) |
