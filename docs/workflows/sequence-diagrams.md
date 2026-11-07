# Sequence Diagrams

> Part of the [MeterFlow Technical Documentation](../README.md). Every diagram traces real classes and methods. Error paths are shown with `alt` blocks.

## Table of Contents

1. [SD-1 Create API Product](#sd-1-create-api-product)
2. [SD-2 Partial Plan Update](#sd-2-partial-plan-update)
3. [SD-3 Upsert Plan Entitlement](#sd-3-upsert-plan-entitlement)
4. [SD-4 Subscribe Customer to Plan](#sd-4-subscribe-customer-to-plan)
5. [SD-5 Cancel Active Subscription](#sd-5-cancel-active-subscription)
6. [SD-6 Issue API Key](#sd-6-issue-api-key)
7. [SD-7 Revoke API Key](#sd-7-revoke-api-key)
8. [SD-8 Metered Usage Call](#sd-8-metered-usage-call)

Related diagrams elsewhere:

- [Request lifecycle](../technical-design/application-flow.md#1-request-lifecycle)
- [Authentication flow](../technical-design/security.md#3-authentication-flow)
- [Concurrent subscription race](../technical-design/performance.md#2-concurrency-control)
- [Database interaction](../database/database-design.md#9-database-interaction-flows)
- [Background processing](../technical-design/application-flow.md#9-background-jobs-and-asynchronous-processing)

---

## SD-1 Create API Product

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as CatalogAdminController
    participant S as CatalogService
    participant R as ApiProductRepository
    participant DB as PostgreSQL
    participant GEH as GlobalExceptionHandler

    Op->>C: POST /admin/api-products code sms, displayName SMS API
    C->>C: Valid CreateApiProductRequest
    alt blank code or displayName
        C-->>GEH: MethodArgumentNotValidException
        GEH-->>Op: 400 VALIDATION_FAILED with fieldErrors
    else valid
        C->>S: createApiProduct(request)
        S->>S: code = SMS, displayName trimmed
        S->>R: existsByCode(SMS)
        R->>DB: SELECT from api_product WHERE code = SMS
        alt exists
            S-->>GEH: MeterFlowException DUPLICATE_RESOURCE
            GEH-->>Op: 409 ProblemDetail
        else new
            S->>R: save(new ApiProduct(SMS, SMS API, now))
            R->>DB: INSERT INTO api_product RETURNING id
            S-->>C: ApiProductResponse.from(saved)
            C-->>Op: 201 Created
        end
    end
```

**Explanation:** This shows the standard create pattern used throughout the catalog: validate, normalize, pre-check for a duplicate, insert. A concurrent duplicate that slips past `existsByCode` fails on the `UNIQUE(code)` constraint and becomes a `500`. That race is not drawn above.

## SD-2 Partial Plan Update

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as CatalogAdminController
    participant S as CatalogService.updatePlan
    participant R as PlanRepository
    participant P as Plan entity
    participant DB as PostgreSQL

    Op->>C: PATCH /admin/plans/starter requestsPerMinute 120
    C->>S: updatePlan(request, starter)
    S->>R: findByCode(STARTER)
    R->>DB: SELECT plan
    alt not found
        S-->>Op: 404 PLAN_NOT_FOUND
    else found
        opt active is not null
            S->>P: activate(now) or deactivate(now)
        end
        opt displayName is not null
            alt blank after trim
                S-->>Op: 400 VALIDATION_FAILED, whole TX rolled back
            else
                S->>P: rename(trimmed, now)
            end
        end
        opt requestsPerMinute or monthlyQuota not null
            S->>P: updateLimits(newOrCurrentRpm, newOrCurrentQuota, now)
        end
        S-->>C: PlanResponse.from(plan)
        Note over S,DB: commit, dirty check
        S->>DB: UPDATE plan SET ... WHERE id = ? AND version = ?
        C-->>Op: 200 OK
    end
```

**Explanation:** No `save()` call is needed. The entity is managed, so Hibernate writes the changes at commit. The `@Version` predicate makes a lost update impossible. A conflicting concurrent update fails, currently with a `500`.

## SD-3 Upsert Plan Entitlement

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as CatalogAdminController
    participant S as CatalogService.updatePlanEntitlement
    participant ER as PlanEntitlementRepository
    participant PR as PlanRepository
    participant AR as ApiProductRepository
    participant DB as PostgreSQL

    Op->>C: PUT /admin/plans/starter/entitlements/sms allowed true, creditCost 1000
    C->>S: updatePlanEntitlement(request, starter, sms)
    alt either code blank
        S-->>Op: 400 INVALID_REQUEST
    end
    S->>ER: findByPlanCodeAndApiProductCode(STARTER, SMS)
    ER->>DB: SELECT e JOIN plan JOIN api_product
    alt entitlement exists
        S->>S: entitlement.updateEntitlement(true, 1000)
        S->>DB: lazy SELECT plan and api_product for the response
        Note over S,DB: commit, UPDATE plan_entitlement
        S-->>Op: 200 PlanEntitlementResponse
    else not found
        S->>PR: findByCode(STARTER)
        alt no plan
            S-->>Op: 404 PLAN_NOT_FOUND
        end
        S->>AR: findByCode(SMS)
        alt no product
            S-->>Op: 404 PRODUCT_NOT_FOUND
        end
        S->>ER: save(new PlanEntitlement(plan, product, true, 1000))
        ER->>DB: INSERT INTO plan_entitlement
        S-->>Op: 200 PlanEntitlementResponse
    end
```

**Explanation:** This is an idempotent `PUT`. The response must be built inside the transaction because the associations are `LAZY` and Open Session in View is disabled.

## SD-4 Subscribe Customer to Plan

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as SubscriptionController
    participant S as SubscriptionService.subscribe
    participant CF as CustomerFacade
    participant KF as CatalogFacade
    participant R as SubscriptionRepository
    participant DB as PostgreSQL

    Op->>C: POST /customers/42/subscriptions planCode starter
    C->>S: subscribe(42, starter)
    Note over S: BEGIN TX, read-write
    S->>CF: getCustomer(42)
    CF->>DB: SELECT customer, joins the TX
    alt not found
        S-->>Op: 404 CUSTOMER_NOT_FOUND
    else status SUSPENDED
        S-->>Op: 400 CUSTOMER_NOT_ACTIVE
    end
    S->>KF: getPlan(starter)
    KF->>DB: SELECT plan WHERE code = STARTER
    alt not found
        S-->>Op: 404 PLAN_NOT_FOUND
    else inactive
        S-->>Op: 400 PLAN_NOT_ACTIVE
    end
    S->>R: findByCustomerIdAndStatus(42, ACTIVE)
    R->>DB: SELECT subscription
    alt already active
        S-->>Op: 409 SUBSCRIPTION_ALREADY_ACTIVE
    end
    S->>S: periodEnd = now in UTC plus 1 month
    S->>R: saveAndFlush(new Subscription)
    R->>DB: INSERT INTO subscription
    alt unique_violation uq_subscription_active_per_customer
        DB-->>S: DataIntegrityViolationException
        S-->>Op: 409 SUBSCRIPTION_ALREADY_ACTIVE, ROLLBACK
    else other integrity violation
        S-->>Op: 500 INTERNAL_ERROR
    else ok
        Note over S: COMMIT
        S-->>Op: 201 SubscriptionResponse
    end
```

**Explanation:** The facade calls join the caller's transaction (propagation `REQUIRED`). `saveAndFlush` forces the INSERT to run right away, so the database's unique-index check happens inside the `try/catch`. The concurrent case is drawn in [Performance](../technical-design/performance.md#2-concurrency-control).

## SD-5 Cancel Active Subscription

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as SubscriptionController
    participant S as SubscriptionService
    participant R as SubscriptionRepository
    participant DB as PostgreSQL

    Op->>C: PATCH /customers/42/subscriptions/active/cancel
    C->>S: cancelActiveSubscription(42)
    S->>R: findByCustomerIdAndStatus(42, ACTIVE)
    R->>DB: SELECT
    alt none
        S-->>Op: 404 SUBSCRIPTION_NOT_FOUND
    else found
        S->>S: subscription.cancel(now)
        Note over S,DB: commit, UPDATE status CANCELLED with version check
        S-->>Op: 200 status CANCELLED
    end
```

**Explanation:** Once the row is `CANCELLED`, it no longer counts toward the partial unique index, so the customer can subscribe again straight away.

## SD-6 Issue API Key

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as ApiKeyAdminController
    participant S as ApiKeyService.createApiKey
    participant CF as CustomerFacade
    participant G as ApiKeyGenerator
    participant H as ApiKeyHasher
    participant R as ApiKeyRepository
    participant DB as PostgreSQL

    Op->>C: POST /admin/customers/42/api-keys
    C->>S: createApiKey(42)
    S->>CF: getCustomer(42)
    alt not found
        S-->>Op: 404 CUSTOMER_NOT_FOUND
    end
    S->>G: generate()
    G-->>S: mf_live_ plus 43 base64url chars
    S->>G: extractPrefix(raw)
    G-->>S: first 16 chars
    S->>H: hash(raw)
    H-->>S: 64 hex chars
    S->>R: save(new ApiKey(42, prefix, hash, now))
    R->>DB: INSERT INTO api_key status ACTIVE
    S-->>C: CreateApiKeyResponse with id, raw apiKey, prefix, createdAt
    C-->>Op: 201 Created, raw key shown once
```

**Explanation:** The raw key exists only in memory and in this single response. The database never sees it.

## SD-7 Revoke API Key

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operator
    participant C as ApiKeyAdminController
    participant S as ApiKeyService.revokeApiKey
    participant CF as CustomerFacade
    participant R as ApiKeyRepository
    participant DB as PostgreSQL

    Op->>C: DELETE /admin/customers/42/api-keys/7
    C->>S: revokeApiKey(42, 7)
    S->>CF: getCustomer(42)
    alt customer not found
        S-->>Op: 404 CUSTOMER_NOT_FOUND
    end
    S->>R: findById(7)
    alt key not found
        S-->>Op: 404 API_KEY_NOT_FOUND
    else key.customerId is not 42
        S-->>Op: 404 API_KEY_NOT_FOUND, does not reveal that the key exists
    else status already REVOKED
        S-->>Op: 200 unchanged, idempotent
    else ACTIVE
        S->>S: apiKey.revoke(now)
        Note over S,DB: commit, UPDATE api_key SET status REVOKED, revoked_at
        S-->>Op: 200 status REVOKED
    end
```

## SD-8 Metered Usage Call

```mermaid
sequenceDiagram
    autonumber
    participant CS as Customer System
    participant F as ApiKeyAuthenticationFilter
    participant P as ApiKeyAuthenticationProvider
    participant DB as PostgreSQL
    participant MC as MeteringController
    participant CC as CurrentCustomer
    participant MS as MeteringService
    participant TF as CustomerFacade
    participant KF as CatalogFacade
    participant SF as SubscriptionFacade

    CS->>F: POST /v1/usage, X-API-Key, productCode sms
    F->>P: authenticate
    P->>DB: SELECT api_key WHERE key_hash = sha256
    alt invalid or revoked
        F-->>CS: 401
    else valid
        P-->>F: principal customerId 42
        F->>MC: continue chain, then DispatcherServlet
        MC->>MC: Valid MeteredRequest
        alt productCode blank
            MC-->>CS: 400 VALIDATION_FAILED
        end
        MC->>CC: getCustomerId()
        CC-->>MC: 42
        MC->>MS: authorize(42, sms)
        Note over MS: BEGIN readOnly TX, facades join it
        MS->>TF: getCustomer(42)
        TF->>DB: SELECT customer WHERE id = 42
        alt status SUSPENDED
            MS-->>CS: 403 CUSTOMER_SUSPENDED
        end
        MS->>KF: getApiProduct(sms)
        KF->>DB: SELECT api_product WHERE code = SMS
        alt not found
            MS-->>CS: 404 PRODUCT_NOT_FOUND
        else active = false
            MS-->>CS: 403 PRODUCT_INACTIVE
        end
        MS->>SF: findActiveSubscription(42)
        SF->>DB: SELECT subscription WHERE customer_id = 42 AND status = ACTIVE
        alt empty
            MS-->>CS: 403 NO_ACTIVE_SUBSCRIPTION
        end
        MS->>KF: findEntitlement(planId 3, productId 7)
        KF->>DB: SELECT plan_entitlement WHERE plan_id = 3 AND api_product_id = 7
        alt empty or allowed = false
            MS-->>CS: 403 PRODUCT_NOT_ENTITLED
        end
        MS-->>MC: UsageAuthorization(42, sub 12, plan 3, product 7, SMS, 1500)
        Note over MS: COMMIT readOnly TX
        MC->>MC: MeteredResponse.from(authorization)
        MC-->>CS: 200 customerId 42, productCode SMS, subscriptionId 12, planId 3, creditCost 1500
        F->>F: finally clearContext()
    end
```

**Explanation:** This is the complete M3 data-plane path. An authorized call costs five indexed queries (one for authentication, four for authorization) and performs **no writes**. Each rejection is a `MeterFlowException` that `GlobalExceptionHandler` turns into a `ProblemDetail` with the `errorCode` shown. The arrows from `MeteringService` straight to the client are a shorthand for that path. `apiProductId` (7) travels in `UsageAuthorization` but is dropped from the HTTP response. Nothing is debited or recorded yet (**[Roadmap]** M4).
