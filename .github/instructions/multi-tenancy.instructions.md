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

## V1 vs V2 Isolation — Two Mechanisms, Never Both

| | v1 (legacy, most tables) | v2 (`openaev.tenant.active-tables`) |
|---|---|---|
| Read isolation | `@Filter("tenantFilter")`, set via `TenantContext` thread-local | SQL rewritten by `TenantStatementInspector` + `can_access_tenant`, scoped per-transaction by a `TxCtx` argument |
| Entrypoint requirement | None — filter is ambient | Every `@Transactional` entrypoint whose call graph reaches the table (directly, via another service, or via an association/join) **must** declare a `TxCtx` parameter |
| `find()` by primary key | **Not filtered** — `@Filter` does not apply to `find()` | Filtered — the inspector rewrites by SQL text, not by Hibernate filter semantics |
| Native query safety | Needs explicit `WHERE tenant_id` | Needs a FROM/JOIN shape the inspector already accepts (see `TenantStatementInspectorTest`); a shape it doesn't recognize fails closed (`TENANT_FILTERING_REFUSED`) |
| Background writer | `TenantScopedTransaction` not required | Convert the job to `TenantScopedTransaction` (`openaev-model/…/context/TenantScopedTransaction.java`) so it runs inside a real per-tenant transaction |

A table is either v1 or v2-active, never both: once a table is added to
`active-tables`, its `@Filter("tenantFilter")` must be removed in the same
commit — leaving it in place is not defense in depth, it silently duplicates
(and can conflict with) the inspector's own predicate.

**Missing `TxCtx` fails silently, not loudly.** If a v2-active table is
reached without a `TxCtx` in scope, the inspector fail-closes the read: no
exception, just an empty result. This shipped once — `ExerciseApi#changeExerciseStatus`
called the same EE-executor-launchability gate as its sibling methods
(`updateExerciseStart`), which had `TxCtx` wired, but `changeExerciseStatus`
didn't; `agent.getExecutor()` (an eager `@ManyToOne` on the now-v2-active
`executors` table) silently returned null, so the Enterprise Edition license
check on manual launch never threw. `TenantScopedEntrypointsTxCtxArchTest`
now enumerates every entrypoint required to carry `TxCtx`; when activating a
table, walk the *entire* call graph up to every `@RestController` entrypoint
(not just one hop) and add each one found — see `activate-tenant-table/SKILL.md`.

A bare `TxCtx` parameter is always safe to add ahead of a table's actual
activation: it's inert until the table is in `active-tables`, and
`TxCtxArgumentResolver` defaults to the caller's full authorized tenant set
when no `@RequireTenantSelector` is present, so it never over- or
under-scopes an unrelated read.

## Critical Rules

1. Native `@Query` on a **v1** table **bypasses** Hibernate filter → always add `WHERE tenant_id = :tenantId`
2. Native query on a **v2-active** table → the FROM/JOIN shape must already be accepted by `TenantStatementInspector` (see table above)
3. Every entrypoint whose call graph reaches a **v2-active** table must declare a `TxCtx` parameter — its absence is a silent empty-result regression, not a build/runtime error
4. `@Filter("tenantFilter")` and v2 activation (`active-tables`) are mutually exclusive per table — never both
5. Never return `tenant_id` in API responses → `@JsonIgnore` on tenant relation
6. Unique constraints on tenant-scoped entities → composite `(field, tenant_id)`
7. Off the request thread, **both** tenant scopes must be set — see below

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

**v2 activation does not retire `TenantContext.setCurrentTenant()` on its own.** Converting a job's
reads to `TenantScopedTransaction` only changes read isolation; `TenantBaseListener.@PrePersist`
still stamps `tenant_id` from `TenantContext.getCurrentTenant()` unconditionally, v1 or v2. Only
drop `setCurrentTenant()` when the job is read-only, every table it touches is v2-active, and no
remaining code path is still v1 — otherwise its absence silently attributes inserts to
`Tenant.DEFAULT_TENANT_UUID`.

## Anti-Patterns

| ❌ Don't | ✅ Do |
|----------|-------|
| Native query without `WHERE tenant_id` | Add explicit tenant clause |
| `@Column(unique = true)` on tenant-scoped field | Composite `UNIQUE (field, tenant_id)` |
| Single service for dual-scope entity | Split `PlatformXxxService` / `TenantXxxService` |
| `tenant_id` in API response | `@JsonIgnore` on tenant relation |
| Background job setting only `TxCtx` | Set `TenantContext` too, cleared in a `finally`, unless the job is read-only and fully v2-active (see above) |
| Relying on the `TenantContext` default fallback | Pass the owning row's tenant explicitly |
| Entrypoint reaching a v2-active table with no `TxCtx` param | Add `TxCtx ctx` and register it in `TenantScopedEntrypointsTxCtxArchTest` |
| `@Filter("tenantFilter")` left in place after adding a table to `active-tables` | Remove it in the same commit — v1 and v2 never coexist |
