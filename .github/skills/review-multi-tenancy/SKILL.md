---
name: review-multi-tenancy
description: >-
  Step-by-step tenant isolation audit for OpenAEV pull requests.
  Use when reviewing PRs that touch entities, repositories, native queries, or migrations.
---

# Review Multi-Tenancy

## Step 1 — Inventory changed entities

```bash
# List all entity classes modified in the PR
git diff --name-only HEAD~1 | grep -E "openaev-model/.*\.java$"
```

For each modified entity, determine:
- Does it extend `TenantBase`? → tenant-scoped
- Does it extend `Base` only? → platform-level
- Is it a new entity? → must decide scoping

## Step 2 — Verify @Filter on tenant-scoped entities

```bash
grep -rn "extends TenantBase" --include="*.java" -l openaev-model/ | while read f; do
  echo "=== $f ==="
  grep -c "tenantFilter" "$f"
done
```

Expected: every `TenantBase` entity has `@Filter(name = "tenantFilter")`.
Missing `@Filter` = 🔴 CRITICAL — Hibernate won't apply tenant filtering.

## Step 3 — Audit native queries

```bash
grep -rn "nativeQuery = true" --include="*.java" openaev-model/ openaev-api/
```

For each native query:
- Does it have `WHERE tenant_id = :tenantId`? ✅
- Does it use SpEL: `WHERE tenant_id = :#{#tenantContext.currentTenant}`? ✅
- Does it join through a tenant-filtered entity? ✅
- Neither? → 🟠 HIGH — filter bypass

## Step 4 — Audit migrations

```bash
git diff --name-only HEAD~1 | grep "migration"
```

For each migration touching a tenant-scoped table, verify:
- ☐ `tenant_id VARCHAR(255) NOT NULL` column
- ☐ FK to `tenants(tenant_id) ON DELETE CASCADE`
- ☐ Index on `tenant_id`
- ☐ Unique constraints composite with `tenant_id`

## Step 5 — Audit async and background contexts

```bash
grep -rn "@Async\|@Scheduled\|CompletableFuture\|ExecutorService\|@EventListener" --include="*.java" openaev-api/src/main/java/ | grep -v "test"
```

For each async entry point:
- Is `TenantContext.setCurrentTenant()` called before any DB access?
- Is the tenant ID passed explicitly to the async method?

## Step 6 — Audit API responses

```bash
grep -rn "tenantId\|tenant_id\|getTenant" --include="*.java" openaev-api/src/main/java/io/openaev/api/ openaev-api/src/main/java/io/openaev/rest/ | grep -v "@JsonIgnore" | grep -v "test"
```

Any tenant reference in API layer without `@JsonIgnore` = 🟠 HIGH.

## Step 7 — Audit caching

```bash
grep -rn "@Cacheable\|@CachePut\|@CacheEvict" --include="*.java" openaev-api/src/main/java/
```

For each cached method:
- Does the cache key include tenant context?
- If not → 🟡 MEDIUM — cross-tenant cache poisoning risk

## Step 8 — Audit dual-scope entities

Dual-scope entities (Settings, User, Role, Group) implement `DualScopeBase` and have **nullable `tenant_id`**:

```bash
# Check that dual-scope repos extend DualScopeRepository
grep -rn "DualScopeRepository\|DualScopeBase" --include="*.java" openaev-model/src/main/java/
```

For each dual-scope entity:
- ☐ Entity implements `DualScopeBase`
- ☐ Repository extends `DualScopeRepository` (blocks unscoped `findAll()`, `findById()`)
- ☐ Two services: `PlatformXxxService` + `TenantXxxService`
- ☐ Two APIs: `PlatformXxxApi` + `TenantXxxApi`
- ☐ Repository only exposes scoped queries (`findByTenantIsNull`, `findByTenantId`)
- ☐ `PlatformXxxService` never receives `tenantId`; `TenantXxxService` always receives `tenantId`
- Entity without `DualScopeBase` = 🟠 HIGH
- Repository without `DualScopeRepository` = 🟠 HIGH
- Single service mixing both scopes = 🟠 HIGH

## Step 9 — Compile findings

Generate the Multi-Tenancy Review Summary following the output format
defined in `multi-tenancy-reviewer.agent.md`.
