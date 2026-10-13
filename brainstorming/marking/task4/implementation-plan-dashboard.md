# Task 4 — Markings, grants and capabilities in dashboards: Implementation Plan (POC)

**Design doc**: [`tech-design-dashboard.md`](./tech-design-dashboard.md) — Part 2 (requirements M/G/C/X)
and Part 3 (security decided in SQL, the index filtered by ids at query time).

**Depends on**: Task 2 (`MarkingClearanceCacheManager`), Task 3 / Task 4 (asset markings,
`is_marking_set_allowed`), the tenant filter of the engine (`tenantFilter(TxCtx)`, #8162).

**Status** (2026-10-09): steps 1–6 done on es8, step 7 partly done, steps 8–9 not started. es9 and
OpenSearch accept the scope but ignore it until step 9 (today's behaviour, unfiltered).

---

## 1) Scope of the POC

Prove the approach end to end on **one engine**, then port it.

**In scope**

- The three dimensions of Part 3: **capability** per indexed type, **grant** per scenario /
  simulation / atomic testing, **marking** per asset.
- One engine: **es8** (`engine.engine-selector=elk`, the default). es9 and OpenSearch are ported once
  the POC is validated (step 9).
- **Every API that reaches `EngineService`** (inventory below), so no engine read stays unfiltered.
- Requirements targeted: **M1, M2, M3, M5, M6, M7**, **G1, G2, G4, G5**, **C1, C3**, **X3**.

**Endpoints in scope.** Each also exists without the tenant prefix (`/api/…`); paths below are the
tenant-prefixed form `/api/tenants/{tenantId}/…`.

| Endpoints | Controller | Engine call (via) |
|---|---|---|
| `POST dashboards/{count,average,series,entities,entities-runtime}/{widgetId}` | `DashboardApi` | `DashboardService` → `count` / `average` / `multiTermHistogram` / `multiDateHistogram` / `entities` |
| `POST dashboards/attack-paths/{widgetId}` | `DashboardApi` | `DashboardService` → `EsAttackPathService` → `entities` + `multiTermHistogram` |
| `GET dashboards/search/{search}` | `DashboardApi` | `DashboardService` → `search` |
| `POST dashboards/adhoc/{series,count,average,entities,entities-runtime}` | `DashboardApi` | `DashboardService` (ad-hoc: widget configuration sent by the client) |
| `POST tenant-settings/home-dashboard/{count,average,series,entities,entities-runtime,attack-paths}/{widgetId}` | `TenantSettingsApi` | `CustomDashboardTenantService` → `DashboardService` |
| `POST scenarios/{scenarioId}/dashboard/{count,average,series,entities,entities-runtime,attack-paths}/{widgetId}` | `ScenarioDashboardApi` | `CustomDashboardService` → `DashboardService` |
| `POST exercises/{simulationId}/dashboard/{count,average,series,entities,entities-runtime,attack-paths}/{widgetId}` | `ExerciseDashboardApi` | `CustomDashboardService` → `DashboardService` |
| `GET attack_patterns/coverage` | `AttackPatternCoverageApi` | `AttackPatternService.getGlobalCoverage` → `multiTermHistogram` |
| `POST snapshot/{attack,vulnerability}-observations/search` (dev feature `BULK_SNAPSHOT_EXPORT`) | `SnapshotObservationApi` | `SnapshotObservationService` → `searchCursorPaged` |

**Related, but not an engine read:** `GET custom-dashboards/{customDashboardId}` (and the home /
scenario / simulation `…/dashboard` GETs) return the dashboard **definition** from SQL: its widgets
and their configuration. The page then calls the endpoints above for the data. They need no engine
filter, but widget filters can hold ids chosen by the author (e.g. an asset id): to check whether a
widget configuration pointing at a hidden asset reveals anything.

Step 6 (plumbing) and step 7 (tests) cover every row of this table.

**Out of scope (listed, not built)**

- Decisions still open with Soumaya: **M4** (multi-asset finding: strict or permissive — the POC
  takes strict, the natural result of a deny-list), **G3** (threat arsenal), **C2** (full
  capability table per index — the POC covers asset, finding, security platform and the
  scenario/simulation family), **C4** (capability of the home dashboard).
- **X1** (response field minimization) and **X2** (`base_tenant_side` in responses).
- Indexing: **no change**, no new field, no reindex. That is the point of the approach.

---

## 2) Confirmed decisions and constraints

- The security decision stays in **SQL and `PermissionService`**. The engine only receives id lists
  (types, granted ids, hidden asset ids): no copy of the rules in ES queries.
- The scope is computed **per request**, from current data, so a marking, grant or role change
  applies on the next request (M5, M6, G5) and the history of past simulations is covered.
- **Fail closed**: a missing scope means "no per-user filter possible" → only the tenant filter would
  apply, so the engine must refuse a read without a scope rather than run it unfiltered.
- The new filters sit **next to `tenantFilter`** in `buildQuery`, the single function every read goes
  through (`ElasticService.java:301-339`).
- The scope object lives in `openaev-model` (the engine is there); the resolver lives in
  `openaev-api` (it needs `PermissionService` and the clearance cache).

---

## 3) Delivery steps

#### Step 1 — Failing tests first ✅ done

Engine integration tests, in the style of `EngineServiceIntegrationTest` "Tenant scope (#6462)":
index a small dataset (two scenarios S1/S2 with a simulation each, an unmarked asset, an asset marked
`TLP:RED`, findings and expectations on both) and query it through `EngineService` as:

- a `TLP:GREEN` user with every capability → nothing of `ASSET_RED` (M1, M2), parents still visible
  without `ASSET_RED` in their "by asset" buckets (M3);
- a user without `ACCESS_ASSESSMENT`, granted on S1 only → only S1 data (G1);
- a user without `ACCESS_ASSETS` → no asset document, no asset label (C1);
- an admin → everything (M7, G4).

**DoD**: the tests compile and fail for the expected reason (today every user sees everything).

#### Step 2 — Registry: index → resource type, asset fields, grant keys ✅ done

A static registry (`EngineSecurityRegistry`, next to `MarkingDerivedTables` in spirit) describing, for
each indexed type:

| Index | Resource type (capability) | Asset reference field(s) | Grant keys (field → grant resource type) |
|---|---|---|---|
| `asset` | `ASSET` | `base_id` | — |
| `security-platform` | `SECURITY_PLATFORM` | `base_id` | — |
| `vulnerable-endpoint` | `ASSET` + `FINDING` (to confirm) | `vulnerable_endpoint_id` | `base_scenario_side` → SCENARIO, `base_simulation_side` → SIMULATION |
| `finding` | `FINDING` | `base_endpoint_side` | scenario, simulation sides |
| `expectation-inject` | parent (SCENARIO / SIMULATION / ATOMIC_TESTING) | `base_asset_side` | scenario, simulation sides; inject id → ATOMIC_TESTING |
| `inject` | parent | — (targets in `base_assets_side`, stripped, step 5) | scenario, simulation sides; `base_id` → ATOMIC_TESTING |
| `scenario` | `SCENARIO` | — (`base_assets_side` stripped) | `base_id` → SCENARIO |
| `simulation` | `SIMULATION` | — (`base_assets_side` stripped) | `base_id` → SIMULATION, scenario side → SCENARIO |
| `asset-group`, `tag`, `team`, `domain`, `attack-pattern` | `ASSET_GROUP`, open types | — | — |
| snapshot observations | `SNAPSHOT_OBSERVATION` | `base_asset_side` | scenario, simulation sides |

Exact field names to confirm per `Es*` class while writing it.

**DoD**: a unit test fails if a `Handler` bean's index is missing from the registry, or if a field
named in it does not exist on the `Es*` class.

#### Step 3 — The scope object and its resolver ✅ done

- `EngineReadScope` (`openaev-model`): `unrestricted` flag (admin / BYPASS), readable `base_entity`
  values, grant filter per index (field → allowed ids), hidden asset ids.
- `EngineReadScopeResolver` (`openaev-api`), built from the current user and the request's `TxCtx`:
  - **capability**: for each index of the registry, `PermissionService` READ on its resource type;
  - **grant**: when the capability is missing and the type is grant-managed, the user's grants of
    the matching `Grant.GRANT_RESOURCE_TYPE` (`RawUserAuth.getUser_grants()` already carries it);
  - **marking**: `SELECT asset_id FROM assets WHERE marking_ids <> '{}' AND NOT
    is_marking_set_allowed(marking_ids)`, run inside a transaction scoped to the user's clearance (the
    same SQL function as the REST API);
  - cached per (user, tenant), evicted with the clearance cache and on asset marking changes
    (`AssetMarkingsService`).

**DoD**: unit tests per dimension (admin, BYPASS, `ACCESS_ASSESSMENT`, grant-only, no capability,
`TLP:GREEN` vs `TLP:RED`); an asset re-marked RED appears in the hidden list on the next call.

#### Step 4 — Filters in `buildQuery` (es8) ✅ done

Next to `tenantFilter(ctx)`:

1. **capability**: `terms(base_entity, readable types)`;
2. **grant**: per grant-managed index, `(base_entity = X AND grant-key ∈ allowed ids) OR …`;
3. **marking**: `must_not terms(<asset field of X>, hidden ids)`, per index.

`buildQuery` refuses a missing scope (fail closed). Delete the commented-out `buildQueryRestrictions`
call and its "issue/3768" TODO.

**DoD**: step 1's document-level tests pass (M1, M2, G1, G2, C1, M7, G4).

#### Step 5 — Buckets, labels and returned documents ✅ done

What `buildQuery` does not cover:

- **aggregations** on asset-id fields (`base_assets_side`, `base_asset_side`, `base_endpoint_side`…):
  `exclude: <hidden ids>` on the terms aggregation;
- **labels**: `resolveIdsRepresentative` goes through `buildQuery`, so hidden assets do not resolve —
  check that the bucket itself is gone (previous point), not only its label;
- **returned documents**: strip hidden asset ids from every `*_side` array of `entities` results.

**DoD**: step 1's M3 test passes: a scenario targeting `ASSET_GREEN` and `ASSET_RED` shows only
`ASSET_GREEN` in its "by asset" breakdown and in its returned `base_assets_side`.

#### Step 6 — Plumbing the scope to every caller ✅ done

`EngineService` methods take the scope (or replace `RawUserAuth` by it). Callers to update:
`DashboardService` (stored widgets, ad-hoc), `CustomDashboardService` / `CustomDashboardTenantService`
(home, scenario and simulation dashboards), `EsAttackPathService`, `AttackPatternService` (coverage),
`SnapshotObservationService` (its own `grantFilter` replaced by the resolver): every row of the
endpoint table in §1.

**DoD**: no `EngineService` read is callable without a scope (compile-time); existing dashboard,
coverage and snapshot tests still pass.

#### Step 7 — Dashboard API tests and the two-browser check 🟡 partly done

- API-level tests, same users as step 1, on at least one endpoint per row of the §1 table
  (stored widget, ad-hoc, home, scenario and simulation dashboards, attack paths, search, coverage,
  snapshot export).
- Manual check, as in Chunk 2: a `TLP:GREEN` user and an admin side by side; the admin marks an
  asset RED → the GREEN user's widgets and `/adhoc` responses drop it on the next refresh; the admin
  removes the GREEN user's grant on S1 → S1 disappears on the next refresh.

**DoD**: tests green; manual check recorded here with its date.

#### Step 8 — Performance check 🔴 not started

On a restored main-environment dump: dashboard response times with and without the scope (home
dashboard, a large simulation dashboard, an expectation list), and the size of the hidden-asset list.

**DoD**: numbers recorded here; deny-list threshold decided (above it, fail closed for asset types).

#### Step 9 — Port to es9 and OpenSearch 🔴 not started

Same filters, buckets and stripping in `es9/ElasticService` and `os3/OpenSearchService`; run step 1's
tests against each engine.

**DoD**: the three engines pass the same tests.

---

## 4) Open questions (to close with Soumaya, see Part 2)

- **M4**: multi-asset findings, strict (POC default) or permissive.
- **G3**: are threat arsenal data shown in dashboards, and under which grant?
- **C2**: the capability required by each index (step 2's table is the proposal).
- **C4**: which capability opens the home dashboard and the `/adhoc` screens (separate GitHub issue).
- **X1 / X2**: field minimization and tenant id in responses, now or later.

---

## 5) What was built (2026-10-09)

- `openaev-model`: `engine/security/EngineReadScope` (the scope, plus `stripHiddenAssets`),
  `engine/security/EngineSecurityRegistry` (15 indexed types: capability, grant keys, asset fields).
  `EngineService` read methods take an `EngineReadScope` instead of `RawUserAuth`. es8 applies it in
  `buildQuery` (`readerFilter`), excludes hidden assets from "by asset" aggregations, strips them
  from returned documents, and applies the same filter to the snapshot export; a read without a scope
  is refused. The dead `buildQueryRestrictions` is gone from es8.
- `openaev-api`: `service/engine/EngineReadScopeResolver` (capabilities through `PermissionService`,
  grants from `RawUserAuth`, hidden assets from the same clearance as the SQL rewrite, per request,
  no cache). Callers: `DashboardService` (stored widgets, ad-hoc, search, attack paths),
  `AttackPatternService` (coverage), `SnapshotObservationService` (its `grantFilter` removed).
- Rule decided while building: a document linked to **no** grant-managed resource (none of its
  grant-key fields set) is not grant-filtered, as before (`must_not exists base_restrictions`).
- Tests: `EngineSecurityRegistryTest` (2), `EngineReadScopeResolverTest` (8),
  `EngineReadScopeIntegrationTest` (9, real Elasticsearch). Existing dashboard / engine / snapshot /
  coverage tests: 281 run, all green after one fix — `DashboardSearchApiTest` searched endpoints
  with a role holding only `ACCESS_DASHBOARDS`; its role now also has `ACCESS_ASSETS` (the leak C1
  closes).
- Not done: the two-browser manual check (step 7), performance on a dump (step 8), es9 / OpenSearch
  (step 9), the full test suite.

