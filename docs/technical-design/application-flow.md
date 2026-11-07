# Application Flow

> Part of the [MeterFlow Technical Documentation](../README.md). This document explains how a request travels through the system and the cross-cutting mechanisms it meets on the way: security, validation, error handling, transactions and background processing.

## Table of Contents

1. [Request Lifecycle](#1-request-lifecycle)
2. [Security Filter Chain Selection](#2-security-filter-chain-selection)
3. [Controller Layer Conventions](#3-controller-layer-conventions)
4. [Service Layer Conventions](#4-service-layer-conventions)
5. [Validation](#5-validation)
6. [Exception Handling](#6-exception-handling)
7. [Transaction Management](#7-transaction-management)
8. [Persistence Interaction](#8-persistence-interaction)
9. [Background Jobs and Asynchronous Processing](#9-background-jobs-and-asynchronous-processing)
10. [Logging and Observability](#10-logging-and-observability)
11. [Time Handling](#11-time-handling)

---

## 1. Request Lifecycle

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Tomcat as Tomcat worker thread
    participant FCP as FilterChainProxy
    participant Chain as Selected SecurityFilterChain
    participant DS as DispatcherServlet
    participant Ctrl as RestController
    participant Val as Bean Validation
    participant Svc as Service via Transactional proxy
    participant Repo as Spring Data Repository
    participant DB as PostgreSQL
    participant GEH as GlobalExceptionHandler

    Client->>Tomcat: HTTP request
    Tomcat->>FCP: servlet filter pipeline
    FCP->>Chain: first chain whose matcher matches
    alt chain rejects the request
        Chain-->>Client: 401 from the AuthenticationEntryPoint
    else chain allows the request
        Chain->>DS: continue
        DS->>Ctrl: HandlerMapping resolves the handler method
        Ctrl->>Val: Jackson deserializes the body, then Valid runs
        alt validation fails
            Val-->>GEH: MethodArgumentNotValidException
            GEH-->>Client: 400 ProblemDetail VALIDATION_FAILED with fieldErrors
        else valid
            Ctrl->>Svc: call the service method
            Svc->>Svc: BEGIN transaction, open EntityManager
            Svc->>Repo: query or save
            Repo->>DB: SQL
            DB-->>Repo: rows
            alt business rule violated
                Svc-->>GEH: MeterFlowException, transaction rolled back
                GEH-->>Client: ProblemDetail with the status from ErrorCode
            else success
                Svc->>Svc: flush dirty entities, then COMMIT
                Svc-->>Ctrl: response record
                Ctrl-->>Client: 200 or 201 JSON
            end
        end
    end
```

**Explanation**

1. Tomcat gives each request a worker thread. Everything that follows runs **synchronously on that thread**: the security context in `ThreadLocal`, the transaction and the JDBC connection.
2. `FilterChainProxy` picks a single `SecurityFilterChain`. See [§2](#2-security-filter-chain-selection).
3. `DispatcherServlet` routes the request to a controller method. Jackson turns the JSON into an immutable `record`, and `@Valid` triggers Jakarta Bean Validation.
4. Controllers are thin. They pass the call to a `@Service`, whose `@Transactional` proxy opens a transaction.
5. Business-rule violations are raised as `MeterFlowException`. Because it is a `RuntimeException`, the transaction rolls back, and `GlobalExceptionHandler` turns it into a `ProblemDetail`.
6. On success, Hibernate flushes any dirty managed entities on commit. That is how update endpoints work without calling `save()`.

## 2. Security Filter Chain Selection

```mermaid
flowchart TD
    R["Incoming request"] --> M{"Path matches /v1/** ?"}
    M -- yes --> C1["apiSecurityFilterChain @Order 1"]
    M -- no --> C2["temporaryAdminSecurityFilterChain @Order 2"]

    C1 --> H{"X-API-Key header present and not blank?"}
    H -- no --> AN["AnonymousAuthenticationFilter sets an anonymous token"]
    AN --> AZ1{"AuthorizationFilter: anyRequest authenticated?"}
    AZ1 -- "anonymous: AccessDenied" --> ETF["ExceptionTranslationFilter"]
    ETF --> EP["AuthenticationEntryPoint: sendError 401"]
    H -- yes --> AUTH["ApiKeyAuthenticationFilter: authenticate via ProviderManager"]
    AUTH -- "BadCredentialsException" --> CLR["clear SecurityContext"] --> EP
    AUTH -- success --> SC["SecurityContextHolder.setContext customerId"]
    SC --> AZ2{"AuthorizationFilter: authenticated?"}
    AZ2 -- yes --> DS1["DispatcherServlet then MeteringController"]
    DS1 --> FIN["finally: SecurityContextHolder.clearContext"]

    C2 --> PA["authorizeHttpRequests anyRequest permitAll, CSRF disabled"]
    PA --> DS2["DispatcherServlet then admin or customer controllers"]
```

Configured in [`SecurityConfig`](../../src/main/java/com/meterflow/security/SecurityConfig.java):

| Setting | `apiSecurityFilterChain` (`@Order(1)`) | `temporaryAdminSecurityFilterChain` (`@Order(2)`) |
|---|---|---|
| Matcher | `securityMatcher("/v1/**")` | none, so any request |
| CSRF | disabled | disabled |
| Session | `STATELESS` | Spring default |
| Authorization | `anyRequest().authenticated()` | `anyRequest().permitAll()` |
| Custom filter | `ApiKeyAuthenticationFilter`, placed before `UsernamePasswordAuthenticationFilter` | — |
| Entry point | lambda: `response.sendError(401, "Unauthorized")` | default |
| `AuthenticationManager` | a local `new ProviderManager(apiKeyAuthenticationProvider)` | — |

The authentication internals are covered in detail in [Security](security.md).

## 3. Controller Layer Conventions

| Convention | Example |
|---|---|
| Constructor injection, no field injection | `CatalogAdminController(CatalogService)` |
| `ResponseEntity` with an explicit status for creates (`201 CREATED`) | `ResponseEntity.status(HttpStatus.CREATED).body(response)` |
| Request bodies are records annotated `@Valid @RequestBody` | `CreatePlanRequest` |
| Resources addressed by **business code** (catalog) or **numeric ID** (customer, key) | `/admin/plans/{code}`, `/admin/customers/{customerId}/api-keys/{apiKeyId}` |
| Path variables are not validated in the controller. Blank-code checks live in the service | `CatalogService.getPlanByCode` |
| Data-plane controllers take identity from `CurrentCustomer`, **never** from the request | `MeteringController.recordUsage` |

## 4. Service Layer Conventions

| Convention | Example |
|---|---|
| One `@Service` per module owns all of its business rules | `CatalogService`, `SubscriptionService` |
| Every public method declares `@Transactional`, with `readOnly = true` for reads | `CatalogService.getPlans` |
| Codes are normalized with `trim().toUpperCase(Locale.ROOT)` before any lookup | All catalog methods |
| Timestamps come from `Instant.now(clock)` | Every create and update |
| Entities are mutated through domain methods, then saved by dirty checking (no `save()` on update) | `CatalogService.updatePlan` |
| Responses are mapped while the transaction is still open (required because `open-in-view: false`) | `PlanEntitlementResponse.from(entitlement)` touches LAZY associations |
| Errors are raised as `new MeterFlowException(ErrorCode.X, "message")` | Everywhere |

## 5. Validation

Validation happens in three layers:

| Layer | Mechanism | Failure → response |
|---|---|---|
| 1. Request body | Jakarta constraints on records + `@Valid` | `MethodArgumentNotValidException` → `400`, `errorCode=VALIDATION_FAILED`, `fieldErrors` map |
| 1b. Method parameter | Constraint on a handler parameter (`@Min(0) int page`). Spring MVC built-in method validation | `HandlerMethodValidationException` → `400`, `VALIDATION_FAILED` (without `fieldErrors`) |
| 2. Service | Explicit checks (blank path code, blank `displayName` on patch, entity status) | `MeterFlowException` → status from `ErrorCode` |
| 3. Database | `CHECK`, `NOT NULL`, `UNIQUE`, FK constraints | `DataIntegrityViolationException`. It is translated to `409` **only** for `uq_subscription_active_per_customer`. Every other violation becomes a `500` |

Request constraint inventory:

| Record | Field | Constraint(s) |
|---|---|---|
| `CreateCustomerRequest` | `name` | `@NotBlank` |
| `CreateApiProductRequest` | `code`, `displayName` | `@NotBlank` |
| `UpdateApiProductRequest` | `active` | `@NotNull` |
| `CreatePlanRequest` | `code`, `displayName` | `@NotBlank` |
| | `requestsPerMinute` | `@NotNull @Positive` |
| | `monthlyQuota` | `@NotNull @Positive` |
| `UpdatePlanRequest` | `requestsPerMinute`, `monthlyQuota` | `@Positive` (nullable, meaning "unchanged") |
| | `displayName`, `active` | none (blank `displayName` is rejected in the service) |
| `UpsertPlanEntitlementRequest` | `allowed` | `@NotNull` |
| | `creditCost` | `@NotNull @PositiveOrZero` |
| `CreateSubscriptionRequest` | `planCode` | `@NotBlank` |
| `MeteredRequest` | `productCode` | `@NotBlank` |
| `CatalogAdminController.getApiProductsByPage` | `page` | `@Min(0)`, default `0` |

## 6. Exception Handling

[`GlobalExceptionHandler`](../../src/main/java/com/meterflow/common/error/GlobalExceptionHandler.java) extends `ResponseEntityExceptionHandler` and always produces `ProblemDetail` (RFC 7807, `application/problem+json`).

```mermaid
flowchart TD
    E["Exception thrown in a controller or service"] --> T{"Type?"}
    T -- MeterFlowException --> H1["handleMeterFlowException<br/>log WARN<br/>status from ErrorCode<br/>errorCode = enum name"]
    T -- MethodArgumentNotValidException --> H2["handleMethodArgumentNotValid<br/>400, VALIDATION_FAILED<br/>fieldErrors map"]
    T -- HandlerMethodValidationException --> H3["handleHandlerMethodValidationException<br/>400, VALIDATION_FAILED"]
    T -- "other Spring MVC exceptions<br/>unreadable JSON, 404 no handler, 405" --> H4["handleExceptionInternal<br/>adds errorCode:<br/>404 RESOURCE_NOT_FOUND<br/>405 METHOD_NOT_ALLOWED<br/>400 MALFORMED_REQUEST<br/>else HTTP_nnn"]
    T -- "any other Exception<br/>incl. DataIntegrityViolation,<br/>OptimisticLockingFailure,<br/>IllegalStateException" --> H5["handleGeneral<br/>log ERROR with stack trace<br/>500, INTERNAL_ERROR<br/>correlationId UUID"]
    H1 & H2 & H3 & H4 & H5 --> R["ProblemDetail + timestamp from Clock"]

    S["Exception inside a security filter, e.g. 401"] -. "never reaches the advice" .-> SE["response.sendError 401<br/>then the container forwards to /error<br/>Spring Boot default error JSON"]
```

**Important behaviours**

- **Internal details never leak.** Unexpected errors return the generic message `"An unexpected error occurred"` plus a `correlationId`. The same ID is logged together with the stack trace, so support can match the two.
- **Security errors are outside this contract.** `401` responses come from `response.sendError`, so the body is Spring Boot's default error JSON, not a `ProblemDetail` with `errorCode`. **[Unverified]** The exact body depends on Boot's `ErrorAttributes` defaults. The tests assert only the status.
- **Concurrency conflicts are not mapped.** `ErrorCode.CONCURRENT_MODIFICATION` (409) exists, but no handler maps `ObjectOptimisticLockingFailureException` to it, so such conflicts surface as `500`.

## 7. Transaction Management

| Aspect | Behaviour |
|---|---|
| Demarcation | Declarative `@Transactional` (Spring) on public service methods |
| Read operations | `@Transactional(readOnly = true)`: Hibernate uses manual flush mode, so no dirty checking happens on commit |
| Propagation | Default `REQUIRED`. A facade call from another service **joins** the caller's transaction, and the inner method's `readOnly` flag is then ignored |
| Isolation | Database default: PostgreSQL `READ COMMITTED` |
| Rollback | Default rules: any `RuntimeException` (including `MeterFlowException`) marks the transaction rollback-only |
| Open Session in View | **Disabled** (`spring.jpa.open-in-view: false`). Lazy associations must be accessed inside the service |
| Flush timing | Normally at commit. `SubscriptionService.subscribe` calls `saveAndFlush` on purpose so that a unique-index violation is raised **inside** its `try/catch` |

Transactional boundaries per service method:

| Method | Annotation | Notes |
|---|---|---|
| `CustomerService.getCustomer` | `readOnly` | |
| `CustomerService.createCustomer` | read-write | |
| `CustomerService.getAll` | **none** | Relies on the repository's own transaction |
| `CatalogService.getPlan`, `getPlans`, `getPlanByCode`, `getAllApiProducts`, `getApiProduct`, `findEntitlement` | `readOnly` | |
| `CatalogService.createPlan`, `updatePlan`, `createApiProduct`, `updateApiProductStatus`, `updatePlanEntitlement` | read-write | Updates rely on dirty checking |
| `SubscriptionService.subscribe`, `cancelActiveSubscription` | read-write | |
| `SubscriptionService.getActiveSubscription` | read-write | Could be `readOnly` |
| `SubscriptionService.findActiveSubscription` | `readOnly` | Used by `SubscriptionFacade` (data plane) |
| `ApiKeyService.createApiKey`, `revokeApiKey` | read-write | |
| `ApiKeyService.listApiKeys` | `readOnly` | |
| `MeteringService.authorize` | `readOnly` | One transaction for the four authorization reads. The facade methods it calls join it (`REQUIRED`). **[Roadmap]** Becomes read-write in M4 when the debit is added |
| `ApiKeyAuthenticationProvider.authenticate` | **none** | `findByKeyHash` runs in Spring Data's default read-only repository transaction |

### Transaction propagation example: `subscribe`

```mermaid
sequenceDiagram
    participant C as SubscriptionController
    participant S as SubscriptionService.subscribe
    participant CF as CustomerFacade
    participant CS as CustomerService.getCustomer readOnly
    participant KF as CatalogFacade
    participant KS as CatalogService.getPlan readOnly
    participant DB as PostgreSQL

    C->>S: subscribe(customerId, planCode)
    Note over S: TX-1 BEGIN (read-write)
    S->>CF: getCustomer(id)
    CF->>CS: getCustomer(id)
    Note over CS: REQUIRED: joins TX-1, readOnly flag ignored
    CS->>DB: SELECT customer
    S->>KF: getPlan(code)
    KF->>KS: getPlan(code)
    Note over KS: joins TX-1
    KS->>DB: SELECT plan
    S->>DB: SELECT active subscription
    S->>DB: INSERT subscription (saveAndFlush)
    Note over S: TX-1 COMMIT, or ROLLBACK on any RuntimeException
```

## 8. Persistence Interaction

```mermaid
flowchart LR
    Svc["Service method"] -->|"save, findBy..."| Repo["Spring Data repository proxy"]
    Repo -->|"derived query"| EM["Hibernate Session / EntityManager<br/>first-level cache"]
    EM -->|"SQL via JDBC"| Pool["HikariCP"]
    Pool --> PG[("PostgreSQL")]
    EM -->|"at commit: dirty check, UPDATE ... WHERE id=? AND version=?"| Pool
    PG -->|"0 rows updated"| OLE["ObjectOptimisticLockingFailureException → 500"]
    PG -->|"constraint violation"| DIV["DataIntegrityViolationException"]
    DIV -->|"uq_subscription_active_per_customer"| C409["MeterFlowException SUBSCRIPTION_ALREADY_ACTIVE → 409"]
    DIV -->|"any other constraint"| C500["500 INTERNAL_ERROR"]
```

**Explanation:** All database access goes through Spring Data repositories. There is no native SQL, `@Query`, `JdbcTemplate` or Criteria API. Updates rely on Hibernate's dirty checking together with `@Version` optimistic locking. Constraint violations come back up as Spring's `DataIntegrityViolationException`, and only one of them is translated into a business error. Table-level details are in [Database Design](../database/database-design.md).

## 9. Background Jobs and Asynchronous Processing

**There is no background or asynchronous processing in the current code.** A search of `src/main` finds no `@Scheduled`, `@EnableScheduling`, `@Async`, `@EnableAsync`, `TaskExecutor`, message listeners, `ApplicationEvent` publishing or outbox tables.

The diagram below contrasts the current synchronous model with the asynchronous capabilities the roadmap expects. The roadmap part is **not implemented**.

```mermaid
flowchart TB
    subgraph now["Implemented: fully synchronous"]
        r1["HTTP request"] --> t1["Tomcat thread"] --> s1["Service + TX"] --> d1[("PostgreSQL")]
        d1 --> resp1["HTTP response"]
    end

    subgraph gaps["Domain transitions with no trigger today"]
        e1["Subscription.expire()<br/>no caller"]
        e2["Customer.suspend() / activate()<br/>no caller"]
        e3["current_period_end passes<br/>subscription stays ACTIVE"]
    end

    subgraph roadmap["Roadmap only, not implemented"]
        m5["M5: billing period rollover job"]
        m7["M7-M9: usage events"]
        ob["Transactional outbox table"]
        kf["Kafka"]
        m5 -.-> e1
        m7 -.-> ob -.-> kf
    end
```

**Consequences for operators:**

- A subscription whose `current_period_end` is in the past stays `ACTIVE`. Nothing expires or renews it.
- No endpoint suspends a customer. `CustomerStatus.SUSPENDED` can only be set directly in the database.

## 10. Logging and Observability

| Item | Current state |
|---|---|
| Application logging | Only in `GlobalExceptionHandler`: `WARN` for `MeterFlowException` and `ERROR` (with stack trace and `correlationId`) for unexpected exceptions |
| SQL logging | `spring.jpa.show-sql: true` in the `local` profile |
| Health | `/actuator/health` |
| Metrics, tracing | Not configured |
| Request correlation ID | Not present. The `correlationId` is generated only for `500` responses |
| Audit trail | Not present. Entities carry `created_at` / `updated_at`, and `api_key.revoked_at` |

## 11. Time Handling

- [`TimeConfig`](../../src/main/java/com/meterflow/config/TimeConfig.java) provides `Clock.systemUTC()`.
- All persisted timestamps are `Instant` mapped to `TIMESTAMPTZ`.
- Subscription period arithmetic is done in UTC: `now.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()`.
- The `Clock` can be replaced in tests (`Clock.fixed`), but the current tests use `Instant.now()` directly when they create fixtures.
