# Multi-Tenancy Architecture

> **Status**: Living document — update when adding tenant-scoped features.
> **Source of truth**: `.github/instructions/multi-tenancy.instructions.md`

## Table of Contents

1. [Overview](#overview)
2. [CE vs EE Feature Matrix](#ce-vs-ee-feature-matrix)
3. [Entity Scoping Model](#entity-scoping-model)
4. [Request Isolation Pipeline](#request-isolation-pipeline)
5. [Tenant Lifecycle](#tenant-lifecycle)
6. [Per-Resource Isolation](#per-resource-isolation)
7. [Dual-Scope Pattern](#dual-scope-pattern)
8. [Platform-Level Background Jobs](#platform-level-background-jobs)
9. [SSO / Identity Provider Integration](#sso--identity-provider-integration)
10. [Configuration Reference](#configuration-reference)
11. [Testing Tenant Isolation](#testing-tenant-isolation)
12. [Anti-Patterns](#anti-patterns)

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
- Tenant membership is validated on every request and **cached** (`tenantMembership` cache,
  key: `userId:tenantId`) to avoid hitting the database on every call. The cache is explicitly
  evicted (not TTL-only) when users are added to or removed from tenants.
- The tenant REST API (`POST /api/tenants`) is an **Enterprise Edition** feature.
  Community Edition deployments run as a single implicit tenant.

---

## CE vs EE Feature Matrix

| Feature | Community Edition | Enterprise Edition |
|---|---|---|
| Single implicit tenant | ✅ | ✅ |
| Create / manage multiple tenants (`POST /api/tenants`) | ❌ | ✅ |
| Get / search tenants (`GET /api/tenants`) | ❌ | ✅ |
| Soft-delete & reactivate tenants | ❌ | ✅ |
| Per-tenant RabbitMQ queues | ❌ | ✅ |
| Per-tenant MinIO path isolation (`{tenantId}/`) | ❌ | ✅ |
| Per-tenant built-in connectors | ❌ | ✅ |
| Per-tenant XTM Hub registration | ❌ | ✅ |
| Tenant-scoped roles & groups | ❌ | ✅ |
| SSO `tenant_id` attribute mapping | ❌ | ✅ |
| Platform-level users / roles / groups | ✅ | ✅ |
| Platform-level background jobs | ✅ | ✅ |

> **How EE is enforced**: every tenant endpoint is annotated with
> `@AccessControl(isEnterpriseEdition = true)`. The `@AccessControl` AOP interceptor
> checks the license before the method body executes.
>
> License key: `openaev.application-license` in `application.properties`.

---

## Entity Scoping Model

Every JPA entity falls into exactly one of three scoping categories:

| Category | Interface | `tenant_id` | Unique constraint | Listeners |
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

**Current dual-scope entities:** `User`, `Role`, `Group`, `Settings`

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
┌──────────────────────────────────────────────────────────┐
│  TenantInterceptor  (HandlerInterceptor)                  │
│  1. Extract {tenantId} from URI template path variable    │
│     → only fires when pathVariables contains "tenantId"   │
│  2. Load authenticated principal (OpenAEVPrincipal)       │
│  3. Check membership via TenantMembershipCacheManager     │
│     → cache key: userId:tenantId                          │
│     → throws TenantAccessDeniedException (403) on miss    │
│  4. TenantContext.setCurrentTenant(tenantId)              │
│  5. afterCompletion: TenantContext.clearCurrentTenant()   │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  HibernateFilterTransactionAspect  (AOP)                  │
│  @Before every @Transactional method                      │
│  Reads TenantContext.getCurrentTenant()                   │
│  Enables Hibernate "tenantFilter" on the EntityManager    │
│  → scopes all JPQL / Criteria queries automatically       │
│  ⚠️  Does NOT apply to native @Query SQL                  │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  JPA / Hibernate                                          │
│  tenantFilter: WHERE tenant_id = :tenantId               │
│  Applied to all @Filter-annotated entities               │
└──────────────────────────────────────────────────────────┘
```

### URL structure

All tenant-scoped endpoints share a common prefix defined in `TenantUriUtils`:

```java
// TenantUriUtils.java
public static final String TENANT_BASE_PATH = "/api/tenants/";
public static final String TENANT_PREFIX    = "/api/tenants/{tenantId}";
```

**Tenant-scoped endpoints** (require `tenantId` in path):
- `GET  /api/tenants/{tenantId}/scenarios`
- `POST /api/tenants/{tenantId}/exercises`
- `GET  /api/tenants/{tenantId}/assets`

**Platform-level endpoints** (no tenant context):
- `GET  /api/platform-groups`
- `GET  /api/platform-users`
- `GET  /api/platform-roles`

### Tenant membership cache

`TenantMembershipCacheManager` wraps the DB membership check with Spring `@Cacheable`:

```
Cache name : tenantMembership
Cache key  : userId + ':' + tenantId
Eviction   : explicit (CacheEvict) — not TTL-based
             → fires on user add/remove from tenant
             → evictForUser(userId, tenantIds) for bulk eviction
```

The cache is **explicitly evicted** (not TTL-only) when users are added to or removed from
tenants, so membership changes take effect immediately without waiting for expiry.

---

## Tenant Lifecycle

### Creation

`TenantService.create()` orchestrates tenant creation in a single transaction:

```
1. Persist Tenant row (id, name, description)
2. Switch TenantContext to new tenant ID
3. Run all DependenciesManager beans (ordered by prerequisites):
   ├── DomainService              → creates default domain for the tenant
   ├── TenantQueueService         → declares per-tenant RabbitMQ queues
   ├── MinioService               → logs tenant file path prefix ({tenantId}/)
   └── BuiltinIntegrationFactory implementations (auto-discovered via Spring):
       ├── OpenAEVAgentFactory    → registers OpenAEV Agent connector
       ├── OpenAEVImplantFactory  → registers OpenAEV Implant connector
       └── ... (any @Service implementing BuiltinTenantRegistrable)
```

> **Extension point**: adding a new per-tenant built-in component requires only implementing
> `DependenciesManager` (or `BuiltinTenantRegistrable` via `BuiltinIntegrationFactory`) on a
> `@Service` bean — no manual wiring in `TenantService` or `ManagerFactory`.

### Soft-delete & reactivation

Tenants are **soft-deleted** with a 30-day grace period:

```
softDelete()    → sets deletedAt = now()
                  tenant data is preserved and still accessible to admins
reactivate()    → clears deletedAt (only within 30-day window)
                  fails with error if grace period has expired
purgeExpired()  → TenantPurgeJob (Quartz) picks up expired soft-deleted tenants
                  → runs DependenciesManager.deleteDependencyForTenant() for each
                  → hard-deletes the Tenant row
```

### Startup initialization

At startup, `TenantQueueService.init()` (via `@PostConstruct`) declares RabbitMQ queues
for every active tenant, so queues are ready before the first message arrives.

---

## Per-Resource Isolation

### RabbitMQ queues (`TenantQueueService`)

Each tenant gets its own set of queues. Queue name pattern:

```
{openaev.rabbitmq.prefix}-{tenantId}_execution_{queueName}
```

Queues are declared at:
- **Startup** — for all existing active tenants (`@PostConstruct`)
- **Tenant creation** — for the new tenant (via `DependenciesManager`)
- **Tenant purge** — queues are deleted (via `DependenciesManager.deleteDependencyForTenant()`)

Configured per-tenant queues (from `application.properties`):

| Queue key | Queue name | Purpose |
|---|---|---|
| `inject-trace` | `inject-trace` | Inject execution traces |
| `workflows-ready` | `workflows-ready` | Workflow engine ready events |
| `workflows-update` | `workflows-update` | Workflow engine update events |

### File storage (`MinioService`)

All files are stored under a **tenant-prefixed path** in a single shared bucket:

```
Bucket : {minio.bucket}   (default: "openaev")
Path   : {tenantId}/{fileName}
```

`MinioService.uploadFileInTenantPath()` automatically prefixes the path using
`TenantContext.getCurrentTenant()`. On tenant purge, all objects under `{tenantId}/`
are batch-deleted (1 000 objects per S3 request).

### Built-in connectors (`BuiltinIntegrationFactory`)

Each tenant gets its own registered instances of built-in connectors (injectors, executors).

```
BuiltinIntegrationFactory (abstract)
  └── implements BuiltinTenantRegistrable
  └── registerConnectorForTenant()   ← tenant-specific DB registration logic

Auto-discovered by ManagerFactory via Spring injection.
Called once per tenant at creation via TenantRegistrationExecutor.

TenantRegistrationExecutor:
  ├── registerForTenantIsolated()  → startup path: switches context, registers, restores
  └── registerForTenant()          → creation path: caller manages context
```

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
| `User` | `UserService` | `TenantUserService` | `/api/users` | `/api/tenants/{tenantId}/users` |
| `Role` | *(platform)* | `TenantRoleService` | `/api/platform-roles` | `/api/tenants/{tenantId}/roles` |
| `Group` | *(platform)* | `TenantGroupService` | `/api/platform-groups` | `/api/tenants/{tenantId}/groups` |

### Rules

- Platform service methods use `*TenantIsNull()` repository queries and **never** receive a `tenantId`
- Tenant service methods use `*TenantId()` repository queries and **always** receive a `tenantId`
- `tenant_id` is **never** returned in API responses — `@JsonIgnore` on the tenant relation
- Unique constraints use **partial indexes**:
  - Platform rows: unique on `field` where `tenant_id IS NULL`
  - Tenant rows: `UNIQUE (field, tenant_id)`

---

## Platform-Level Background Jobs

The following Quartz jobs run at the **platform level** (not per-tenant). They either iterate
over all active tenants internally or operate on platform-wide data.

| Job class | Quartz identity | Purpose |
|---|---|---|
| `InjectsExecutionJob` | `InjectsExecutionJob` | Triggers scheduled inject execution across all tenants |
| `ComchecksExecutionJob` | `ComchecksExecutionJob` | Processes communication check results |
| `ScenarioExecutionJob` | `ScenarioExecutionJob` | Starts scheduled scenario runs |
| `EngineSyncExecutionJob` | `EngineSyncExecutionJob` | Syncs search engine indexes |
| `ManagerIntegrationsSyncJob` | `managerIntegrationsSync` | Calls `ManagerFactory.monitorIntegrations()` — iterates all tenants |
| `SecurityCoverageJob` | `SecurityCoverageJob` | Recomputes security coverage scores |
| `OpenCTIConnectorRegisterPingJob` | `ConnectorPingJob` | Registers/pings OpenCTI connectors for all tenants |
| `UserEventRetentionJob` | `UserEventRetentionJob` | Purges old user events (platform-wide) |
| `ExecutionTracesBatchRequeueJob` | `executionTracesBatchRequeueJob` | Requeues stuck execution traces |
| `TenantPurgeJob` | `TenantPurgeJob` | Hard-deletes expired soft-deleted tenants + their data |
| `QueueChainingJob` | `QueueChainingJob` | Drives the workflow chaining engine |
| `WorkflowTimeoutJob` | `WorkflowTimeoutJob` | Times out stalled workflow steps |

> ⚠️ Jobs that iterate over tenants (e.g. `ManagerIntegrationsSyncJob`,
> `OpenCTIConnectorRegisterPingJob`) must switch `TenantContext` for each tenant
> and restore it afterwards. Failing to clear the context between tenants causes
> cross-tenant data bleed.

---

## SSO / Identity Provider Integration

OpenAEV supports mapping SSO identity provider attributes to tenants.

### Tenant mapping via SSO (`openaev.provider.{registrationId}.tenant_id`)

When an SSO provider is configured, the `tenant_id` attribute can be used to automatically
assign users to a tenant at login:

```properties
# Automatically assigns authenticated users to a tenant based on IdP attribute
openaev.provider.{registrationId}.tenant_id=<idp-tenant-attribute-name>

# Roles and groups management via SSO
openaev.provider.{registrationId}.roles_path=<path-to-roles-in-token>
openaev.provider.{registrationId}.roles_admin=<admin-role-value>
openaev.provider.{registrationId}.groups_management=<true|false>
openaev.provider.{registrationId}.audience=<expected-audience>
```

> This is an **Enterprise Edition** feature — tenant assignment via SSO requires a valid EE license.

---

## Configuration Reference

All multi-tenancy-related configuration properties from
`openaev-api/src/main/resources/application.properties`.

### Core application

| Property | Default | Description |
|---|---|---|
| `openaev.application-license` | *(empty)* | EE license key — empty = Community Edition |
| `openaev.base-url` | `http://localhost:8080` | Base URL for redirect URIs and SSO callbacks |
| `openaev.cookie-name` | `openaev_token` | Session cookie name |
| `openaev.cookie-secure` | `false` | Set `true` in production (HTTPS) |
| `openaev.cookie-duration` | `P1D` | Session duration (ISO 8601 duration) |
| `openaev.unsecured-certificate` | `false` | Allow self-signed TLS certificates |
| `openaev.with-proxy` | `false` | Enable HTTP proxy support |
| `openaev.extra-trusted-certs-dir` | *(empty)* | Directory with extra CA certificates to trust |
| `openaev.starterpack.enabled` | `true` | Load starter data pack on first boot |
| `openaev.enabled-dev-features` | *(empty)* | Comma-separated list of dev feature flags to enable |

### Admin bootstrap (mandatory on first start)

| Property | Description |
|---|---|
| `openaev.admin.email` | Admin user email |
| `openaev.admin.password` | Admin user password |
| `openaev.admin.token` | Admin API token |
| `openaev.admin.encryption_key` | Encryption key for sensitive data |
| `openaev.admin.encryption_salt` | Encryption salt (min 8 bytes) |

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
| `spring.security.oauth2.client.registration.{id}.redirect-uri` | Callback URL — use `${openaev.base-url}/login/oauth2/code/{id}` |
| `spring.security.oauth2.client.registration.{id}.scope` | Scopes (e.g. `openid,profile,email`) |
| `spring.security.saml2.relyingparty.registration.{id}.entity-id` | SAML2 entity ID |
| `spring.security.saml2.relyingparty.registration.{id}.assertingparty.metadata-uri` | IdP metadata URI |
| `openaev.provider.{id}.firstname_attribute_key` | SAML attribute for first name |
| `openaev.provider.{id}.lastname_attribute_key` | SAML attribute for last name |
| `openaev.provider.{id}.roles_path` | Path to roles in token/assertion |
| `openaev.provider.{id}.roles_admin` | Role value that grants admin |
| `openaev.provider.{id}.audience` | Expected token audience |
| `openaev.provider.{id}.groups_management` | Enable group sync from IdP |
| `openaev.provider.{id}.tenant_id` | IdP attribute to use for tenant assignment (EE only) |

### RabbitMQ

| Property | Default | Description |
|---|---|---|
| `openaev.rabbitmq.hostname` | `localhost` | RabbitMQ host |
| `openaev.rabbitmq.port` | `5672` | AMQP port |
| `openaev.rabbitmq.prefix` | `openaev` | Queue name prefix (used in per-tenant queue names) |
| `openaev.rabbitmq.user` | `guest` | Username |
| `openaev.rabbitmq.pass` | `guest` | Password |
| `openaev.rabbitmq.vhost` | `/` | Virtual host |
| `openaev.rabbitmq.ssl` | `false` | Enable TLS |
| `openaev.rabbitmq.management-port` | `15672` | Management plugin port |
| `openaev.rabbitmq.queue-type` | `classic` | `classic` or `quorum` |
| `openaev.rabbitmq.management-insecure` | `true` | Allow insecure management API |
| `openaev.rabbitmq.trust-store-password` | — | TLS trust store password (if ssl=true + insecure=false) |
| `openaev.rabbitmq.trust.store` | — | Path to TLS trust store file |

Per-queue config (replace `{queueKey}` with `inject-trace`, `workflows-ready`, `workflows-update`):

| Property | Description |
|---|---|
| `openaev.queue-config.{queueKey}.publisher-number` | Publisher thread count |
| `openaev.queue-config.{queueKey}.consumer-number` | Consumer thread count |
| `openaev.queue-config.{queueKey}.worker-number` | Worker thread count |
| `openaev.queue-config.{queueKey}.worker-frequency` | Worker polling interval (ms) |
| `openaev.queue-config.{queueKey}.queue-name` | Base queue name |
| `openaev.queue-config.{queueKey}.max-size` | Max queue size |
| `openaev.queue-config.{queueKey}.consumer-qos` | Consumer QoS prefetch count |
| `openaev.queue-config.{queueKey}.publisher-qos` | Publisher QoS (0 = unlimited) |

### MinIO / S3

| Property | Default | Description |
|---|---|---|
| `minio.endpoint` | `localhost` | MinIO hostname |
| `minio.port` | `9000` | MinIO port |
| `minio.bucket` | `openaev` | Bucket name (shared; isolation via `{tenantId}/` path prefix) |
| `minio.access-key` | — | S3 access key |
| `minio.access-secret` | — | S3 secret key |
| `openaev.s3.use-aws-role` | `false` | Use AWS IAM role instead of key/secret |
| `openaev.s3.sts-endpoint` | — | STS endpoint for IAM role assumption |

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

### Audit logging

| Property | Default | Description |
|---|---|---|
| `openaev.audit-logs.service.enabled` | `false` | Enable audit log service |
| `openaev.audit-logs.log-reads` | `false` | Include read operations in audit log |
| `openaev.audit-logs.console.enabled` | `true` | Log audit events to console |
| `openaev.audit-logs.file.enabled` | `true` | Log audit events to file |
| `openaev.audit-logs.engine.enabled` | `true` | Index audit events in search engine |
| `engine.audit-log-retention-days` | `365` | Days before old audit-log indexes are deleted (0 = no policy) |
| `engine.audit-log-rollover-max-size` | `5gb` | Max shard size before index rollover |
| `engine.audit-log-rollover-max-age` | `30d` | Max index age before rollover |

### Executors (built-in, per-tenant)

| Property | Default | Description |
|---|---|---|
| `executor.openaev.binaries.origin` | `local` | Binary source: `local` or `repository` |
| `executor.openaev.binaries.version` | `@project.version@` | Binary version to use |
| `executor.caldera.enable` | `false` | Enable Caldera executor |
| `executor.tanium.enable` | `false` | Enable Tanium executor |
| `executor.crowdstrike.enable` | `false` | Enable CrowdStrike executor |
| `executor.sentinelone.enable` | `false` | Enable SentinelOne executor |

---

## Testing Tenant Isolation

### Helper: `TenantIsolationTestHelper`

All tenant isolation tests use `TenantIsolationTestHelper` (available via `@Autowired` in
any class extending `IntegrationTest`).

```java
// Create two real tenants and attach the current mock user to both
Tenant tenantA = helper.createTenantWithCurrentUser("Tenant A");
Tenant tenantB = helper.createTenantWithCurrentUser("Tenant B");

// Create data in tenant A
// POST /api/tenants/{tenantA.getId()}/scenarios → 201

// Assert data is NOT visible from tenant B
// GET /api/tenants/{tenantB.getId()}/scenarios/{id} → 404
```

Use `createTenantWithCapabilities()` when the endpoint requires real RBAC capabilities
(not just `isAdmin = true`).

### Switching tenant context in tests

```java
// Switches Hibernate filter + TenantContext in one call
// Flushes and clears the persistence context first (no L1 cache hits from previous tenant)
tenantIsolationHelper.switchToTenant(tenantId, entityManager);
```

### Standard test matrix

Isolation tests live in a `@Nested @DisplayName("Tenant Isolation")` class inside the
API test class:

| Test | What it verifies |
|---|---|
| Cross-tenant read (GET by ID) | Entity from tenant A returns 404 from tenant B |
| Cross-tenant search | Search in tenant B does not return entity from tenant A |
| Cross-tenant update | PATCH/PUT on entity from tenant A returns 404 from tenant B |
| Cross-tenant delete | DELETE on entity from tenant A returns 404 from tenant B |
| Same-tenant read | Entity is visible within the same tenant (sanity check) |

See `.github/skills/add-test/TENANT_ISOLATION.md` for the full procedure and templates.

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
| Platform jobs that forget to switch `TenantContext` per tenant | Always switch + restore context in per-tenant loops | Cross-tenant data bleed in background jobs |
| Adding new code to `openaev-framework` | Place new code in `openaev-api` or `openaev-model` | Framework module is deprecated |
