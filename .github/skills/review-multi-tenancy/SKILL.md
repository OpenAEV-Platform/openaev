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

> NOTE: if the API, entity class is using v2 API isolation mechanism, then the `@Filter` annotation should not be there. 
> Check the ./activate-tenant-table/SKILL.md. 

## Step 2b — Determine v1 vs v2 for every touched table

Before auditing anything else, classify each entity/table touched by the PR.
The correct pattern (and the correct severity for a missing check) is
different depending on which mechanism governs the table — applying v1
expectations (`@Filter`, `TenantContext`) to a v2-active table, or vice versa,
produces false positives/negatives in every later step.

```bash
# v2-active tables: the authoritative list
grep -n "active-tables" openaev-api/src/main/resources/application.properties

# does the touched entity/repository carry TxCtx-based scoping anywhere in the diff?
grep -rn "TxCtx\|TenantScopedTransaction\|RequireTenantSelector\|can_access_tenant" --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null

# does it still rely on v1?
grep -rn "TenantBase\b\|@Filter(\"tenantFilter\")\|TenantContext\." --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null
```

Classify each touched table as one of:
- **v2-active** — its name appears in `openaev.tenant.active-tables`. Isolation
  is enforced by `TenantStatementInspector` + `can_access_tenant`, scoped by a
  `TxCtx` parameter threaded through every `@Transactional` entrypoint that
  reaches it (directly, via another service, or via an association/join —
  see `activate-tenant-table/SKILL.md` Phase 1). For these tables:
  - `@Filter("tenantFilter")` should be ABSENT (v1 and v2 are mutually
    exclusive per table; both present is a red flag, not defense in depth)
  - every new/changed `@Transactional` method whose call graph reaches the
    table must carry a `TxCtx` param — missing it is 🔴 CRITICAL (silent
    empty-result regression, not a loud failure; see the `changeExerciseStatus`
    incident: a caller of the same EE-executor gate as two already-wired
    sibling endpoints shipped without `TxCtx` and silently bypassed the
    license check)
  - write/attribution endpoints (composite-PK lookups, row creation) need
    `@RequireTenantSelector` on the `TxCtx` param, not just its presence
  - a native `@Query` that `JOIN`s a v2-active table anywhere in the codebase
    (not just its own repository) is pulled into the fail-closed rewrite —
    check its FROM/JOIN shape against `TenantStatementInspectorTest` (#7007)
- **v1 (still `@Filter`-based)** — not in `active-tables`. Isolation is
  Hibernate `@Filter` + `TenantBaseListener`, ambient via
  `TenantContext.getCurrentTenant()`. For these tables, Steps 2-7 below
  (as originally written) apply as-is.
- **permanently v1 by design** — a table deliberately kept off
  `active-tables` because its access pattern (e.g. a native upsert with
  `ON CONFLICT ... RETURNING`) is a shape the inspector cannot rewrite. Every
  statement on it must carry its own explicit tenant predicate
  (`WHERE tenant_id = :tenantId` / `tenant.id IN :tenantIds`) since neither
  `@Filter` nor the inspector protects it. Missing predicate here is 🔴
  CRITICAL — there is no fallback mechanism at all for this class of table.
  Model: `AttackPathGraphVersionRepository` (see its class javadoc).

If the PR adds a `TxCtx` parameter but the table is NOT in `active-tables`,
that's a no-op today (the aspect sets an inert GUC) — not wrong, but flag it
as premature/dead code unless it's part of a deliberate pre-wiring pass, so it
doesn't get mistaken for the table having gone live.

## Step 3 — Audit native queries

```bash
grep -rn "nativeQuery = true" --include="*.java" openaev-model/ openaev-api/
```

For each native query, first check Step 2b's classification of the table(s) it touches:
- table is v2-active → this is the #7007 FROM/JOIN-shape check, not the v1
  checklist below: verify the shape is in `TenantStatementInspectorTest`'s
  accepted list (a table-function FROM item needs `LATERAL`); a refused shape
  fails closed (`TENANT_FILTERING_REFUSED`) in production, not silently
- table is v1 or permanently-v1-by-design → the checklist below applies:
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

# is this entry point already converted to the background transaction primitive?
grep -rln "TenantScopedTransaction" --include="*.java" openaev-api/src/main/java/
```

First check whether the job opens its transaction with `@Transactional` (v1-style, still on the frozen background baseline — see `reduce-tx-baseline/SKILL.md`) or with `TenantScopedTransaction.execute`/`executeNew`/`forEachTenant` (converted to the v2 background primitive, #6398). The expectations differ:

- **Not yet converted** (`@Transactional` / raw `TransactionTemplate`): flag it per `reduce-tx-baseline/SKILL.md`, not this step.
- **Converted** (`TenantScopedTransaction`): the scope IS carried correctly for every read against a v2-active table — that part needs no `TenantContext` at all, since reads go through the `TxCtx`/GUC/inspector path, not `@Filter`. The remaining question is whether `TenantContext.setCurrentTenant()` inside the block is still doing real work or is leftover from before the conversion:
  - Does the block **persist/save any `TenantBase` entity** (`.save(`, `.saveAll(`, `.persist(`, or a service method that does)? `TenantBaseListener.@PrePersist` stamps `tenant_id` from `TenantContext.getCurrentTenant()` — this is v1 attribution machinery that v2 activation does **not** replace (v2 only changes how reads are filtered, not how inserts are attributed). If yes → `TenantContext.setCurrentTenant()`/`clearCurrentTenant()` (in a `finally`) is REQUIRED. Do not suggest removing it; flag its absence as 🔴 CRITICAL instead (rows would be attributed to `Tenant.DEFAULT_TENANT_UUID` on the ambient fallback).
  - Does the block call into any code that is **not yet v2** (a legacy `io.openaev.rest` service, an entity still governed by `@Filter`, or anything reading `TenantContext.getCurrentTenant()` directly)? If yes → keep it, same reasoning.
  - If the block is **read-only** and every table it touches is v2-active (cross-check against `openaev.tenant.active-tables`, Step 2b) and it calls no legacy/v1 code path → `TenantContext.setCurrentTenant()` is redundant: the `TxCtx` passed to `TenantScopedTransaction.execute(...)` already carries the scope the reads need. Flag it as 🟡 MEDIUM — suggestion to remove the dead v1 call, progressing the job fully onto v2, but call this out explicitly as a suggestion, not a blocker, and require the reviewer to confirm both bullets above before recommending removal (a false positive here reintroduces the DEFAULT_TENANT_UUID attribution bug).

For a job NOT yet converted to `TenantScopedTransaction` (still async/`@Scheduled` raw):
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
