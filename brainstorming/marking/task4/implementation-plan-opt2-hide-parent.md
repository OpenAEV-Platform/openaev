# Task 4 — Option 2 (hide parents) with C-2: Implementation Plan

**Design doc**: [`tech-design.md` §2](./tech-design.md#2--option-2-hide-parents). This plan implements
variant **C-2** (read-time check through SQL functions, a new `DerivedMarkingDimension`, no new column on
any parent table).

**Depends on**: [Task 3](../task3/tech-design.md). Already on `main`: `assets.marking_ids`,
`is_marking_set_allowed`, `MarkingDimension`, `ScopeStatementInspector`, `TenantScopedTransaction`'s
system clearance for background jobs.

**Branch**: `task4-poc-option2`, created from `main`. It does **not** contain Option 1's code (no
`launched_by` / `scheduled_by`, no per-asset dispatch filtering).

**Status**: 🔴 not started.

---

## POC — Hide an Atomic Testing, and its findings, when it targets a restricted asset

### 1) Use cases (acceptance criteria)

Actors:

- `ADMIN`: bypass, sees every marking.
- `USER_GREEN`: member of a group cleared for `TLP:GREEN` (which expands to `TLP:CLEAR` + `TLP:GREEN`).
  Has the capabilities to access and launch Atomic Testing.

**US1: an Atomic Testing holding a restricted asset is hidden, with its findings.**

1. `ADMIN` creates an Atomic Testing targeting `ASSET_GREEN` (`TLP:GREEN`) and `ASSET_RED` (`TLP:RED`), and
   launches it. The run produces findings on both assets.
2. `ADMIN` sees the Atomic Testing in the list and its detail page, and sees **all** its findings: on the
   Findings page, on the Atomic Testing's findings tab, and on each endpoint's findings.
3. `USER_GREEN`:
   - **does not see** the Atomic Testing in the Atomic Testing list, the count, or search;
   - gets `404` on its direct URL / API;
   - **does not see any finding of that run** on the Findings page, the findings-by-inject endpoint, or
     `ASSET_GREEN`'s findings. That includes findings on `ASSET_GREEN`, because they belong to a hidden
     run;
   - cannot launch, relaunch, update, duplicate or delete it (`404`).

**US2: an Atomic Testing holding only visible assets is visible and launchable.**

1. An Atomic Testing targets only `ASSET_GREEN` (`TLP:GREEN`), optionally with unmarked assets too.
2. `USER_GREEN` sees it in the list and its detail page, launches it, and the run executes on every target.
   They see its findings.

### 2) Scope

**In scope:**

- `DerivedMarkingDimension`: a third `ScopeDimension` next to `TenantDimension` and `MarkingDimension`.
  Its tables are declared in a registry, not discovered from the schema.
- SQL functions `can_see_asset_group`, `can_see_inject`, `can_see_finding`, created by Flyway.
- Two derived tables: `injects` (guarded to **root injects only**, i.e. Atomic Testing, see D2) and
  `findings`.
- Integration tests for US1 / US2, and an execution regression test (the admin's mixed run still executes
  on every target).

**Out of scope (later steps, see §5):**

- **dynamic asset groups** (C-3): ruled out of this initial PoC by decision D8, to be done right after it;
- Scenarios and Simulations;
- `injects_expectations` / `execution_traces` / attack-path tables;
- Elasticsearch (dashboards);
- scheduled-run gate;
- frontend changes (none needed: hidden rows simply don't come back).

### 3) Decisions and constraints

| # | Decision | Why |
|---|---|---|
| D1 | **No new column.** Visibility is computed at read time by SQL functions that follow links down to `assets.marking_ids` | C-2 as designed; `assets.marking_ids` keeps its Task 3 meaning |
| D2 | **The `injects` predicate is guarded to root injects for this PoC.** `t.inject_scenario IS NOT NULL OR t.inject_exercise IS NOT NULL OR can_see_inject(t.inject_id)` | Hiding scenario / simulation injects without hiding their scenario / simulation would show half a scenario. They join when `scenarios` / `exercises` are registered |
| D3 | **Findings use the full end-state rule:** hidden if their inject holds a restricted asset (**any** inject, not only atomic testing) **or** any of their own assets is restricted | Safe side: never shows a finding derived from a restricted target. Accepted PoC inconsistency: a finding of a mixed *simulation* is hidden while the simulation itself is still visible (to confirm) |
| D4 | **Functions are real database functions** (`LANGUAGE sql STABLE`), never SQL inlined into the rewritten query | The inspector does not rewrite function bodies, so `assets` read inside them is **not** filtered by the caller's clearance. That is what lets the function *find* `ASSET_RED` for `USER_GREEN`. Inlined SQL could be filtered and would silently answer "nothing restricted" |
| D5 | **A link registry declared in Java** (`DerivedMarkingRegistry`), plus an activation allowlist `openaev.marking.derived-tables` (empty by default, inert unless `MARKING` is enabled) | Same rollout model as `openaev.marking.active-tables`; the registry holds *why* a table is filtered (tech-design §2.3) |
| D6 | **`writePredicate` = `readPredicate`; no write attribution** | An UPDATE / DELETE on a hidden atomic testing from a user transaction affects 0 rows, consistent with the `404` on read |
| D7 | **Background work keeps seeing everything.** Execution reads run through `TenantScopedTransaction`, which sets system clearance (`MarkingCtx.all()` → every marking of the tenant) | `InjectHelper.getInjectsToRun()` already does this. Any background reader that does **not** go through the primitive gets `MarkingCtx.none()` and would silently drop hidden atomic tests: these must be inventoried (step O2.4) |
| D8 | **Dynamic asset groups are out of scope for this initial PoC.** Only direct targets and static group members count; C-3 follows as the next step | Keeps the first PoC to pure C-2 (no write-side maintenance). The resulting gap is known and documented (§5); the PoC's tests and demo use direct targets and static groups only |

### 4) Delivery steps (test-first)

#### Step O2.1 — Acceptance tests first (red)

New `AtomicTestingMarkingHideParentTest extends IntegrationTest`. It follows
`AssetMarkingIsolationTest`'s pattern, with
`@TestPropertySource(properties = {"openaev.marking.active-tables=assets", "openaev.marking.derived-tables=injects,findings"})`.

- **Seed:**
  - `TLP:GREEN` / `TLP:RED` marking definitions;
  - a group cleared for `TLP:GREEN` with `USER_GREEN` as a member, plus atomic testing access/launch
    capabilities;
  - `ASSET_GREEN` (`TLP:GREEN`), `ASSET_RED` (`TLP:RED`), `ASSET_PLAIN` (unmarked);
  - `AT_MIXED` targeting green and red, and `AT_GREEN` targeting green and plain;
  - findings attached to each run's inject and assets. They are written the way execution writes them
    (`FindingWriter` / repository), since tests have no real agent.
- **`@Nested US1_HiddenAtomicTesting`**:
  - list and search exclude `AT_MIXED` and the count matches;
  - direct `GET` → `404`;
  - launch / relaunch / update / duplicate / delete → `404`;
  - the Findings page, findings-by-inject and findings-by-endpoint(`ASSET_GREEN`) contain none of
    `AT_MIXED`'s findings;
  - `ADMIN` sees everything.
- **`@Nested US2_VisibleAtomicTesting`**: `USER_GREEN` lists, opens and launches `AT_GREEN`, and sees its
  findings.
- **`@Nested Boundaries`** (direct targets and static groups only, per D8):
  - a user with no clearance sees only atomic tests with no marked target;
  - a `TLP:RED`-cleared user sees both;
  - a finding on `ASSET_RED` produced by a *simulation* inject is hidden from `USER_GREEN` (D3);
  - the response for a hidden id is identical to a non-existent id (no `403` vs `404` oracle, including
    through `@AccessControl` on launch).

**DoD**: the tests compile and fail for the right reason (atomic test and findings still visible).

#### Step O2.2 — SQL functions (Flyway migration)

`V6_<timestamp>__Add_derived_marking_functions.java`, modeled on
`V6_20260921090000000__Add_is_marking_set_allowed_function.java`:

```sql
-- static members only in this PoC (C-2); dynamic members are C-3
CREATE OR REPLACE FUNCTION can_see_asset_group(gid varchar) RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT NOT EXISTS (
    SELECT 1 FROM asset_groups_assets aga
    JOIN assets a ON a.asset_id = aga.asset_id
    WHERE aga.asset_group_id = gid AND NOT is_marking_set_allowed(a.marking_ids))
$$;

-- an inject is visible if none of its target assets, and none of its target groups, is restricted
CREATE OR REPLACE FUNCTION can_see_inject(iid varchar) RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT NOT EXISTS (
           SELECT 1 FROM injects_assets ia
           JOIN assets a ON a.asset_id = ia.asset_id
           WHERE ia.inject_id = iid AND NOT is_marking_set_allowed(a.marking_ids))
     AND NOT EXISTS (
           SELECT 1 FROM injects_asset_groups iag
           WHERE iag.inject_id = iid AND NOT can_see_asset_group(iag.asset_group_id))
$$;

-- a finding is visible if its inject is visible and none of its own assets is restricted (D3)
CREATE OR REPLACE FUNCTION can_see_finding(fid varchar, iid varchar) RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT (iid IS NULL OR can_see_inject(iid))
     AND NOT EXISTS (
           SELECT 1 FROM findings_assets fa
           JOIN assets a ON a.asset_id = fa.asset_id
           WHERE fa.finding_id = fid AND NOT is_marking_set_allowed(a.marking_ids))
$$;
```

All link columns are already indexed (`injects_assets(inject_id)`, `injects_asset_groups(inject_id)`,
`asset_groups_assets(asset_group_id)`, `findings_assets(finding_id)`, `findings(finding_inject_id)`).

**DoD**: function-level tests run the functions directly after `set_config('app.current_markings', …)`.
Cover:

- empty clearance;
- green only;
- red;
- a target through a static group;
- a finding with no inject;
- a finding on a red asset of a green inject.

#### Step O2.3 — `DerivedMarkingDimension`, registry and configuration

- **`DerivedMarkedTable(table, predicateTemplate)`** with `DerivedMarkingRegistry` holding the PoC
  entries:
  - `injects` → `(%1$s.inject_scenario IS NOT NULL OR %1$s.inject_exercise IS NOT NULL OR can_see_inject(%1$s.inject_id))` (D2)
  - `findings` → `can_see_finding(%1$s.finding_id, %1$s.finding_inject_id)`
- **`DerivedMarkingDimension implements ScopeDimension`**:
  - `name() = "derived-marking"`;
  - `activeTables()` = registry ∩ `openaev.marking.derived-tables`, only when `MARKING` is enabled;
  - `readPredicate` = `writePredicate` = the template applied to the alias;
  - no write attribution.
- **`DerivedMarkingFilteringConfig`**:
  - builds the dimension;
  - reuses `MarkingFilteringConfig.isMarkingFeatureEnabled` (extract it to a shared helper);
  - **fails at startup** when a listed table has no registry entry, or when a function the registry uses
    does not exist (`pg_proc`).
- **`ScopeFilteringConfig`**: `List.of(tenantDimension, markingDimension, derivedMarkingDimension)`.

**DoD**:

- `DerivedMarkingDimensionTest`:
  - predicates per table;
  - inert when the flag is off or the allowlist is empty;
  - unknown table → startup failure.
- Inspector rewrite tests, modeled on `MarkingRewriteHypothesisTest`, for the shapes actually used on
  these tables:
  - primary `FROM injects`;
  - `LEFT JOIN injects` from `findings`;
  - `WHERE f.inject.id = :id`;
  - the findings upsert (`ON CONFLICT DO UPDATE`);
  - `UPDATE injects …`.
- `MarkingCoexistsWithTenantV1Test` extended: `injects` is still on tenant v1 `@Filter`, and the derived
  predicate must AND with it.

#### Step O2.4 — Code-path inventory and regression with the tables active

Activate `openaev.marking.derived-tables=injects,findings` in the test `application.properties` (as
`assets` already is), then:

1. **Run the full backend suite.** A query shape on `injects` / `findings` that the inspector does not
   support now throws `TenantFilteringException` (fail-closed), so every one must be found here: native
   `UPDATE … FROM`, `DELETE … USING`, CTEs.
2. **Check every entry point that reads these tables.** It must be `@Transactional` **and** take `TxCtx`.
   Otherwise the marking GUC is never written, the caller (admin included) runs with `MarkingCtx.none()`,
   and atomic tests holding *any* marked asset disappear for them. `AtomicTestingApi`, `FindingApi` and
   `FindingSearchApi` already take `TxCtx`. Inventory the rest: inject APIs, exports, imports, `@AccessControl`
   resource lookups, mappers that lazy-load an inject.
3. **Check every background reader.** It must run through `TenantScopedTransaction` (D7):
   - inject execution and status updates (`InjectsExecutionJob` → `InjectHelper`, `InjectExecutionStep`,
     `BatchingInjectStatusService`);
   - expectation processing;
   - `FindingWriter`;
   - `AtomicTestingExecutionJob` (scheduled relaunch).

**DoD**:

- the suite is green with both tables active;
- the inventory table (path → `TxCtx`/primitive ✅/❌ → fix) is added to this document;
- every ❌ is fixed or explicitly accepted.

#### Step O2.5 — Make US1 / US2 green, plus the execution regression

- The O2.1 tests turn green.
- **Execution regression** (`InjectsExecutionJobTest`-style): `ADMIN` launches `AT_MIXED`, the job runs, and
  the inject is dispatched to **both** assets; findings are written for both. This proves hiding the
  atomic test from `USER_GREEN` never hides it from the system.
- **US2 execution**: `USER_GREEN` launches `AT_GREEN`, and it is dispatched to all its targets.

**DoD**: all green, with no regression in `AtomicTestingApiTest`, `FindingApiTest`, `FindingSearchApiTest`
and `AtomicTestingServiceTest`.

#### Step O2.6 — Performance check

Measure with the derived dimension on and off, on a seeded dataset (e.g. 5k atomic tests, 100k findings,
10% of assets marked):

- atomic testing search, page and count;
- Findings page (aggregated);
- findings by endpoint.

Cheap shortcuts to evaluate if needed:

- skip the predicate when the caller's clearance holds every marking of the tenant (admin / bypass);
- skip it when the tenant has no marked asset.

**DoD**: numbers recorded here; a decision on the shortcuts.

#### Step O2.7 — End-to-end (Playwright), optional for the PoC

`openaev-front/tests_e2e/tests/marking/marking-hide-atomic-testing.spec.ts`:

- set up US1 through the API;
- as `USER_GREEN`, assert the atomic test is absent from the list and the Findings page;
- assert a generic not-found on its URL;
- as `ADMIN`, assert both are visible.

This needs a second authenticated session (no multi-user fixture exists in the suite yet).

### 5) Known gaps and next steps (not this PoC)

| Gap | Risk on this branch | Next step |
|---|---|---|
| **Dynamic asset groups** (ruled out by D8, first follow-up). `can_see_asset_group` only checks static members | An atomic test targeting a dynamic group that matches `ASSET_RED` stays visible to `USER_GREEN`, and **can be launched by them and executes on `ASSET_RED`**: this branch has no dispatch-time filtering | C-3: `asset_group_marked_dynamic_members`, maintained in Java with system clearance for marked assets only |
| Scenarios and Simulations | not hidden; their injects are not filtered (D2) | register `scenarios`, `exercises` (children link → `injects`) and drop the root-only guard |
| `injects_expectations`, `injects_statuses` / `execution_traces`, attack-path tables | reachable through direct reads (e.g. expectations by agent) | register them (parent link → `injects`, plus their own asset / agent link) |
| Elasticsearch (dashboards, `EsInject`, `EsFinding`) | dashboards still count hidden runs and findings | marking set computed at indexing time |
| Scheduled relaunch | a recurrence configured by `USER_GREEN` keeps firing after `ADMIN` adds `ASSET_RED` (system clearance, no user) | all-or-nothing gate on the schedule owner's clearance (tech-design §2.4) |

### 6) Validation matrix

To be filled as steps complete.

### 7) Traceability

- **US1 / US2 above**: Task 4 "Atomic testing with visible and restricted assets", answered with Option 2
  (hide the entity), user-stories Row 2 Option 1 / Q2 (d).
- Findings visibility: user-stories Q4, answered for Option 2 by D3.
