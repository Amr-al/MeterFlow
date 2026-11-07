# MeterFlow — Technical Documentation

MeterFlow is a backend platform for **metering and monetizing APIs**. Operators use it to:

- define API products and subscription plans,
- grant plans access to products, with a per-call credit cost,
- onboard customers and subscribe them to a plan,
- issue API keys,
- authenticate customer traffic on a data-plane endpoint that will meter usage.

The service is a single **Spring Boot 4 modular monolith** backed by **PostgreSQL**.

> **Documentation status.** This documentation describes the code on branch `feature/m3-metering-flow`, including uncommitted working-tree changes, as of 2026-11-06. Statements are taken from the source code. Anything that could not be verified from the code is marked **[Unverified]**, and anything taken from the project roadmap rather than the code is marked **[Roadmap]**.

---

## Table of Contents

1. [Main Features](#main-features)
2. [Project Status and Roadmap](#project-status-and-roadmap)
3. [Technology Stack](#technology-stack)
4. [Prerequisites](#prerequisites)
5. [Setup and Installation](#setup-and-installation)
6. [Running the Project](#running-the-project)
7. [Running the Tests](#running-the-tests)
8. [Documentation Map](#documentation-map)
9. [Glossary](#glossary)

---

## Main Features

| Area | Capability | Module |
|---|---|---|
| Tenancy | Create and look up customers (`ACTIVE` / `SUSPENDED`) | `com.meterflow.tenancy` |
| Catalog | Create and list API products, activate or deactivate them, paginated (10 per page) | `com.meterflow.catalog` |
| Catalog | Create, list, read and partially update plans (rate per minute, monthly quota, active flag) | `com.meterflow.catalog` |
| Catalog | Upsert plan↔product entitlements (`allowed`, `creditCost`) | `com.meterflow.catalog` |
| Subscription | Subscribe a customer to a plan, with **at most one active subscription per customer**, enforced in the database | `com.meterflow.subscription` |
| Subscription | Read or cancel the active subscription | `com.meterflow.subscription` |
| Credentials | Issue API keys. The raw key is shown once and only a SHA-256 hash is stored. Keys can be listed and revoked | `com.meterflow.credential` |
| Security | Stateless API-key authentication (`X-API-Key`) for `/v1/**` | `com.meterflow.security` |
| Data plane | `POST /v1/usage`: resolves the customer from the API key and validates that the product exists (**metering is still in progress**) | `com.meterflow.gateway` |
| Errors | Uniform RFC 7807 `ProblemDetail` responses with a machine-readable `errorCode` | `com.meterflow.common.error` |
| Ops | Actuator `health` and `info` endpoints | Spring Boot Actuator |

## Project Status and Roadmap

**[Roadmap]** This table is based on the project milestones document.

| Milestone | Name | Status |
|---|---|---|
| M0 | Foundation (Boot, PostgreSQL, Flyway, profiles, error handling, integration tests, `Clock`) | Completed |
| M1 | Tenancy & Catalog (customers, products, plans, entitlements, subscriptions) | Completed |
| M2 | API Keys & Security | Implemented |
| **M3** | **Metering Flow**: usage API, customer resolution, product, subscription and entitlement validation, credit-cost resolution | **In progress** |
| M4 | Wallet & Ledger (atomic debit, ledger entries) | Planned |
| M5 | Monthly Quota (per-customer billing periods) | Planned |
| M6 | Idempotency keys | Planned |
| M7–M9 | Rate limiting, usage events, transactional outbox, Kafka (provisional) | To be confirmed |
| M10 | User identity & admin security (`app_user`) | Future |

**What M3 has today:** the usage endpoint, customer resolution from the API key, and a check that the product **exists**.

**What M3 still needs:** checks for product `active`, customer `ACTIVE`, an active subscription and an allowed entitlement, plus credit-cost resolution. See [Business Logic § Metering](technical-design/business-logic.md#5-metering-gateway--m3-in-progress).

## Technology Stack

| Technology | Version / Source | Purpose in MeterFlow |
|---|---|---|
| Java | 17 (`<java.version>17</java.version>`) | Language. Uses records and pattern matching for `instanceof` |
| Spring Boot | 4.1.1 (parent POM) | Application framework and auto-configuration |
| Spring Web MVC | `spring-boot-starter-webmvc` | REST controllers on the Servlet stack (embedded Tomcat) |
| Spring Security | `spring-boot-starter-security` | Two `SecurityFilterChain`s and custom API-key authentication |
| Spring Data JPA / Hibernate | `spring-boot-starter-data-jpa` | Entities and derived-query repositories |
| PostgreSQL JDBC driver | `org.postgresql:postgresql` (runtime) | Database connectivity |
| Flyway | `flyway-core`, `flyway-database-postgresql`, `spring-boot-flyway` | Versioned schema migrations (`V1`–`V6`) |
| Bean Validation | `spring-boot-starter-validation` | Request validation (`@NotBlank`, `@Positive`, …) |
| Spring Boot Actuator | `spring-boot-starter-actuator` | `/actuator/health`, `/actuator/info` |
| Lombok | optional | `@Getter` on `ApiKey`, `@Slf4j` on `GlobalExceptionHandler` |
| Kotlin stdlib + `kotlin-maven-plugin` 2.4.0 | declared in `pom.xml` | **Not used.** The project has no `.kt` sources. These are leftovers from the initial generator |
| JUnit 5, MockMvc | `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` | Integration tests against a real PostgreSQL database |
| Maven Wrapper | `mvnw`, `.mvn/` | Build tool |

The full rationale for each technology is in [System Overview § Technology Stack](architecture/system-overview.md#5-technology-stack).

## Prerequisites

| Requirement | Notes |
|---|---|
| JDK 17 | `scripts/dev-up.sh` defaults `JAVA_HOME` to `/usr/lib/jvm/java-17-openjdk-amd64` |
| PostgreSQL | Must run on `localhost:5432` (host and port can be overridden for the script's readiness check only) |
| PostgreSQL client tools | `pg_isready` and `psql` are used by `scripts/dev-up.sh` |
| Database role | `meterflow` |
| Databases | `meterflow` (local profile) and `meterflow_test` (test profile) |
| Environment variable | `DB_PASSWORD`: the password of the `meterflow` role |

## Setup and Installation

```bash
# 1. Create the role and databases (one-time, as a PostgreSQL superuser)
psql -U postgres -c "CREATE ROLE meterflow LOGIN PASSWORD '<password>';"
createdb -U postgres -O meterflow meterflow
createdb -U postgres -O meterflow meterflow_test

# 2. Export the password used by both Spring profiles
export DB_PASSWORD='<password>'

# 3. Build (runs the tests against meterflow_test)
./mvnw clean verify
```

You don't create tables by hand. **Flyway** applies `src/main/resources/db/migration/V*.sql` automatically on startup, and Hibernate then validates the entity mappings against that schema (`ddl-auto: validate`).

## Running the Project

### Option A: helper script (recommended for local development)

```bash
export DB_PASSWORD='<password>'
./scripts/dev-up.sh
```

[`scripts/dev-up.sh`](../scripts/dev-up.sh) does the following:

1. Fails fast if `DB_PASSWORD` is not set.
2. Checks PostgreSQL with `pg_isready`.
3. Verifies that both `meterflow` and `meterflow_test` exist.
4. Runs `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.

### Option B: Maven directly

```bash
DB_PASSWORD='<password>' ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> A Spring profile is **required**. The base [`application.yml`](../src/main/resources/application.yml) has no datasource, so without the `local` or `test` profile the application fails to start.

### Verify that it runs

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Explore the API

Import [`postman/MeterFlow.postman_collection.json`](../postman/MeterFlow.postman_collection.json) and follow [`postman/README.md`](../postman/README.md). All endpoints are documented in the [API Reference](api/api-reference.md).

## Running the Tests

```bash
DB_PASSWORD='<password>' ./mvnw test
```

- Tests use the `test` profile and the **real** `meterflow_test` database. There are no Testcontainers and no H2.
- The base class [`IntegrationTest`](../src/test/java/com/meterflow/support/IntegrationTest.java) is `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`.
- Each test class cleans its own tables with `deleteAllInBatch()`. Flyway `clean` is disabled.
- The last recorded run (`target/surefire-reports`) shows 70 tests with 0 failures. Test classes cover the catalog, plans, API keys, subscriptions (including a concurrency test), security and metering.

## Documentation Map

| Document | Contents |
|---|---|
| **Architecture** | |
| [architecture/system-overview.md](architecture/system-overview.md) | Business context, high-level architecture, patterns, technology rationale |
| [architecture/component-design.md](architecture/component-design.md) | Modules, classes, responsibilities, dependency rules |
| [architecture/deployment-architecture.md](architecture/deployment-architecture.md) | Runtime topology, environments, configuration, startup |
| **Technical Design** | |
| [technical-design/application-flow.md](technical-design/application-flow.md) | Request lifecycle, filter chains, validation, error handling, transactions |
| [technical-design/business-logic.md](technical-design/business-logic.md) | Business rules for each module, with method-level walkthroughs |
| [technical-design/security.md](technical-design/security.md) | API-key design, authentication flow, threat analysis |
| [technical-design/performance.md](technical-design/performance.md) | Concurrency, caching, bottlenecks, scalability |
| **Database** | |
| [database/database-design.md](database/database-design.md) | Tables, constraints, indexes, migrations, queries |
| [database/er-diagram.md](database/er-diagram.md) | ER diagram and relationship explanation |
| **API** | |
| [api/api-reference.md](api/api-reference.md) | All endpoints, with examples, validation rules and errors |
| **Workflows** | |
| [workflows/workflow-overview.md](workflows/workflow-overview.md) | Actors and step-by-step business processes |
| [workflows/sequence-diagrams.md](workflows/sequence-diagrams.md) | Sequence diagrams for every major workflow |
| **Diagram sources** | |
| [diagrams/](diagrams/) | Standalone `.mmd` Mermaid sources |

## Glossary

| Term | Meaning |
|---|---|
| **API Product** | A sellable API identified by a unique upper-case `code` (for example `SMS`) |
| **Plan** | A subscription tier with `requestsPerMinute` and `monthlyQuota` limits |
| **Entitlement** | A plan↔product link with an `allowed` flag and a `creditCost` |
| **Subscription** | A customer's enrolment in a plan for a billing period. At most one is `ACTIVE` per customer |
| **API Key** | A credential with the format `mf_live_<43 base64url chars>`. Only its SHA-256 hash is stored |
| **Control plane** | Admin and management endpoints (`/admin/**`, `/customers/**`). They are currently unauthenticated |
| **Data plane** | Customer traffic endpoints (`/v1/**`), authenticated with an API key |
| **Milli-credit** | **[Roadmap]** Credit unit: 1 credit = 1,000 milli-credits, stored as `BIGINT` |
