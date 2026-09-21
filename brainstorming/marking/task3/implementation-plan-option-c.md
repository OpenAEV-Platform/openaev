# Option C — implementation plan

**Design doc**: [tech-design-option-c.md](./tech-design-option-c.md) — the mechanism and its rationale

**Parent doc**: [tech-design.md](./tech-design.md) — Option C

**Decision of record**: [ADR-009](../../../adr/ADR-009-Marking-based-access-control.md)

**Task 2 scope channel**: [`../task2/implementation-plan.md`](../task2/implementation-plan.md) — steps
2.1–2.4, relocated (see below)

**Status**:

- Steps 1.1–1.3 (inspector mechanism) delivered.
- 2.1–2.4 (Task 2, group clearance) delivered, see [Task 2](../task2/implementation-plan.md) (2.5,
  `ASSIGN_MARKING` capability, tracked there, not here).
- 3.1–3.3 (Task 3 mechanism PoC on `assets`) delivered.
- 3.4 API delivered but has **no UI**.
- 3.5's list column is delivered but filter/search and `ACCESS_MARKINGS` are not; the column and its fetch
  are now gated behind `MARKING` (Endpoints list — the leak found in review; the Assets list column was
  already gated).
- 3.6 (bulk assign) not started.
- 3.7 (ES/OpenSearch query-side filtering) not started — in scope for this plan, not go-live polish.
- 3.8 (definition delete: scrub + evict) and 3.9 (definition update: evict on order change) delivered.
  3.10 (eviction scope tightening) not started — found during review, deferred as pure optimization (not
  a correctness gap, since over-eviction never fails open).

**Feature flag**: every UI piece of 3.4/3.5/3.6 must render only behind `MARKING` (reused from Task 1,
default off) — see the note under each step.

## 1. Delivery plan

The 3 tasks (Task 1, Task 2, Task 3) are delivered behind the `MARKING` feature flag — already implemented
by Task 1 (default off, menu masking when disabled).

**Status at a glance**

| Step | | Commit |
|---|---|---|
| 1.1 `ScopeDimension` extraction | ✅ done | `4100dac` |
| 1.2 `MarkingDimension` + allowlist | ✅ done, reshaped by 1.3 | |
| 1.3 schema-shape spike → **Option 2 adopted** | ✅ done | `a0dcbd471c` |
| 2.1 `marking_definitions` schema + CRUD + UI | ✅ done | `3033c7759b` |
| 2.2 `MarkingCtx` + resolver + cache + eviction wiring | ✅ done | |
| 2.3 marking scope written on both transaction paths | ✅ done | |
| 2.4 `groups_markings` write path (assign endpoint + escalation guard) | ✅ done — lifted the step-3 gate | |
| 3.1 `assets.marking_ids` migration + GIN index | ✅ done | `6bfd3d2db8` |
| 3.2 activate `assets` on `openaev.marking.active-tables` | ✅ done | `6bfd3d2db8` |
| 3.3 escalation guard + declassification logging on the asset write path | ✅ done | `d9b216fffb` |
| 3.4 asset detail — assign a marking (US1) | 🔴 API done, **no UI** | `d9b216fffb` (API only) |
| 3.5 Assets list — column/filter/search (US2) | 🔴 **column done, gated behind `MARKING`**, filter/search + `ACCESS_MARKINGS` not done | `1da4aac904` (column only) |
| 3.6 Assets list — bulk assign (US3) | pending | |
| 3.7 ES/OpenSearch query-side filtering | pending | |
| 3.8 Definition delete: `marking_ids` scrub + cache eviction | ✅ done | |
| 3.9 Definition update: evict on `order` change | ✅ done | |
| 3.10 `TenantGroupService` eviction scope tightening | pending — deferred (optimization, not a correctness gap) | |
| 4 `activate-marking-table` skill — *the mechanism PoC's real deliverable* | pending | |
| 5 go-live hardening | out of scope for this plan | |

### Step 1 — Inspector mechanism (no task-story mapping — prerequisite plumbing for Task 3)

**1.1 — ✅ DONE** (commit `4100dac`) 

- Extract `ScopeDimension`; 
- Refactor `TenantStatementInspector` →
`ScopeStatementInspector`, tenant-only, zero behaviour change. *(deps: none)*

- **Context**: pure refactor; `TenantDimension` wraps today's `TenantTables` + `can_access_tenant` call.
  - **DoD**:
    - The entire existing tenant test suite is green, **unmodified**.
    - No marking test in this commit.
    - Land it on a clean base, before `TxCtx` is reshaped by 2.3, so a bisect stays readable.
  - **Delivered**:
    - `TenantStatementInspector` is now an 18-line subclass, so all existing wiring and the 79 tenant
      inspector tests are untouched.
    - `ScopeStatementInspectorTest` pins the composition contract.

**1.2 — ✅ DONE** *(deps: 1.1)*

- `MarkingDimension`
- `openaev.marking.active-tables` (empty) + `MarkedTable` schema derivation.

   - **DoD**:
     - With an empty allowlist, emitted SQL is byte-identical to before (pinned by test).
     - Unknown table name in the property fails startup.
     - **Plus the hypothesis test**: a fixture table + join table, GUC set manually via `set_config`,
       asserting unmarked-visible / in-clearance-visible / out-of-clearance-hidden / multi-marking-AND — no
       resolver, no product code, no Task 1 dependency.
   - **Delivered**:
     - `MarkedTable` / `MarkedTables` / `MarkingDimension` / `MarkingFilteringConfig`, plus
       `ScopeFilteringConfig` which now owns the single Hibernate inspector composed of both dimensions
       (`TenantFilteringConfig` only contributes the `TenantDimension` bean).
     - `MarkingDimensionTest` (12 cases) and `MarkingRewriteHypothesisTest` (6 cases on real rows, including
       a guarded UPDATE).
   - **Deviation from the sketch in §4.1**: derivation lives in its own `MarkingFilteringConfig` next to
     `TenantFilteringConfig` rather than in a single `ScopeFilteringConfig`; `ScopeFilteringConfig` is kept
     to the one thing that must be central — composing and installing the inspector.

**1.3 — ✅ DONE (commit `a0dcbd471c`) — schema-shape spike: Option 2 (`marking_ids text[]`) confirmed
over Option 1.** *(deps: 1.2, blocked 3 and 4 until it landed)*

§3.2 chose Option 2 on argument; 1.1–1.2 had validated Option 1 on evidence. This step existed to equalise
them **before** any table was activated, because activating on the wrong shape is what would have made the
choice expensive — that decision has now been made, on evidence, not on argument.

   - **Delivered**:
     - `is_marking_set_allowed(text[])` migration
       (`V6_20260921090000000__Add_is_marking_set_allowed_function`).
     - `MarkingDimension`'s `readPredicate` is the one-liner `is_marking_set_allowed(<alias>.marking_ids)`.
     - `MarkedTable` degenerated to `(table, markingColumn)` as sketched.
   - **DoD** — `MarkingRewriteHypothesisTest` (real rows, `<@`/containment predicate), all five criteria
     green:
     1. **truth table** — `TruthTable` nested class: in-clearance visible, out-of-clearance hidden,
        multi-marked row needs every marking, full clearance shows everything, UPDATE is guarded too.
     2. **fail-closed** — `FailClosed` nested class: GUC unset, GUC empty, `NULL` array and `'{}'` array all
        keep only unmarked rows visible.
     3. **fail-open trap pinned** — `FailOpenTrap.overlapAgainstLackedFormLeaks()`: proves the `&&`-on-lacked
        form leaks a row carrying a marking created mid-session, so the leaking form is a failing red line,
        not a plausible "optimisation" someone reaches for later.
     4. **composite-PK viability** — `CompositeKeys` nested class: the predicate applies unchanged to a
        two-column-PK fixture table, the property Option 1 could not deliver.
     5. **`EXPLAIN (ANALYZE, BUFFERS)`** — `IndexBehaviour` nested class: confirms the function is inlined
        by the planner and the containment form is index-eligible; recorded in §5.5.
   - **Exit taken**: all five held — Option 2 is confirmed.
     - `MarkedTable` is reshaped as above.
   - *Why this mattered as a gate*: this was the cheapest moment to be wrong.
     - After step 3 the cost would have included a data migration.
     - After step 4 it would also have meant rewriting the skill and every table activated through it.
     - Nothing downstream needs to re-litigate this; steps 3+ build on `marking_ids text[]` as settled
       ground.

### Step 2 — Scope channel — moved to Task 2 ✅ done

> Steps 2.1–2.4 (the group-clearance scope channel, including the `groups_markings` assign/unassign write
> path) are now
> documented in [`../task2/tech-design.md`](../task2/tech-design.md) and
> [`../task2/implementation-plan.md`](../task2/implementation-plan.md) — that is Task 2's own scope
> ("assign markings to groups")

### Step 3 — Assign markings to assets (Task 3, full scope) *(deps: 1.3, 2.3)*

`assets` is chosen first deliberately: it is the largest and most-joined of the three (§3.1 — Endpoint and
Security Platform share it), so it is the honest test of both the fail-closed blast radius (§5.1) and the
predicate cost (§5.5). If marking survives `assets`, the remaining tables are formalities. Per **Q10** it
does **not** need to be on tenant v2 first.

**3.1** — ✅ **DONE** (`6bfd3d2db8`) — Migration: `ALTER TABLE assets ADD COLUMN marking_ids text[]`

- A functional GIN index on the rewritten predicate expression (not the plain column — see the migration's
  javadoc for why a plain `GIN (marking_ids)` index can never match `COALESCE(...) <@ COALESCE(...)`).
- Mapped on `Asset` with `@Type(StringArrayType.class)`, reusing the pattern already used by `asset_ips` on
  the same entity.
- **No backfill** — a `NULL` array is inert, so every existing asset stays visible to everyone the
  moment the column appears.

**3.2** — ✅ **DONE** (`6bfd3d2db8`) — Activated `openaev.marking.active-tables=assets`.

- **Delivered**: `AssetMarkingIsolationTest`:
  - User cleared `TLP:GREEN` cannot read/search a `TLP:RED` endpoint (404, not 403).
  - Unmarked endpoint visible to all.
  - Multi-marking AND semantics.
  - Two users in different groups see different subsets.
  - A multi-group user gets the highest clearance.
  - Tenant + marking compose correctly.
  - Mutation-checked: with the dimension switched off, 5 of the 7 tests fail.
- *Covers US4, US5, US6, US8 — access control based on group marking, visibility, highest-marking-wins,
  marking as a first layer of access control.*

**3.3** — ✅ **DONE** (`d9b216fffb`) — `MarkingEscalationValidator` (built in **2.4**) wired into the asset
write path (`AssetMarkingsService.updateAssetMarkings`).

- **Delivered**:
  - The validator is called before any `marking_ids` write.
  - A `404` (not `403`) on an asset the caller cannot already read, so a caller cannot even probe for
    the asset's existence, let alone declassify it.
- **Self-lockout resolved by construction, not by a separate check**: the validator enforces
  `requested ⊆ your clearance`, and a row is visible iff `row_markings ⊆ clearance` — so the asset you
  just marked is still readable by you, with no extra invariant to maintain.
- **Declassification is logged**, not yet raised as a domain audit event:
  - The platform has no audit-event facility yet; `logDeclassification` is the single call site to move
    once one lands.
  - Only *removals* are logged, per §4.3 (adding a marking narrows access and needs no explaining;
    removing one widens it).
- **No cache eviction on an asset write, deliberately** — `is_marking_set_allowed(marking_ids)` re-reads
  the row's array as a query argument on every read; only the cached *clearance* GUC would need
  eviction, and nothing here changes a clearance.

**3.4 — 🔴 API done, UI not started — US1: assign a marking from the asset detail page.**

   - **Delivered (API only)**: `PUT /api/tenants/{t}/assets/{assetId}/markings` (`d9b216fffb`).
     - The write path for the row side of the model, on `/api/assets` rather than per-subtype so one
       endpoint marks every asset category (endpoint, security platform, AI target).
     - Replace-the-whole-set semantics, same as the sibling `groups/{id}/markings` endpoint.
     - An empty list clears every marking.
   - **Still missing**: no UI. There is no editable marking field on the asset detail/update form (Endpoint,
     Security Platform) — an admin can only assign/change/clear a marking via a direct API call today (see
     `task3/demo/mark-asset.sh`).
   - **Build (remaining)**:
     - An editable marking field on the asset detail/update form, a single-select (per §2.2's "collapse
       ordinality in Java" — the *stored* value is a single marking id per type, per the STIX
       `object_marking_refs` model already in §3 of `tech-design-option-c.md`), sourced from
       `marking_definitions` scoped to the current tenant.
     - Change/remove reuses the same field.
   - **Feature-flagged, like Task 1**: the field must render only when `isFeatureEnabled('MARKING')`
     (`SecurityMenu.tsx` / `settings.config.tsx` pattern) — with the flag off, the asset form must look
     exactly as it does today.
   - **Depends on**: the `ACCESS_MARKINGS` read-capability fix noted under 3.5 below — pulled forward here
     since without it the field cannot label its own options for non-admin users.
   - **DoD (remaining)**:
     - Component test for the form field (select, clear, disabled state on over-clearance).
     - Component test confirming the field does not render when `MARKING` is disabled.
     - The assigned marking is visible on the detail page immediately after save.

**3.5 — 🔴 column done and flag-gated, filter/search + `ACCESS_MARKINGS` not done — US2: marking column +
filter + search on the Assets list.**

- **Delivered**: a **Markings** column on the Endpoints list (`1da4aac904`).
  - `EndpointOutput` gains `asset_markings` (ids only, normalised from `null` to an empty set), rendered
    as a MUI chip per marking via the shared `ItemMarkings` component
    (`openaev-front/src/components/ItemMarkings.tsx`), wired into `Endpoints.tsx`.
  - The column is **not sortable** (markings are a `text[]` on the row, not a joinable column).
  - Per the design's read-side argument, exposing `asset_markings` leaks nothing — a row only reaches
    the caller if its markings are already a subset of their clearance.
- ✅ **Regression fixed**: `Endpoints.tsx` now calls `isFeatureEnabled('MARKING')` and gates both the
  column definition and the `useMarkingDefinitions` fetch (an optional `{ skip }` param was added to the
  hook so no `marking_definitions` request fires at all when the flag is off) — matching the
  `settings.config.tsx` gating pattern used for the Marking Definitions menu entry.
- **Still missing**: the column is display-only. There is no marking entry in the list's filter options
  and no marking term in the free-text search; a user cannot yet narrow the Assets list to "only
  `TLP:RED`" or "no marking".
- 🔴 **Open finding, not yet fixed**:
  - `MARKING_DEFINITION` read is currently bundled into `ACCESS_TENANT_SETTINGS`, which a `Manager` role
    does not hold, so **any list or form that needs to resolve `marking_ids` into names/colours for a
    non-admin** (this column, and 3.4's form field) is exposed to silently rendering empty/unlabelled for
    that role.
  - The fix — a dedicated `ACCESS_MARKINGS` capability (mirroring `ACCESS_TAGS`), granted wherever
    `ACCESS_TAGS` is granted — has not been built.
- **DoD (remaining)**:
  - `isFeatureEnabled('MARKING')` gate added around the column and its data fetch, with a component test
    asserting the column is absent when the flag is off.
  - Filter/search by marking, including "no marking" as a distinct filter value consistent with existing
    no-value column/filter behaviour (US2 AC5).
  - The `ACCESS_MARKINGS` capability and a `Manager`-role component/API test proving the column and
    filter resolve without `ACCESS_TENANT_SETTINGS`.
  - `yarn check-ts` / `yarn lint` on the touched list/filter components.

**3.6 — 🔴 pending — US3: bulk-assign a marking across a filtered selection.**

- **Build**:
  - A bulk-action entry ("Assign marking") on the Assets list, available once one or more assets are
    selected.
  - "Select all" must select **every asset matching the current filter**, not just the rendered page
    (US3 AC2) — the same all-matching-filter selection semantics used elsewhere in the platform for bulk
    actions, not a client-side "select visible rows" shortcut.
  - A single bulk action can span Asset Groups, Endpoints and Security Platforms together (US3 AC3).
- **Feature-flagged, like Task 1**: the bulk-action entry must render only when
  `isFeatureEnabled('MARKING')`, same as 3.4/3.5 — with the flag off, the bulk-action menu must have no
  "Assign marking" entry at all.
- Each asset in the batch goes through the **same** write guard as 3.3/3.4 — a bulk action is not a
  backdoor around the escalation check. Partial failure (e.g. one asset type lacks the caller's write
  capability) must be reported per-asset, not swallowed into a single pass/fail (US3 AC5).
- **DoD**:
  - API test — bulk-assign across mixed asset types, over a filtered set larger than one page.
  - Partial-failure test asserting a per-asset success/failure breakdown is returned, not a silent
    partial apply.
  - Confirmation summary shows the count actually updated.
  - Component test confirming the bulk-action entry does not render when `MARKING` is disabled.

**3.7 — 🔴 pending — ES/OpenSearch query-side filtering.** *(pulled in from Step 5 — this is in scope, not
go-live polish, because assets are already indexed into ES/OpenSearch for surfaces other than the
DB-backed Assets list search covered by 3.2/3.5.)*

- **Why this is a real gap, not a duplicate of 3.2**: `openaev.marking.active-tables` and the
  `MarkingDimension` statement inspector only rewrite **Hibernate-issued SQL**.
  Any read path that goes through the ES/OpenSearch index instead of the database bypasses the inspector
  entirely and sees the raw indexed document, marking or not.
- **Context**: per `tech-design-option-c.md` §4.1.3, the ES sync legitimately indexes every marked asset
  row — the index is not the thing to restrict — so `marking_ids` must be carried into the indexed
  document, and the **query side** must apply the same clearance filter the SQL inspector applies today.
- **Build**:
  - Confirm the current inventory of ES/OpenSearch-backed reads over `assets` (today's Assets/Endpoints
    list search is DB-backed per 3.2, so this is about any other read path — dashboards, global search,
    exports — that queries the asset index directly).
  - Add `marking_ids` to the indexed asset document.
  - Add the equivalent of the `<@` clearance-containment predicate to every ES/OpenSearch query over that
    index, sourced from the same resolved clearance set 2.2/2.3 already populate for the SQL path.
- **DoD**:
  - An isolation test mirroring `AssetMarkingIsolationTest` (3.2), but exercised through the ES/OpenSearch
    query path instead of the JPA/Hibernate path.
  - A test proving a marked asset is indexed with its `marking_ids`, and that an out-of-clearance query
    does not return it.
  - Mutation-checked, same discipline as 3.2: with the ES-side filter removed, the new test(s) fail.

**3.8 — ✅ done — Definition delete now scrubs `marking_ids` and evicts the cache (was a real bug, not
hardening).**

- **Delivered**: `MarkingDefinitionService` now injects `MarkedTables`, `MarkingClearanceCacheManager`
  and `JdbcTemplate` (class-level `@AllowRawJdbc`, justified below). `delete()` runs the scrub over
  `MarkedTables.tableNames()` after the row is removed, then calls `evictAll()`.
- **Tests**: `MarkingDefinitionServiceTest` (new, Mockito-based) pins: the scrub SQL runs once per
  marking-active table with the deleted id as both parameters, `evictAll()` is called exactly once, a
  protected definition throws before either side effect runs, and an empty `MarkedTables` (flag off)
  still evicts but issues no SQL. Existing `AssetMarkingIsolationTest` and
  `MarkingClearanceCacheManagerTest` continue to pass unmodified.

- **Why this is required now, not go-live polish**: `assets` is already activated (3.1/3.2), so
  `MarkingDefinitionService.delete()` is not a stale demo path — it is destructive today. Confirmed by
  reading the actual code, not assumed: neither the scrub nor `evictAll()` is called, contradicting the
  "What the PoC code does today" claim in `tech-design-option-c.md` §3.2 (written when no table was
  activated yet, never revisited after 3.2 shipped) — that section needs correcting once this lands.
- **Root cause**: Option 2 has no FK on `marking_ids` (§3.2), so nothing cascades into the array. Once the
  `marking_definitions` row is gone, the deleted id survives inside every row's array forever — and can
  never re-enter anyone's clearance, since `MarkingClearanceCacheManager.TENANT_MARKINGS_SQL` only ever
  returns ids that still exist. Every row carrying it becomes invisible platform-wide, permanently,
  including to admins — and it cannot be repaired through the API either: `updateAssetMarkings` reads the
  asset through `findByIdAndTenantId`, which the inspector already filters, so the asset 404s before the
  write is ever reached. Only direct SQL gets it back.
- `groups_markings` is **not** part of this gap — its FK already has `ON DELETE CASCADE`
  (`V6_20260921130000000__Add_groups_markings.java:33`), so the grants clean up correctly today.
- **PO decision**: hard delete must stay possible even while the marking is still assigned to rows or
  groups — the "archive instead of delete" mitigation in §3.2 is explicitly **not** the chosen path, so
  the scrub is mandatory, not optional.
- **Build**:
  - Inject `MarkedTables` (already a bean, `MarkingFilteringConfig.markedTables()`) into
    `MarkingDefinitionService`, and for every name in `MarkedTables.tableNames()` — the same
    schema-derived, allowlist-narrowed set the inspector itself filters against, so the scrub can never
    drift out of sync with which tables are actually marking-active — run:
    ```sql
    UPDATE <table> SET marking_ids = array_remove(marking_ids, ?) WHERE marking_ids @> ARRAY[?]::text[]
    ```
    The `@>` guard (§3.2 mitigation 2) lets the GIN index select candidate rows instead of a blind
    full-table rewrite; without it every row in every marked table gets rewritten regardless of whether
    it holds the id.
  - Call `markingClearanceCacheManager.evictAll()` after the delete commits — the definition's grants are
    gone from `groups_markings`, but any clearance already cached before the delete still contains the id
    until eviction (fails open for up to the 5-minute TTL otherwise).
  - Dynamic table name means this cannot be expressed through JPA/`Specification`; needs raw JDBC,
    `@AllowRawJdbc`-justified the same way as `MarkingClearanceCacheManager` (metadata-only /
    schema-driven, not an arbitrary query).
- **DoD**:
  - Test: delete a marking definition that is both referenced by an in-clearance asset's `marking_ids`
    and granted through a group's `groups_markings` row; assert the array no longer contains the id, the
    grant row is gone (already true today via cascade), and the previously-marked asset is now visible to
    every caller as unmarked — not vanished.
  - Mutation-checked: with the `evictAll()` call removed, a clearance cached before the delete must still
    resolve to the old (larger) set on the next call, proving the test would actually catch the
    regression.
  - Regression: `AssetMarkingIsolationTest` and `MarkingClearanceCacheManagerTest` continue to pass
    unmodified.

**3.9 — ✅ done — Definition-order update now evicts the clearance cache.**

- **Why this matters**: `MarkingScopeResolver` treats marking as ordinal per type (`TLP:AMBER` implies
  `TLP:GREEN` implies `TLP:CLEAR`) and expands the highest order a user was granted, per type, downward
  (`MarkingScopeResolver.resolve`). Lowering (or raising) a definition's `order` therefore changes what an
  already-cached clearance *should* expand to, for every user holding any grant on that type — not just
  one group's members, unlike the group-side changes 3.10 discusses.
- **Current gap**: `MarkingDefinitionService.update()` persists the new `order` but calls no eviction at
  all. This is a documented-but-never-wired gap, not a new design question: both
  `task2/tech-design.md:147` and `task2/implementation-plan.md:76` already call for `evictAll()` here —
  `MarkingDefinitionService` predates `MarkingClearanceCacheManager` (it shipped in Task 1, #7651, before
  the cache existed) and was never revisited when Task 2 added it.
- **Build**: call `markingClearanceCacheManager.evictAll()` in `update()` whenever
  `input.order() != existing.getOrder()` — guarded, so a definition save that only touches `color` or
  `definition` text does not pay for a full-cache blast for no reason.
- **DoD**: a test that lowers a definition's `order` and asserts a clearance cached beforehand is
  recomputed (not served stale) on the next call after the update, mutation-checked against removing the
  `evictAll()` call.
- **Delivered**: `update()` computes `orderChanged` before mutating the entity and calls
  `evictAll()` after `save()` only when it is true — a `color`/`definition`-only save no longer pays for
  a cache-wide blast. `MarkingDefinitionServiceTest` pins both the evict-on-order-change and the
  no-evict-when-unchanged cases.

**3.10 — 🔴 pending — `TenantGroupService` over-evicts across every tenant for a single-tenant change.**

- **Why this is safe today but worth tightening**: `MarkingClearanceCacheManager` exposes both a
  single-tenant `evict(userId, tenantId)` and a multi-tenant `evictForUser(userId)`/`evictForUsers(...)`.
  The latter deliberately walks every tenant the user belongs to (via
  `TenantMembershipCacheManager.findTenantIdsByUserId`) — necessary for `PlatformGroupService`, since a
  platform group is dual-scope and can grant markings across many tenants at once (see
  `task2/implementation-plan.md:68-72`).
  `TenantGroupService.updateGroupUsers`, `updateGroupMarkings` and `delete` are tenant-scoped by
  construction — they already resolve `TenantContext.getCurrentTenant()` or receive `tenantId` as a
  parameter — yet call the same broad `evictForUsers`, paying for an unnecessary
  `findTenantIdsByUserId` lookup plus evictions in every unrelated tenant the user happens to belong to.
  Over-eviction is not a correctness bug — it never fails open — just wasted work.
- **Build**: replace the three `TenantGroupService` call sites with a loop over the affected user ids
  calling `markingClearanceCacheManager.evict(userId, tenantId)` with the tenant already in scope, leaving
  every `PlatformGroupService` call site on `evictForUsers` unchanged.
- **DoD**:
  - Existing `TenantGroupMarkingsApiTest` and group tests continue to pass unmodified.
  - A new test asserting a tenant-group marking/membership change does **not** evict the user's cached
    clearance in an unrelated second tenant — proving the narrower call does what the broad one
    incidentally did, not less.

### Step 4 — Capture the procedure as an AI skill, then prove it *(deps: 3)*

Step 3 is the only time anyone will have the whole activation procedure in their head. Capture it
immediately, the way `activate-tenant-table` captured the tenant equivalent.

**4.1 — Write `.github/skills/activate-marking-table/SKILL.md`**, mirroring the phase structure of
`activate-tenant-table`:

| Phase | Content |
|---|---|
| **0 — Eligibility gate** | Table is not on the **clearance-resolution path** (`groups`, `users_groups`, `groups_markings` — filtering these makes the resolver depend on a clearance it is computing, failing closed for everyone; §3.3); no raw-JDBC **writers** (§5.7 — the FK no longer guards id validity) or raw-JDBC readers that would bypass the inspector. **No PK constraint** — Option 2 marks composite-PK and relationship tables unchanged. *(Under the Option 1 fallback this phase must additionally reject composite-PK tables.)* |
| **1 — Inventory: reads** | Every read path: repository methods, native `@Query` joins, ES/OpenSearch reads (§5.2 — these bypass the inspector entirely), background jobs (§5.3), raw-JDBC `@AllowRawJdbc` sites |
| **1b — Inventory: downstream** | Which tables hold an **FK to this table** *and* denormalise any of its content (e.g. `injects_expectations.asset_id` + `inject_expectation_name`), and which **native aggregates** read them. Marking propagates transitively along every write the system makes on a marked row, and each aggregate is a place where "filtered rows" and "one number" disagree (§5.8). Produce the inventory — do **not** activate those tables; the per-viewer-vs-system aggregate decision is deferred to the next epic. Far cheaper to trace while activating than afterwards |
| **2 — RED** | Write the isolation test first: unmarked-visible / in-clearance / out-of-clearance 404 / multi-marking AND |
| **3 — GREEN: migration** | `ALTER TABLE <table> ADD COLUMN marking_ids text[]` + GIN index; map with `@Type(StringArrayType.class)`. One statement, no FK, no cascade — and **nothing global to regenerate**. *(Under the Option 1 fallback: `<table>_markings` + `ON DELETE CASCADE` both sides + reverse index + `@ManyToMany` + regenerate `marking_usage`.)* |
| **4 — Activate** | Add the table to `openaev.marking.active-tables` |
| **5 — Write guard** | Wire `MarkingEscalationValidator` into the table's write paths (§4.3) |
| **6 — Regression** | Full tenant suite + marking suite; measure the marking predicate against a pre-activation baseline (§5.5) |

   - **DoD**:
     - The skill names the exact files, properties and test classes, and states its stop conditions.
     - Register it in `AGENTS.md`.

**4.2 — Prove the skill and the "cheap to extend" claim** — run it on `asset_groups`, then
`secret_references`.

   - **DoD**:
     - The *only* Java change per table is the migration class + the `@ManyToMany` mapping.
     - If any repository or service read code needs editing, Option C's core promise is broken → stop and
       re-evaluate.
     - `secret_references` is the valuable one: it is already tenant-v2-active, so it proves tenant and
       marking compose on a real table.

### Step 5 — Out of PoC, required for go-live

- **5.1** Background-job marking scope (§5.3).
- **5.2** Join-table exposure review (§5.7).
- **5.3** Decide and implement derived-data propagation + the aggregate semantics (§5.8).
- **5.4** Performance validation (§5.5).
- **5.5** **Un-skim the remaining Task 1/2 governance**: `@AccessControl` on the marking CRUD beyond what
  Task 2's `ASSIGN_MARKING` chain covers, and the separate decision on whether to marking-activate
  `groups_markings` itself, now that the resolver's bootstrap order is settled.
