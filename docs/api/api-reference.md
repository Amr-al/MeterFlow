# API Reference

> Part of the [MeterFlow Technical Documentation](../README.md). This reference covers every HTTP endpoint in `src/main`. A runnable collection is in [`postman/`](../../postman/).

## Table of Contents

1. [Conventions](#1-conventions)
2. [Authentication](#2-authentication)
3. [Error Model](#3-error-model)
4. [Endpoint Summary](#4-endpoint-summary)
5. [Customers](#5-customers)
6. [API Products](#6-api-products)
7. [Plans](#7-plans)
8. [Plan Entitlements](#8-plan-entitlements)
9. [Subscriptions](#9-subscriptions)
10. [API Keys](#10-api-keys)
11. [Data Plane: Usage](#11-data-plane-usage)
12. [Operational Endpoints](#12-operational-endpoints)

---

## 1. Conventions

| Convention | Detail |
|---|---|
| Base URL | `http://localhost:8080` (default port) |
| Content type | `application/json` for requests and success responses. Errors use `application/problem+json` |
| Timestamps | ISO-8601 UTC `Instant`s, for example `"2026-11-06T10:00:00Z"`. **[Unverified exact fractional-second formatting]** |
| Codes | Product and plan codes are **case-insensitive** and trimmed. They are stored and returned in upper case |
| IDs | Numeric `Long` |
| Partial updates | `PATCH` bodies treat omitted or `null` fields as "unchanged" |

## 2. Authentication

| Path pattern | Requirement |
|---|---|
| `/v1/**` | Header `X-API-Key: mf_live_…` with an **ACTIVE** key. Otherwise `401` |
| All other paths | **None** (temporary, **[Roadmap]** M10) |

Example:

```http
POST /v1/usage HTTP/1.1
Host: localhost:8080
X-API-Key: mf_live_3Jx9aB7kQ2rT...
Content-Type: application/json

{"productCode":"SMS"}
```

The authentication flow is described in [Security](../technical-design/security.md#3-authentication-flow).

## 3. Error Model

### 3.1 ProblemDetail shape (all non-security errors)

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Plan already exists: STARTER",
  "instance": "/admin/plans",
  "errorCode": "DUPLICATE_RESOURCE",
  "timestamp": "2026-11-06T10:00:00Z"
}
```

`errorCode` and `timestamp` are always added by `GlobalExceptionHandler`. `type`, `title`, `status`, `detail` and `instance` follow Spring's standard `ProblemDetail` serialization. **[Unverified: whether `instance` is populated for every handler.]**

Validation errors on request bodies also include `fieldErrors`:

```json
{
  "status": 400,
  "detail": "Request validation failed",
  "errorCode": "VALIDATION_FAILED",
  "fieldErrors": { "requestsPerMinute": "Requests per minute must be greater than 0" },
  "timestamp": "2026-11-06T10:00:00Z"
}
```

Unexpected errors include a `correlationId` that matches the server log entry:

```json
{ "status": 500, "detail": "An unexpected error occurred", "errorCode": "INTERNAL_ERROR",
  "correlationId": "5b0c0d5e-…", "timestamp": "…" }
```

### 3.2 Business error codes ([`ErrorCode`](../../src/main/java/com/meterflow/common/error/ErrorCode.java))

| `errorCode` | HTTP | Raised when |
|---|---|---|
| `CUSTOMER_NOT_FOUND` | 404 | Customer ID does not exist |
| `PLAN_NOT_FOUND` | 404 | Plan code does not exist |
| `PRODUCT_NOT_FOUND` | 404 | Product code does not exist |
| `SUBSCRIPTION_NOT_FOUND` | 404 | Customer has no `ACTIVE` subscription |
| `API_KEY_NOT_FOUND` | 404 | Key does not exist **or belongs to another customer** |
| `DUPLICATE_RESOURCE` | 409 | Product or plan code already exists |
| `CONCURRENT_MODIFICATION` | 409 | *Defined but never raised* (optimistic-lock conflicts currently return 500) |
| `SUBSCRIPTION_ALREADY_ACTIVE` | 409 | Customer already has an `ACTIVE` subscription |
| `VALIDATION_FAILED` | 400 | Bean Validation failure, or a blank code or display name detected in the service |
| `INVALID_REQUEST` | 400 | Blank plan or product code on the entitlement upsert |
| `CUSTOMER_NOT_ACTIVE` | 400 | Subscribing a `SUSPENDED` customer |
| `PLAN_NOT_ACTIVE` | 400 | Subscribing to an inactive plan |
| `CUSTOMER_SUSPENDED` | 403 | Data plane: the authenticated customer is `SUSPENDED` |
| `PRODUCT_INACTIVE` | 403 | Data plane: the product exists but is not active |
| `NO_ACTIVE_SUBSCRIPTION` | 403 | Data plane: the customer has no `ACTIVE` subscription |
| `PRODUCT_NOT_ENTITLED` | 403 | Data plane: the subscription's plan has no entitlement for the product, or the entitlement has `allowed = false` |
| `INTERNAL_ERROR` | 500 | Any unhandled exception |

### 3.3 Framework error codes (from `handleExceptionInternal`)

| `errorCode` | HTTP | Typical cause |
|---|---|---|
| `MALFORMED_REQUEST` | 400 | Unreadable JSON, type mismatch |
| `RESOURCE_NOT_FOUND` | 404 | No handler for the URL |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method |
| `HTTP_<status>` | other | Any other Spring MVC exception |

### 3.4 Security errors

`401 Unauthorized` on `/v1/**` is produced by the security entry point with `response.sendError`. Its body is **Spring Boot's default error JSON**, not a `ProblemDetail`, and has no `errorCode`. **[Unverified exact body]**

## 4. Endpoint Summary

| # | Method | Path | Auth | Success |
|---|---|---|---|---|
| 1 | POST | `/customers` | none | 201 |
| 2 | GET | `/customers/{id}` | none | 200 |
| 3 | GET | `/customers` | none | 200 |
| 4 | POST | `/admin/api-products` | none | 201 |
| 5 | GET | `/admin/api-products?page={n}` | none | 200 |
| 6 | PATCH | `/admin/api-products/{code}` | none | 200 |
| 7 | POST | `/admin/plans` | none | 201 |
| 8 | GET | `/admin/plans` | none | 200 |
| 9 | GET | `/admin/plans/{code}` | none | 200 |
| 10 | PATCH | `/admin/plans/{code}` | none | 200 |
| 11 | PUT | `/admin/plans/{planCode}/entitlements/{productCode}` | none | 200 |
| 12 | POST | `/customers/{customerId}/subscriptions` | none | 201 |
| 13 | GET | `/customers/{customerId}/subscriptions/active` | none | 200 |
| 14 | PATCH | `/customers/{customerId}/subscriptions/active/cancel` | none | 200 |
| 15 | POST | `/admin/customers/{customerId}/api-keys` | none | 201 |
| 16 | GET | `/admin/customers/{customerId}/api-keys` | none | 200 |
| 17 | DELETE | `/admin/customers/{customerId}/api-keys/{apiKeyId}` | none | 200 |
| 18 | POST | `/v1/usage` | **API key** | 200 |
| — | GET | `/actuator/health`, `/actuator/info` | none | 200 |

> The Postman collection also contains `GET /admin/ping`. That endpoint is defined only in a **test** class (`AdminSecurityTestController`), so against a normally started application it returns 404.

---

## 5. Customers

Controller: [`CustomerController`](../../src/main/java/com/meterflow/tenancy/api/CustomerController.java)

### 5.1 `POST /customers`: create a customer

| Field | Type | Rules |
|---|---|---|
| `name` | string | required, not blank (`@NotBlank`). Stored as sent (not trimmed) |

```http
POST /customers
{"name": "Acme Corp"}
```

**201 Created**

```json
{ "id": 42, "name": "Acme Corp", "status": "ACTIVE" }
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | `name` missing or blank |

### 5.2 `GET /customers/{id}`

**200 OK**: `{ "id": 42, "name": "Acme Corp", "status": "ACTIVE" }`

| Error | When |
|---|---|
| 404 `CUSTOMER_NOT_FOUND` | Unknown ID |

### 5.3 `GET /customers`

Returns **all** customers, unpaginated. ⚠️ It serializes the JPA entity directly, so the response contains persistence fields:

```json
[ { "id": 42, "name": "Acme Corp", "status": "ACTIVE",
    "createdAt": "2026-11-06T10:00:00Z", "updatedAt": "2026-11-06T10:00:00Z", "version": 0 } ]
```

---

## 6. API Products

Controller: [`CatalogAdminController`](../../src/main/java/com/meterflow/catalog/api/CatalogAdminController.java)

### 6.1 `POST /admin/api-products`

| Field | Type | Rules |
|---|---|---|
| `code` | string | required, not blank. Trimmed and upper-cased |
| `displayName` | string | required, not blank. Trimmed |

```json
{ "code": "sms", "displayName": "SMS API" }
```

**201 Created**

```json
{ "id": 1, "code": "SMS", "displayName": "SMS API", "active": true, "createdAt": "2026-11-06T10:00:00Z" }
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | Blank `code` or `displayName` |
| 409 `DUPLICATE_RESOURCE` | Code already exists (after normalization) |

### 6.2 `GET /admin/api-products?page={n}`

| Param | Default | Rules |
|---|---|---|
| `page` | `0` | `@Min(0)`. The page size is fixed at **10** |

**200 OK**: a serialized Spring Data `Page`. `content` and `totalElements` are verified by tests. The other paging fields follow Spring Data's `PageImpl` serialization **[Unverified exact field set]**.

```json
{
  "content": [
    { "id": 1, "code": "SMS", "displayName": "SMS API", "active": true, "createdAt": "…" }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | `page < 0` |

### 6.3 `PATCH /admin/api-products/{code}`: activate or deactivate

| Field | Type | Rules |
|---|---|---|
| `active` | boolean | **required** (`@NotNull`) |

```json
{ "active": false }
```

**200 OK**: `ApiProductResponse` with the updated `active`.

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | `active` missing |
| 404 `PRODUCT_NOT_FOUND` | Unknown code |

---

## 7. Plans

### 7.1 `POST /admin/plans`

| Field | Type | Rules |
|---|---|---|
| `code` | string | required, not blank. Normalized to upper case |
| `displayName` | string | required, not blank. Trimmed |
| `requestsPerMinute` | integer | required, > 0 |
| `monthlyQuota` | long | required, > 0 |

```json
{ "code": "starter", "displayName": "Starter Plan", "requestsPerMinute": 100, "monthlyQuota": 100000 }
```

**201 Created**

```json
{
  "id": 3, "code": "STARTER", "displayName": "Starter Plan",
  "requestsPerMinute": 100, "monthlyQuota": 100000, "active": true,
  "createdAt": "2026-11-06T10:00:00Z", "updatedAt": "2026-11-06T10:00:00Z"
}
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | Missing, blank or non-positive fields. `fieldErrors` lists each offending field |
| 409 `DUPLICATE_RESOURCE` | Code exists |

### 7.2 `GET /admin/plans`

**200 OK**: an array of `PlanResponse` (unpaginated).

### 7.3 `GET /admin/plans/{code}`

The code is case-insensitive. **200 OK**: `PlanResponse`.

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | Code is blank (whitespace only) |
| 404 `PLAN_NOT_FOUND` | Unknown code |

### 7.4 `PATCH /admin/plans/{code}`: partial update

All fields are optional. `null` or omitted means "unchanged".

| Field | Type | Rules |
|---|---|---|
| `displayName` | string | If present, it must not be blank after trimming (otherwise 400 `VALIDATION_FAILED`) |
| `requestsPerMinute` | integer | If present, > 0 |
| `monthlyQuota` | long | If present, > 0 |
| `active` | boolean | `true` activates, `false` deactivates |

```json
{ "requestsPerMinute": 120 }
```

**200 OK**: the updated `PlanResponse` (`updatedAt` refreshed).

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | Non-positive limits, blank `displayName` |
| 404 `PLAN_NOT_FOUND` | Unknown code |
| 500 `INTERNAL_ERROR` | Concurrent modification (optimistic-lock conflict) |

---

## 8. Plan Entitlements

### 8.1 `PUT /admin/plans/{planCode}/entitlements/{productCode}`: upsert

Both codes are case-insensitive. The call creates the entitlement if it is missing, otherwise it updates it.

| Field | Type | Rules |
|---|---|---|
| `allowed` | boolean | required |
| `creditCost` | long | required, ≥ 0. **[Roadmap]** milli-credits |

```json
{ "allowed": true, "creditCost": 1000 }
```

**200 OK** (for both create and update)

```json
{ "id": 9, "planCode": "STARTER", "productCode": "SMS", "allowed": true, "creditCost": 1000 }
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | `allowed` or `creditCost` missing, or `creditCost < 0` |
| 400 `INVALID_REQUEST` | A path code is blank |
| 404 `PLAN_NOT_FOUND` / `PRODUCT_NOT_FOUND` | Creating, and the plan or product does not exist |
| 500 `INTERNAL_ERROR` | Two concurrent first-time upserts for the same pair |

---

## 9. Subscriptions

Controller: [`SubscriptionController`](../../src/main/java/com/meterflow/subscription/api/SubscriptionController.java)

### 9.1 `POST /customers/{customerId}/subscriptions`

| Field | Type | Rules |
|---|---|---|
| `planCode` | string | required, not blank. Case-insensitive |

```json
{ "planCode": "starter" }
```

**201 Created**

```json
{
  "id": 15, "customerId": 42, "planId": 3, "status": "ACTIVE",
  "startedAt": "2026-11-06T10:00:00Z",
  "currentPeriodStart": "2026-11-06T10:00:00Z",
  "currentPeriodEnd": "2026-12-06T10:00:00Z"
}
```

| Error | When |
|---|---|
| 400 `VALIDATION_FAILED` | `planCode` blank |
| 404 `CUSTOMER_NOT_FOUND` | Unknown customer |
| 400 `CUSTOMER_NOT_ACTIVE` | Customer is `SUSPENDED` |
| 404 `PLAN_NOT_FOUND` | Unknown plan |
| 400 `PLAN_NOT_ACTIVE` | Plan is inactive |
| 409 `SUBSCRIPTION_ALREADY_ACTIVE` | An `ACTIVE` subscription exists (also under concurrency) |

### 9.2 `GET /customers/{customerId}/subscriptions/active`

**200 OK**: `SubscriptionResponse`.

| Error | When |
|---|---|
| 404 `SUBSCRIPTION_NOT_FOUND` | No active subscription. This is also returned for an unknown customer |

### 9.3 `PATCH /customers/{customerId}/subscriptions/active/cancel`

No body. **200 OK**: `SubscriptionResponse` with `"status": "CANCELLED"`. The period fields are unchanged.

| Error | When |
|---|---|
| 404 `SUBSCRIPTION_NOT_FOUND` | No active subscription |

---

## 10. API Keys

Controller: [`ApiKeyAdminController`](../../src/main/java/com/meterflow/credential/api/ApiKeyAdminController.java)

### 10.1 `POST /admin/customers/{customerId}/api-keys`

No request body.

**201 Created**: ⚠️ the **only** time the raw key is returned. Store it securely.

```json
{
  "id": 7,
  "apiKey": "mf_live_3Jx9aB7kQ2rT…(51 chars total)",
  "prefix": "mf_live_3Jx9aB7k",
  "createdAt": "2026-11-06T10:00:00Z"
}
```

| Error | When |
|---|---|
| 404 `CUSTOMER_NOT_FOUND` | Unknown customer. The customer's status is not checked |

### 10.2 `GET /admin/customers/{customerId}/api-keys`

**200 OK**: every key of the customer, active and revoked. Raw keys and hashes are never included.

```json
[
  { "id": 7, "prefix": "mf_live_3Jx9aB7k", "status": "ACTIVE", "createdAt": "…", "revokedAt": null }
]
```

| Error | When |
|---|---|
| 404 `CUSTOMER_NOT_FOUND` | Unknown customer |

### 10.3 `DELETE /admin/customers/{customerId}/api-keys/{apiKeyId}`: revoke

A soft revoke. It is **idempotent**: a second call returns the same body with the original `revokedAt`.

**200 OK**

```json
{ "id": 7, "prefix": "mf_live_3Jx9aB7k", "status": "REVOKED", "createdAt": "…", "revokedAt": "2026-11-06T11:00:00Z" }
```

| Error | When |
|---|---|
| 404 `CUSTOMER_NOT_FOUND` | Unknown customer |
| 404 `API_KEY_NOT_FOUND` | Unknown key **or** the key belongs to a different customer |

---

## 11. Data Plane: Usage

Controller: [`MeteringController`](../../src/main/java/com/meterflow/gateway/api/MeteringController.java). Implemented in M3. The endpoint **authorizes** a call and returns its credit cost. It does **not** debit a wallet, store a usage record, deduplicate retries, enforce quota or rate-limit (see [Business Logic § 5.4](../technical-design/business-logic.md#54-implemented-versus-planned)).

### 11.1 `POST /v1/usage`

| Header | Required |
|---|---|
| `X-API-Key` | yes |

| Field | Type | Rules |
|---|---|---|
| `productCode` | string | required, not blank. Case-insensitive and trimmed for the lookup |

```http
POST /v1/usage
X-API-Key: mf_live_3Jx9aB7k…
Content-Type: application/json

{"productCode": "sms"}
```

**200 OK**

```json
{
  "customerId": 42,
  "productCode": "SMS",
  "subscriptionId": 12,
  "planId": 3,
  "creditCost": 1500
}
```

| Field | Type | Meaning |
|---|---|---|
| `customerId` | number | From the authenticated key, never from the request |
| `productCode` | string | The product's **canonical stored code**, not the value as sent (`"sms"` → `"SMS"`) |
| `subscriptionId` | number | The `ACTIVE` subscription the call was authorized against |
| `planId` | number | The plan of that subscription |
| `creditCost` | integer (`long`) | Cost of one call from the plan entitlement, in **milli-credits** (1 credit = 1,000). Never fractional |

The internal product ID is deliberately not part of the response.

**Errors**, in the order the checks run:

| Status | `errorCode` | When |
|---|---|---|
| 401 (default error body) | — | Header missing or blank, key unknown, or key revoked |
| 400 | `VALIDATION_FAILED` | `productCode` blank |
| 403 | `CUSTOMER_SUSPENDED` | The key's customer is `SUSPENDED` |
| 404 | `PRODUCT_NOT_FOUND` | No product with that code |
| 403 | `PRODUCT_INACTIVE` | The product is deactivated |
| 403 | `NO_ACTIVE_SUBSCRIPTION` | The customer has no `ACTIVE` subscription (none, or only `CANCELLED`/`EXPIRED`) |
| 403 | `PRODUCT_NOT_ENTITLED` | The plan has no entitlement for the product, or it has `allowed = false` |

Notes:

- A **deactivated plan does not block** existing subscribers. Only the subscription status is checked.
- `currentPeriodEnd` is **not** checked yet (**[Roadmap]** M5).
- Retrying the same call authorizes it again; there is no idempotency key yet (**[Roadmap]** M6).

---

## 12. Operational Endpoints

| Endpoint | Response |
|---|---|
| `GET /actuator/health` | `{"status":"UP"}` when the application and DB are healthy |
| `GET /actuator/info` | Application info (empty by default) |
