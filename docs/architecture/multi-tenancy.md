# Multi-Tenancy Architecture

> **Status**: Living document — update when adding tenant-scoped features.

## Table of Contents

1. [Overview](#overview)
2. [Entity Scoping Model](#entity-scoping-model)
3. [Request Isolation Pipeline](#request-isolation-pipeline)
4. [Tenant Lifecycle](#tenant-lifecycle)
5. [Per-Resource Isolation](#per-resource-isolation)
6. [Dual-Scope Pattern (Settings, Users, Roles, Groups)](#dual-scope-pattern)
7. [SSO / Identity Provider Integration](#sso--identity-provider-integration)
8. [Configuration Reference](#configuration-reference)
9. [Testing Tenant Isolation](#testing-tenant-isolation)
10. [Anti-Patterns](#anti-patterns)

---

## Overview

OpenAEV supports **multi-tenancy**: a single deployment can host multiple isolated tenants.
Each tenant has its own data, users, roles, RabbitMQ queues, and file storage prefix.
Data isolation is enforced at every layer — HTTP, JPA, and storage — so one tenant can never
read or modify another tenant's data.

**Key design decisions:**
- Isolation is enforced by a **Hibernate `tenantFilter`** activated on every `@Transactional`
  method via an AOP aspect — no application code needs to filter manually.
- The tenant context is carried in a **`ThreadLocal`** (`TenantContext`) and is set/cleared
  by a Spring `HandlerInterceptor` on every HTTP request.
- Tenant membership is validated on every request and **cached** (5-minute TTL) to avoid
  hitting the database on every call.
- The tenant REST API (`POST /api/tenants`) is an **Enterprise Edition** feature.
  Community Edition deployments run as a single implicit tenant.

---

## Entity Scoping Model

Every JPA entity falls into exactly one of three scoping categories:

| Category | Interface | `tenant_id` | Unique constraint | Listener |
|---|---|---|---|---|
| **Tenant-scoped** | `TenantBase` | `NOT NULL` | Composite `(field, tenant_id)` | `TenantBaseListener` + `ModelBaseListener` |
| **Platform-level** | `Base` (no tenant) | absent | Simple unique | `ModelBaseListener` |
| **Dual-scope** | `DualScopeBase` | `NULLABLE` | Partial unique indexes | `ModelBaseListener` |

### Tenant-scoped entities (`TenantBase`)

```
TenantBase (interface)
  └── getTenant(): @Nonnull Tenant
  └── setTenant(@Nonnull Tenant)
```

All tenant-scoped entities **must**:
- Implement `TenantBase`
- Carry `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`
- Use `@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})`
- Define composite unique constraints: `UNIQUE (field, tenant_id)` — never `@Column(unique = true)`

**Examples of tenant-scoped entities:**
`Exercise`, `Scenario`, `Inject`, `Team`, `Asset`, `AssetGroup`, `Agent`, `Finding`,
`Tag`, `TagRule`, `Payload`, `Domain`, `TenantXtmHubRegistration`, `Collector`, `Executor`

### Platform-level entities (`Base`)

Entities shared across all tenants, not filtered by `tenant_id`.

**Examples:** `AttackPattern`, `Mitigation`, `Cwe`, `DataPack`, `InjectorContract`

### Dual-scope entities (`DualScopeBase`)

```
DualScopeBase (interface)
  └── getTenant(): @Nullable Tenant
  └── setTenant(@Nullable Tenant)
```

When `tenant_id IS NULL` → platform-level resource (visible to all tenants).
When `tenant_id IS NOT NULL` → tenant-scoped resource (visible only to that tenant).

**Dual-scope entities:** `User`, `Role`, `Group`, `Settings`

Each dual-scope entity requires:
- Two services: `PlatformXxxService` (uses `*TenantIsNull` queries) and `TenantXxxService`
  (uses `*TenantId` queries)
- Two API prefixes: `/api/platform-{entities}` and `TENANT_PREFIX + "/{entities}"`

---

## Request Isolation Pipeline

Every tenant-scoped HTTP request goes through a strict 3-step pipeline:

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────────┐
│  TenantInterceptor  (HandlerInterceptor)      │
│  1. Extract {tenantId} from path variable     │
│     /api/tenants/{tenantId}/**                │
│  2. Validate user belongs to tenant           │
│     (via TenantMembershipCacheManager)        │
│  3. Set TenantContext.setCurrentTenant()      │
│  4. afterCompletion: clearCurrentTenant()     │
└──────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────┐
│  HibernateFilterTransactionAspect  (AOP)      │
│  @Before every @Transactional method          │
│  Reads TenantContext.getCurrentTenant()       │
│  Enables Hibernate "tenantFilter"             │
│  → scopes all JPQL/Criteria queries           │
│  ⚠️  Does NOT apply to native @Query          │
└──────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────┐
│  JPA / Hibernate                              │
│  tenantFilter: WHERE tenant_id = :tenantId   │
│  Applied to all @Filter-annotated entities    │
└──────────────────────────────────────────────┘
```

### URL structure

All tenant-scoped endpoints share a common prefix:

```java
// TenantUriUtils.java
TENANT_PREFIX = "/api/tenants/{tenantId}"
```

Examples:
- `GET  /api/tenants/{tenantId}/scenarios`
- `POST /api/tenants/{tenantId}/exercises`
- `GET  /api/tenants/{tenantId}/assets`

Platform-level endpoints (no tenant context):
- `GET  /api/platform-groups`
- `GET  /api/platform-users`

### Tenant membership cache

`TenantMembershipCacheManager` wraps the DB membership check with a Spring `@Cacheable`
(cache name: `tenantMembership`, key: `userId:tenantId`).

The cache is **explicitly evicted** (not TTL-only) when users are added to or removed from
tenants, so membership changes take effect immediately.

---

## Tenant Lifecycle

### Creation

`TenantService.create()` orchestrates tenant creation in a single transaction:

```
1. Persist Tenant row
2. Switch TenantContext to new tenant ID
3. Run all DependenciesManager beans (ordered by prerequisites):
   ├── DomainService         → creates default domain
   ├── TenantQueueService    → declares RabbitMQ queues
   ├── MinioService          → logs file path prefix (tenantId/)
   └── BuiltinIntegrationFactory impls
       ├── OpenAEVAgentFactory   → registers OpenAEV Agent connector
       └── OpenAEVImplantFactory → registers OpenAEV Implant connector
       └── ... (any BuiltinTenantRegistrable bean)
```

Adding a new dependency to tenant creation requires only implementing `DependenciesManager`
on a `@Service` bean — no manual wiring in `TenantService`.

### Soft-delete & reactivation

Tenants are **soft-deleted** (grace period: 30 days):

```
softDelete()   → sets deletedAt = now()
reactivate()   → clears deletedAt (only within 30-day window)
purgeExpired() → runs DependenciesManager.deleteDependencyForTenant()
                 then hard-deletes the row
```

### Startup initialization

At startup, `TenantQueueService.init()` (via `@PostConstruct`) declares RabbitMQ queues
for every active tenant, so queues are ready before the first message arrives.

---

## Per-Resource Isolation

### RabbitMQ queues (`TenantQueueService`)

Each tenant gets its own set of queues. Queue name pattern:

```
{prefix}-{tenantId}_execution_{queueName}
```

Queues are declared at:
- **Startup** — for all existing tenants
- **Tenant creation** — for the new tenant
- **Tenant purge** — queues are deleted

Configured queues (from `application.properties`):

| Queue key | Queue name | Purpose |
|---|---|---|
| `inject-trace` | `inject-trace` | Inject execution traces |
| `workflows-ready` | `workflows-ready` | Workflow engine ready events |
| `workflows-update` | `workflows-update` | Workflow engine update events |

### File storage (`MinioService`)

All files are stored under a **tenant-prefixed path**:

```
{tenantId}/{fileName}
```

`MinioService.uploadFileInTenantPath()` automatically prefixes the path using
`TenantContext.getCurrentTenant()`. On tenant purge, all objects under `{tenantId}/`
are batch-deleted (1000 objects per S3 request).

### Built-in connectors (`BuiltinIntegrationFactory`)

Each tenant gets its own registered instances of built-in connectors (injectors, executors).
Any `@Service` extending `BuiltinIntegrationFactory` (which implements `BuiltinTenantRegistrable`)
is auto-discovered and called once per tenant creation via `TenantRegistrationExecutor`.

`TenantRegistrationExecutor` provides two entry points:
- `registerForTenantIsolated()` — startup path: switches context, registers, restores
- `registerForTenant()` — tenant creation path: caller manages context

### XTM Hub registration (`TenantXtmHubRegistration`)

Each tenant can independently register with XTM Hub. The registration entity is
tenant-scoped (`TenantBase` + `@Filter("tenantFilter")`), so each tenant's registration
token and connectivity status are fully isolated.

---

## Dual-Scope Pattern

Entities that exist at both platform level and tenant level follow the dual-scope pattern.

### Current dual-scope entities

| Entity | Platform service | Tenant service | Platform API | Tenant API |
|---|---|---|---|---|
| `User` | `UserService` | `TenantUserService` | `/api/users` | `TENANT_PREFIX/users` |
| `Role` | *(platform)* | `TenantRoleService` | `/api/platform-roles` | `TENANT_PREFIX/roles` |
| `Group` | *(platform)* | `TenantGroupService` | `/api/platform-groups` | `TENANT_PREFIX/groups` |

### Rules

- Platform service methods use `*TenantIsNull()` repository queries and **never** receive a `tenantId`
- Tenant service methods use `*TenantId()` repository queries and **always** receive a `tenantId`
- `tenant_id` is **never** returned in API responses — `@JsonIgnore` on the tenant relation
- Unique constraints use **partial indexes**: unique on `field` where `tenant_id IS NULL`
  (platform), and `UNIQUE (field, tenant_id)` for tenant-scoped rows

---

## SSO / Identity Provider Integration

OpenAEV supports mapping SSO identity provider attributes to tenants.

### Tenant mapping via SSO (`openaev.provider.{registrationId}.tenant_id`)

When an SSO provider is configured, the `tenant_id` attribute can be used to automatically
assign users to a tenant at login:

```properties
# application.properties — SSO / OAuth2 / SAML2 provider config
# Automatically assigns authenticated users to a tenant based on IdP attribute
openaev.provider.{registrationId}.tenant_id=<idp-tenant-attribute-name>

# Roles and groups management via SSO
openaev.provider.{registrationId}.roles_path=<path-to-roles-in-token>
openaev.provider.{registrationId}.roles_admin=<admin-role-value>
openaev.provider.{registrationId}.groups_management=<true|false>
openaev.provider.{registrationId}.audience=<expected-audience>
```

---

## Configuration Reference

All multi-tenancy-related configuration properties from `application.properties`:

### Core application

| Property | Default | Description |
|---|---|---|
| `openaev.base-url` | `http://localhost:8080` | Base URL used for redirect URIs and SSO callbacks |
| `openaev.cookie-name` | `openaev_token` | Session cookie name |
| `openaev.cookie-secure` | `false` | Set `true` in production (HTTPS) |
| `openaev.cookie-duration` | `P1D` | Session duration (ISO 8601 duration) |

### Authentication

| Property | Default | Description |
|---|---|---|
| `openaev.auth-local-enable` | `true` | Enable local username/password auth |
| `openaev.auth-openid-enable` | `false` | Enable OpenID Connect / OAuth2 |
| `openaev.auth-saml2-enable` | `false` | Enable SAML2 |
| `openaev.auth-kerberos-enable` | `false` | Enable Kerberos |

### SSO provider (per-provider, replace `{registrationId}`)

| Property | Description |
|---|---|
| `spring.security.oauth2.client.provider.{id}.issuer-uri` | OIDC issuer URI |
| `spring.security.oauth2.client.registration.{id}.client-id` | OAuth2 client ID |
| `spring.security.oauth2.client.registration.{id}.client-secret` | OAuth2 client secret |
| `spring.security.oauth2.client.registration.{id}.redirect-uri` | Callback URL (use `${openaev.base-url}/login/oauth2/code/{id}`) |
| `spring.security.oauth2.client.registration.{id}.scope` | Scopes (e.g. `openid,profile,email`) |
| `spring.security.saml2.relyingparty.registration.{id}.entity-id` | SAML2 entity ID |
| `spring.security.saml2.relyingparty.registration.{id}.assertingparty.metadata-uri` | IdP metadata URI |
| `openaev.provider.{id}.firstname_attribute_key` | SAML attribute for first name |
| `openaev.provider.{id}.lastname_attribute_key` | SAML attribute for last name |
| `openaev.provider.{id}.roles_path` | Path to roles in token/assertion |
| `openaev.provider.{id}.roles_admin` | Role value that grants admin |
| `openaev.provider.{id}.audience` | Expected token audience |
| `openaev.provider.{id}.groups_management` | Enable group sync from IdP |
| `openaev.provider.{id}.tenant_id` | IdP attribute to use for tenant assignment |

### RabbitMQ (per-queue, replace `{queueKey}`)

| Property | Default | Description |
|---|---|---|
| `openaev.rabbitmq.hostname` | `localhost` | RabbitMQ host |
| `openaev.rabbitmq.port` | `5672` | AMQP port |
| `openaev.rabbitmq.prefix` | `openaev` | Queue name prefix |
| `openaev.rabbitmq.user` | `guest` | Username |
| `openaev.rabbitmq.pass` | `guest` | Password |
| `openaev.rabbitmq.vhost` | `/` | Virtual host |
| `openaev.rabbitmq.ssl` | `false` | Enable TLS |
| `openaev.rabbitmq.management-port` | `15672` | Management plugin port |
| `openaev.rabbitmq.queue-type` | `classic` | `classic` or `quorum` |
| `openaev.rabbitmq.management-insecure` | `true` | Allow insecure management API |
| `openaev.queue-config.{queueKey}.publisher-number` | — | Publisher thread count |
| `openaev.queue-config.{queueKey}.consumer-number` | — | Consumer thread count |
| `openaev.queue-config.{queueKey}.worker-number` | — | Worker thread count |
| `openaev.queue-config.{queueKey}.queue-name` | — | Base queue name |
| `openaev.queue-config.{queueKey}.max-size` | — | Max queue size |
| `openaev.queue-config.{queueKey}.consumer-qos` | — | Consumer QoS prefetch |

### MinIO / S3

| Property | Default | Description |
|---|---|---|
| `minio.endpoint` | `localhost` | MinIO hostname |
| `minio.port` | `9000` | MinIO port |
| `minio.bucket` | `openaev` | Bucket name (shared; isolation via path prefix) |
| `minio.access-key` | — | S3 access key |
| `minio.access-secret` | — | S3 secret key |
| `openaev.s3.use-aws-role` | `false` | Use AWS IAM role instead of key/secret |
| `openaev.s3.sts-endpoint` | — | STS endpoint for role assumption |

### XTM Hub

| Property | Default | Description |
|---|---|---|
| `openaev.xtm.hub.enable` | `true` | Enable XTM Hub integration |
| `openaev.xtm.hub.url` | `https://hub.filigran.io` | Hub URL |
| `openaev.xtm.hub.connectivity-email-enable` | `true` | Send connectivity alert emails |
| `openaev.xtm.hub.override-api-url` | — | Override Hub API URL (optional) |
| `openaev.xtm.hub.collector.enable` | `false` | Enable Hub collector |
| `openaev.xtm.hub.collector.id` | — | Collector UUID |
| `openaev.xtm.opencti.{id}.enable` | `false` | Enable OpenCTI integration |
| `openaev.xtm.opencti.{id}.url` | — | OpenCTI URL |
| `openaev.xtm.opencti.{id}.token` | — | OpenCTI API token |

### XTM One

| Property | Default | Description |
|---|---|---|
| `openaev.xtm.one.url` | — | XTM One URL (set to enable registration) |
| `openaev.xtm.one.token` | — | Registration token |

---

## Testing Tenant Isolation

### Helper: `TenantIsolationTestHelper`

All tenant isolation tests use `TenantIsolationTestHelper` (available via `@Autowired` in
any class extending `IntegrationTest`).

```java
// Create two tenants and attach the current mock user to both
Tenant tenantA = helper.createTenantWithCurrentUser("Tenant A");
Tenant tenantB = helper.createTenantWithCurrentUser("Tenant B");

// Create data in tenant A
// POST /api/tenants/{tenantA.getId()}/scenarios → 201

// Assert data is NOT visible from tenant B
// GET /api/tenants/{tenantB.getId()}/scenarios/{id} → 404
```

Use `createTenantWithCapabilities()` when the endpoint requires real RBAC capabilities
(not just `isAdmin = true`).

### Test structure

Isolation tests live in a `@Nested @DisplayName("Tenant Isolation")` class inside the
API test class. The standard test matrix covers:

| Test | What it verifies |
|---|---|
| Cross-tenant read (GET by ID) | Entity from tenant A returns 404 from tenant B |
| Cross-tenant search | Search in tenant B does not return entity from tenant A |
| Cross-tenant update | PATCH/PUT on entity from tenant A returns 404 from tenant B |
| Cross-tenant delete | DELETE on entity from tenant A returns 404 from tenant B |
| Same-tenant read | Entity is visible within the same tenant (sanity check) |

See `.github/skills/add-test/TENANT_ISOLATION.md` for the full procedure and templates.

### Switching tenant context in tests

```java
// Switch Hibernate filter + TenantContext in one call
tenantIsolationHelper.switchToTenant(tenantId, entityManager);
```

This flushes and clears the persistence context before switching, ensuring Hibernate
issues a real SQL query (not returning L1 cache hits from the previous tenant).

---

## Anti-Patterns

| ❌ Don't | ✅ Do | Risk |
|---|---|---|
| Native `@Query` without `WHERE tenant_id = :tenantId` | Always add explicit tenant clause | Cross-tenant data leak |
| `@Column(unique = true)` on a tenant-scoped field | Composite `UNIQUE (field, tenant_id)` | Constraint violation across tenants |
| Single service for a dual-scope entity | Split `PlatformXxxService` / `TenantXxxService` | Mixed scoping, hard to audit |
| Return `tenant_id` in API responses | `@JsonIgnore` on tenant relation | Tenant ID exposure |
| Manually filtering by tenant in service code | Let the Hibernate filter do it | Inconsistent isolation, missed paths |
| Calling `TenantContext.getCurrentTenant()` in a non-`@Transactional` method | Ensure filter is active before querying | Filter not applied, full table scan |
| Reusing tenant context across threads | Use `TenantContext.clearCurrentTenant()` in `afterCompletion` | Cross-request tenant bleed |
| Hardcoding tenant ID in tests | Use `TenantIsolationTestHelper.createTenantWithCurrentUser()` | Brittle, non-isolated tests |
