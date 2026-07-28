---
applyTo: "openaev-model/src/main/java/**/*.java,openaev-api/src/main/java/**/*.java,**/migration/**"
description: "Multi-tenancy conventions: tenant isolation, dual-scope, anti-patterns"
---

# Multi-Tenancy Conventions

## Entity Scoping

| Tenant-scoped | Platform-level | Dual-scope (Settings, User, Role, Group) |
|---------------|----------------|------------------------------------------|
| `TenantBase` + `@Filter("tenantFilter")` + `TenantBaseListener` | `Base` + `ModelBaseListener` | `DualScopeBase` + `ModelBaseListener` |
| `tenant_id NOT NULL` | No `tenant_id` | `tenant_id` **NULLABLE** |
| Unique: composite `(field, tenant_id)` | Simple unique | Partial unique indexes |
| Single service + single API | Single service + single API | Two services + two APIs |

## Dual-Scope Pattern

- Entity implements `DualScopeBase` — `@Nullable getTenant()` / `setTenant()`
- Repository keeps generic JPA methods (`findByKey()`, `findAll()`, etc.) and adds explicit platform-scoped methods (`findByKeyAndTenantIsNull()`, `findAllByTenantIsNull()`) and tenant-scoped methods (`findByKeyAndTenantId()`, `findAllByTenantId()`)
- Two services: `PlatformXxxService` (uses `*TenantIsNull` methods, never receives tenantId) / `TenantXxxService` (uses `*TenantId` methods, always receives tenantId)
- Two APIs: `/api/platform-{entities}` / `TENANT_PREFIX + "/{entities}"`

## Critical Rules

1. Native `@Query` **bypasses** Hibernate filter → always add `WHERE tenant_id = :tenantId`
2. Never return `tenant_id` in API responses → `@JsonIgnore` on tenant relation
3. Unique constraints on tenant-scoped entities → composite `(field, tenant_id)`
4. Off the request thread, **both** tenant scopes must be set — see below

## Background Threads Carry Both Scopes

On an HTTP request `TenantInterceptor` sets the thread-local `TenantContext`, and
`TxCtxArgumentResolver` resolves the v2 `TxCtx`. Nothing does either on a Quartz job, a queue
consumer, an `@Async` task or a `parallelStream` worker, so a background entry point must set them
itself:

```java
TenantContext.setCurrentTenant(tenantId);   // v1: HibernateFilterTransactionAspect -> @Filter
try {
  tenantTx.execute(TxCtx.forTenant(tenantId), work);  // v2: transaction GUC -> inspector
} finally {
  TenantContext.clearCurrentTenant();       // restore the previous value on a shared pool
}
```

Setting only the v2 primitive is **not** enough while entities still carry `@Filter`, and it fails
silently: `TenantContext.getCurrentTenant()` falls back to `Tenant.DEFAULT_TENANT_UUID`, so every
JPQL / Criteria read resolves the DEFAULT tenant's rows and every `TenantBaseListener` insert is
attributed there. Single-tenant deployments never see it, because the fallback is the right tenant.
This shipped once: scheduled inject execution resolved another tenant's endpoints and created a
customer simulation's expectations against them (`InjectsExecutionJob#executeInTenant`).

Two details that are easy to miss: Hibernate `@Filter` does **not** apply to `find()` by primary
key, so a green `findById` proves nothing about scoping; and a deliberately cross-tenant sweep
(`session.disableFilter("tenantFilter")`) must set the tenant explicitly on anything it writes.

## Anti-Patterns

| ❌ Don't | ✅ Do |
|----------|-------|
| Native query without `WHERE tenant_id` | Add explicit tenant clause |
| `@Column(unique = true)` on tenant-scoped field | Composite `UNIQUE (field, tenant_id)` |
| Single service for dual-scope entity | Split `PlatformXxxService` / `TenantXxxService` |
| `tenant_id` in API response | `@JsonIgnore` on tenant relation |
| Background job setting only `TxCtx` | Set `TenantContext` too, cleared in a `finally` |
| Relying on the `TenantContext` default fallback | Pass the owning row's tenant explicitly |
