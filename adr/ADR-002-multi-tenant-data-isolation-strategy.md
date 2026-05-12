# ADR-0002: Multi-tenant data isolation strategy

|  |                                                                                       |
| --- |---------------------------------------------------------------------------------------|
| Status | Accepted                                                                              |
| Date | 2026-05-11                                                                            |
| Deciders | Corinne, Backend team                                                                 |
| Consulted | —                                                                                     |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/3505|

## 1. Context

OpenAEV is evolving from a single-tenant platform to a multi-tenant platform. All tenant-scoped entities share the same PostgreSQL database and schema (shared-schema multi-tenancy). 
We need to guarantee that one tenant's data can never be read, modified, or leaked to another tenant. The initial chosen approach for tenant isolation was to use
**Hibernate `@Filter`** for automatic entity-level scoping in all JPQL to have a developper experience as close as possible to single-tenant code. 

However, Hibernate filters only apply to JPQL/Criteria queries and can be easily bypassed by native SQL or if a developer forgets to use the correct repository method (e.g., `findByIdAndTenantId` vs `findById`).
A single isolation mechanism is insufficient: Hibernate filters don't cover native SQL.

To mitigate this risk, we decided to add **PostgreSQL Row-Level Security (RLS)** as a database-level safety net that applies to all queries, including native SQL. 
RLS policies will filter rows based on the `app.current_tenant` session variable, which is set on every connection checkout.

## 2. Decision drivers

1. **Security and data isolation** — a tenant must never see another tenant's data, even partially.
2. **Defence in depth** — no single layer failure should cause a data leak.
3. **Developer ergonomics** — isolation should be automatic and hard to bypass accidentally.
4. **Testability** — each layer must be independently verifiable in CI. Each integration test should validate tenant isolation for the code it covers. New AI skills have added to cover tenant isolation gaps (e.g., native queries). See [TENANT_ISOLATION](https://github.com/OpenAEV-Platform/openaev/blob/release/current/.github/skills/add-test/TENANT_ISOLATION.md).
5. **Performance** — isolation must not add measurable latency to hot paths.

## 3. Considered options

### Option A: Application-level only (service + repository scoping)

All repositories use `findByIdAndTenantId()`, services receive `tenantId` explicitly, controllers resolve tenant from the URL path.

**Pros**: Simple, no DB-level complexity, fully testable with MockMvc.
**Cons**: A single missed `findById()` or native `@Query` leaks data. No safety net. Relies entirely on developer discipline and code review.

> NOTE: Option A was the initially chose approach, but we identified critical gaps during implementation and testing that led us to add RLS as a safety net (Option C).

### Option B: Database-level RLS only

PostgreSQL Row-Level Security policies filter all queries transparently based on `app.current_tenant` session variable.

**Pros**: Database-level guarantee, covers native SQL, impossible to bypass from application code.
**Cons**: Difficult to test (superuser in tests bypasses RLS), error messages are opaque (silent empty results vs. 404), no Hibernate L1 cache awareness, harder to debug, requires `SET ROLE` to a non-superuser, tables need to be listed in RLS scope.

### Option C: two-layer defence in depth (chosen)

Combine entity-level Hibernate filters, application-level tenant scoping, and database-level RLS as a safety net.

**Pros**: Defence in depth — each layer catches what the others miss. Testable at each level independently.
**Cons**: Slightly more complexity. Developer need to develop code being aware of multitenancy.

## 4. Decision

We chose **Option C** — two layers of tenant isolation:

### Layer 1: Hibernate `@Filter` (entity level)

Every tenant-scoped entity extends `TenantBase`, declares `@Filter(name = "tenantFilter")`, and has a `tenant_id` FK. 
The Hibernate filter is enabled per-session via an AOP aspect (`HibernateFilterTransactionAspect`) that reads from `TenantContext`. This ensures all JPQL/Criteria queries are automatically filtered.

```java
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Scenario extends BaseEntity implements TenantBase { ... }
```
> ⚠️ NOTE ⚠️:  Interface `DualScopeBase` is used for entities that can be either platform-level ({@code tenant_id IS NULL}) or tenant-scoped ({@code tenant_id NOT NULL}). 
> These entities require special handling in services and repositories to account for both cases as they don't use Layer 1: hibernate @Filter but relies on Layer 2: application-level scoping entirely.

### Layer 2: Application-level scoping (REST + service)

- **Controllers**: Tenant is resolved from the URL path (`/api/tenants/{tenantId}/...`) by `TenantInterceptor`, which validates user membership and sets `TenantContext`.
- **Repositories**: Use `findByIdAndTenantId(id, tenantId)` instead of `findById(id)`.
- **Services**: Never call `TenantContext` directly — receive tenant as an argument from the controller layer.
- **Integration tests**: Every `*ApiTest.java` includes a `@Nested class TenantIsolation` that creates two tenants and asserts cross-tenant access returns 403/404.

### Layer 3: PostgreSQL Row-Level Security (database safety net)

RLS is enabled on all tables listed in `TenantScopedTables.java`. The application connects as role `openaev_app` (non-superuser, non-owner) so RLS policies are enforced:

```sql
CREATE POLICY tenant_isolation_<table> ON <table>
  USING (tenant_id = current_setting('app.current_tenant'));
```

The `app.current_tenant` session variable is set on every connection checkout by `TenantAwareDataSourceConfig`, reading from `TenantContext`.

> ⚠️ NOTE ⚠️: Background jobs: For scheduled tasks that run outside of a web request context and are tenant agnostic, the tenant validation is turn off for ta given thread 
> by setting `TenantContext.setCurrentTenant(null)`. This allows background jobs to access all tenant data when necessary while still enforcing isolation for web requests.

## 5. Consequences

### Positive

- A single forgotten `findById()` is caught by the Hibernate filter (Layer 1).
- A native `@Query` missing `WHERE tenant_id` is caught by RLS (Layer 3).
- Integration tests (Layer 2) catch application logic errors in CI without needing RLS.

### Negative / trade-offs

- Mid-transaction tenant switches require updating 2 systems in sync: `TenantContext.setCurrentTenant(tenantId)` - Hibernate - and `set_config('app.current_tenant', tenantId, false)` — JDBC connection (for RLS)
- Adding a new tenant-scoped table requires updating `TenantScopedTables.java` for RLS policy generation.

### Neutral

- Performance impact is negligible: Hibernate filter adds a `WHERE` clause (same as manual scoping), RLS adds the same check at the planner level (no double cost if both match the same index).
- Integration tests requires no DB superuser, so RLS cannot be exercised in Spring integration tests. E2E tests (Playwright) at API level for a set of smoke tests will be recommended to validate RLS specifically.

## 6. Validation

| Layer | Validation method | Status |
|-------|-------------------|--------|
| Hibernate filter | `@Nested class TenantIsolation` in all `*ApiTest.java` | ✅ In CI |
| Application scoping | Same integration tests + `TenantInterceptor` membership check | ✅ In CI |
| RLS | Manual curl tests (cross-tenant GET returns empty/404) + future Playwright E2E | ✅ Manual, E2E planned |

## 7. Follow-ups

| # | Limitation or follow-up | Severity | Owner | Target |
| --- | --- | --- | --- | --- |
| 1 | Add Playwright E2E tests specifically for RLS on native `@Query` endpoints | Medium | — | Next sprint |
| 2 | Automate `TenantScopedTables` validation (fail build if entity has `TenantBase` but table not listed) | Low | — | Backlog |
| 3 | Audit all existing native `@Query` methods for missing `WHERE tenant_id` | High | — | In progress |

## 8. Decision log

| Date | Author | Change |
| --- | --- | --- |
| 2026-05-11 | Corinne | Initial draft |

