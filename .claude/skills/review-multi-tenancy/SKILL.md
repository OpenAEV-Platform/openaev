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

## Step 2: No @Filter on tenant-scoped entities

No entity declares a Hibernate `@Filter` and `TenantFilterRemovedArchTest` fails the build when one
does (multi-tenancy rule 4). A `@Filter`, `enableFilter` or `disableFilter` in the diff is a build
failure to report, never a fix to suggest.

## Step 2b: Classify every touched table: gated or self-isolated

Before auditing anything else, classify each entity/table touched by the PR. The correct pattern (and
the correct severity for a missing check) depends on which one governs the table.

```bash
# the self-isolated tables, the only tenant tables the inspector leaves out
grep -n "SELF_ISOLATED" openaev-api/src/main/java/io/openaev/config/TenantTables.java

# does the touched entity/repository carry TxCtx-based scoping anywhere in the diff?
grep -rn "TxCtx\|TenantScopedTransaction\|RequireTenantSelector\|can_access_tenant" --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null
```

Classify each touched table as one of:
- **gated**: every tenant table (a `tenant_id` column) except the self-isolated ones. The inspector
  gates it at every start, with no property to turn it off. Isolation is enforced by
  `TenantStatementInspector` + `can_access_tenant`, scoped by a `TxCtx` parameter threaded through
  every `@Transactional` entrypoint that reaches it (directly, via another service, or via an
  association/join). For these tables:
  - every new/changed `@Transactional` method whose call graph reaches the
    table must carry a `TxCtx` param; missing it is 🔴 CRITICAL (silent
    empty-result regression, not a loud failure; see the `changeExerciseStatus`
    incident: a caller of the same EE-executor gate as two already-wired
    sibling endpoints shipped without `TxCtx` and silently bypassed the
    license check)
  - write/attribution endpoints (composite-PK lookups, row creation) need
    `@RequireTenantSelector` on the `TxCtx` param, not just its presence
  - a native `@Query` that `JOIN`s a gated table anywhere in the codebase
    (not just its own repository) is pulled into the fail-closed rewrite:
    check its FROM/JOIN shape against `TenantStatementInspectorTest` (#7007)
  - a new endpoint (in ANY controller) whose response type serializes a
    computed value derived from a gated table needs `TxCtx` too, even
    though nothing in the file names the table: `Inject#getType()`
    (`@JsonProperty("inject_type")`) resolves `injectors`, so every endpoint
    returning `Inject`, `InjectOutput`, `InjectResultOutput`,
    `InjectResultOverviewOutput` or `InjectTestStatusOutput` reads it. Missing
    `TxCtx` here is 🔴 CRITICAL and completely silent: 200 OK with the scalar
    `null` (#7605/#7621, missing injector icons across the Execution screen).
    Sweep by RESPONSE TYPE, not by API package.
  - `@Transactional(propagation = SUPPORTS)` + `TxCtx` is a false fix: with no
    inbound transaction the aspect never fires. Flag it 🟠 HIGH.
- **self-isolated**: a table of `TenantTables.SELF_ISOLATED_TABLES`, left out of the inspector with
  its reason there (e.g. an access pattern such as a native upsert with `ON CONFLICT ... RETURNING`
  that the inspector cannot rewrite). Every statement on it must carry its own explicit tenant
  predicate (`WHERE tenant_id = :tenantId` / `tenant.id IN :tenantIds`), since the inspector does not
  protect it. Missing predicate here is 🔴 CRITICAL: there is no fallback mechanism at all for this
  class of table. Model: `AttackPathGraphVersionRepository` (see its class javadoc).

## Step 2c — Enforce TxCtx argument position

For every method signature changed by the PR that carries `TxCtx`, verify that
`TxCtx` is the FIRST parameter (annotation allowed, e.g.
`@RequireTenantSelector TxCtx ctx`).

```bash
# PR-focused search for non-first TxCtx in signatures/calls
git diff -U0 HEAD~1 -- '*.java' | grep -n ',\s*[@A-Za-z0-9_ ]*TxCtx\b'
```

- Non-first `TxCtx` in a modified signature = 🟠 HIGH (review blocker for v2 scope consistency)
- Non-first `TxCtx` callsite for a modified API/service method = 🟡 MEDIUM (style drift; fix in same PR)

## Step 2d — Repository base interface for strict tenant-scoped tables

```bash
# List repos for strict tenant-scoped entities (TenantBase, not DualScopeBase)
# extending JpaRepository instead of CrudRepository
grep -rln "extends JpaRepository" --include="*.java" openaev-model/src/main/java/io/openaev/database/repository/
```

For each repository backing a gated or strict tenant-scoped entity, check
which base interface it extends:
- `CrudRepository` (the convention used across the codebase, e.g.
  `TagRuleRepository`, `DomainRepository`, `NotificationRepository`) exposes
  `findAll()` as `Iterable<T>` and has no `findAll(Sort)` or JPA batch/flush
  extras.
- `JpaRepository` additionally exposes `findAll(Sort)` (returns every row
  across every tenant, unbounded and unpaginated) plus `saveAndFlush`,
  `deleteInBatch`, `deleteAllInBatch`, `getReferenceById` — none of these take
  a tenant argument, so they are easy to call by accident and bypass the
  tenant scope entirely.
- Extending `JpaRepository` on a tenant-scoped repository = 🟡 MEDIUM
  (nitpick, non-blocking) unless the diff actually calls one of the
  JpaRepository-only methods without a tenant-scoped specification, in which
  case it is 🟠 HIGH (real bypass, not just an exposed footgun).

## Step 3 — Audit native queries

```bash
grep -rn "nativeQuery = true" --include="*.java" openaev-model/ openaev-api/
```

For each native query, first check Step 2b's classification of the table(s) it touches:
- table is gated → this is the #7007 FROM/JOIN-shape check, not the v1
  checklist below: verify the shape is in `TenantStatementInspectorTest`'s
  accepted list (a table-function FROM item needs `LATERAL`); a refused shape
  fails closed (`TENANT_FILTERING_REFUSED`) in production, not silently
- table is self-isolated → the checklist below applies:
  - Does it have `WHERE tenant_id = :tenantId`? ✅
  - No explicit tenant predicate? → 🔴 CRITICAL (multi-tenancy rule 1); a `#tenantContext` SpEL
    expression fails `V1TenantConstructArchTest`

Whatever the table's classification, also check what the join is **keyed on**. An id that
repeats across tenants by design (a business key whose uniqueness is composite with `tenant_id`, such
as an external id or a published catalog id) is not a tenant boundary: joining on it alone lets
another tenant's row in. `openaev-api/src/test/resources/shared-tenant-keys.txt` lists those keys.
A join on one of them must constrain both sides to the same tenant, or carry a reasoned entry in
`shared-tenant-key-join-waivers.txt`. 🟠 HIGH otherwise.

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
- **Converted** (`TenantScopedTransaction`): the transaction scope carries the tenant for reads (inspector) and for events built off a request (`TransactionTenantScope.pinnedTenant()`, when the scope pins one tenant). A `TenantContext.setCurrentTenant()` left in the block is a v1 leftover. It can go when the three conditions of [multi-tenancy › Background Threads Carry Their Transaction Scope](../../../.github/instructions/multi-tenancy.instructions.md#background-threads-carry-their-transaction-scope) hold: every row written names its tenant or inherits it from its parent, no code reached reads the ambient tenant, and the entity events it publishes take their tenant from a one-tenant transaction scope. Flag a removal that breaks one of them as 🔴 CRITICAL; flag a leftover that meets all four as 🟡 MEDIUM (suggestion).

For a job NOT yet converted (raw async or `@Scheduled`):
- Does it run each tenant's work through `TenantScopedTransaction` (or `TenantScopedJobRunner`, which sets the transaction scope only), with the tenant passed explicitly?
- Does every row it writes name its tenant or inherit it?

## Step 5b — Test code hygiene for v2

In API v2 activation tests and integration tests, do not add v1 scoping idioms.

```bash
# changed tests should not introduce v1 tenant context/filter primitives
git diff -U0 HEAD~1 -- 'openaev-api/src/test/java/**/*.java' \
  | grep -n 'TenantContext.getCurrentTenant\|TenantContext.setCurrentTenant\|enableFilter("tenantFilter")'
```

- Added/modified usage in v2/integration tests = 🟠 HIGH
- Preferred pattern: explicit tenant ids + `TxCtx` / `TenantScopedTransaction`

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
