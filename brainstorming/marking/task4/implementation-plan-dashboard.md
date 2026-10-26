# Task 4 — Markings, grants and capabilities in dashboards: Implementation Plan (POC)

**Design doc**: [`tech-design-dashboard.md`](./tech-design-dashboard.md):
- Part 1: how RBAC works today (Option B: The editor follows the parent);
- Part 2: how grants work today;
- Part 3: how dashboards work today;
- Part 4: the approach, security resolved in SQL with the filtered ids passed to Elasticsearch.

**Depends on**: Task 2 (`MarkingClearanceCacheManager`), Task 3 / Task 4 (asset markings,
`is_marking_set_allowed`), the tenant filter of the engine (`tenantFilter(TxCtx)`, #8162).

**Status** (2026-10-23):
- **Steps 1–6:** done on es8. **Step 7** (everything behind `MARKING`): done.
- **Step 8 (e2e matrix):** API tests partly done.
- **Steps 7a–7f:** done; **step 7g:** to decide (PR review); **steps 9–21:** not started (21 optional).
- **es9 and OpenSearch:** they accept the scope but ignore it until steps 11–12 (today's behaviour, unfiltered).
- **Branch `task4-poc-dashboard`:** rebased on `main` (2026-10-19) and compiles. The dashboard, engine, snapshot and coverage tests pass.

**What's left**

| Step | What | Blocked by |
|---|---|---|
| 8 | End-to-end tests: RBAC, grants, markings (matrix §4) | — |
| 9 | Performance measurement on a main dump → SLO | — |
| 10 | Asset labels in breakdowns ("deleted") | Soumaya: Option 1 or 2 |
| 11 | Port to es9, dashboards and OpenGRC export | steps 8, 10 |
| 12 | Port to OpenSearch, dashboards and OpenGRC export | steps 8, 10 |
| 13 | Full test suite and CI on the three engines | steps 11, 12 |
| 14 | ADR (with the SLO), user documentation, release note | step 9 |
| 15 | Bug: atomic testing edit form shows no target without `ACCESS_ASSETS` | — |
| 16 | Bug: "Add assets" offered without `ACCESS_ASSETS` | — |
| 17 | Bug: embedded findings rows link to a page needing `ACCESS_FINDINGS` | — |
| 18 | Bug: "Select dashboard" hidden for `MANAGE_ASSESSMENT` without a grant | — |
| 19 | Bug: asset posture data checked on `ACCESS_DASHBOARDS` | — |
| 20 | Bug: custom home dashboard needs `ACCESS_TENANT_SETTINGS` | — |
| 21 | *Optional* refactor: one tenant filter, from the scope | steps 11, 12 |

---

## 1) Scope of the POC

Prove the approach end to end on **one engine**, then port it.

**In scope**

- The three dimensions of Part 4:
  - **capability** per indexed type;
  - **grant** per scenario, simulation or atomic testing;
  - **marking** per asset.
- **One rule for every dashboard read** (§4.5). A dashboard attached to a scenario's or simulation's
  Statistics tab is filtered exactly like a custom dashboard. There is no context-dependent rule.
- One engine: **es8** (`engine.engine-selector=elk`, the default). es9 and OpenSearch are ported once
  the POC is validated (steps 11–12).
- **Every API that reaches `EngineService`** (inventory below), so no engine read stays unfiltered.

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

**Related, but not an engine read:** `GET custom-dashboards/{customDashboardId}` (and the home,
scenario and simulation `…/dashboard` GETs) return the dashboard **definition** from SQL: its widgets
and their configuration. The page then calls the endpoints above for the data. These GETs need no
engine filter. But widget filters can hold ids chosen by the author (e.g. an asset id): still to check
whether a widget configuration pointing at a hidden asset reveals anything.

Step 6 (plumbing) and step 8 (tests) cover every row of this table.

**Out of scope (listed, not built)**

- The decision still open with Soumaya, logged in the tech design (§4.5).
- Field minimisation in responses, and `base_tenant_side` returned to the client.
- Indexing: **no change**, no new field, no reindex. That is the point of the approach.
- The REST and UI bugs found while writing the design: they get their own issues (steps 15–20).

---

## 2) Confirmed decisions and constraints

- The security decision stays in **SQL and `PermissionService`**. The engine only receives id lists
  (types, granted ids, hidden asset ids): no copy of the rules in ES queries.
- The scope is computed **per request**, from current data. A marking, grant or role change applies
  on the next request, and the history of past simulations is covered.
- **Fail closed**: the engine refuses a read without a scope rather than run it with the tenant filter
  only.
- **One rule for every dashboard** (§4.5): the scenario or simulation only decides which dashboard is
  displayed, never what its widgets return. This also closes the parameter bug of the scenario and
  simulation dashboards, which could otherwise read another scenario's data (flag on; with the flag off it stays as on `main`).
- The new filters sit **next to `tenantFilter`** in `buildQuery`, the single function every read goes
  through.
- **Everything behind `MARKING`** (step 7): with the flag off, dashboards and the OpenGRC export
  behave exactly as on `main`.
- The scope object lives in `openaev-model` (the engine is there); the resolver lives in
  `openaev-api` (it needs `PermissionService` and the clearance cache).

---

## 3) Delivery steps

#### Step 1 — Failing tests first ✅ done

Engine integration tests, in the style of `EngineServiceIntegrationTest` "Tenant scope (#6462)". They
index a small dataset: two scenarios S1/S2 with a simulation each, an unmarked asset, an asset marked
`TLP:RED`, and findings and expectations on both. They query it through `EngineService` as:

- a `TLP:GREEN` user with every capability: nothing of `ASSET_RED`, parents still visible without
  `ASSET_RED` in their "by asset" buckets;
- a user without `ACCESS_ASSESSMENT`, granted on S1 only: only S1 data;
- a user without `ACCESS_ASSETS`: no asset document;
- an admin: everything.

**DoD**: the tests compile and fail for the expected reason (today every user sees everything).

#### Step 2 — Registry: index → resource type, asset fields, grant keys ✅ done

A static registry (`EngineSecurityRegistry`, next to `MarkingDerivedTables` in spirit) describing, for
each indexed type:

| Index | Resource type (capability) | Asset reference field(s) | Grant keys (field → grant resource type) |
|---|---|---|---|
| `asset` | `ASSET` | `base_id` | — |
| `security-platform` | `SECURITY_PLATFORM` | `base_id` | — |
| `vulnerable-endpoint` | `ASSET` + `FINDING` | `vulnerable_endpoint_id` | `base_scenario_side` → SCENARIO, `base_simulation_side` → SIMULATION |
| `finding` | `FINDING` | `base_endpoint_side` | scenario, simulation sides; `base_inject_side` → ATOMIC_TESTING |
| `expectation-inject` | grants (bypass: `ACCESS_ASSESSMENT`) | `base_asset_side` | scenario, simulation sides; `base_inject_side` → ATOMIC_TESTING |
| `inject` | grants (bypass: `ACCESS_ASSESSMENT`) | — (targets in `base_assets_side`, stripped, step 5) | scenario, simulation sides; `base_id` → ATOMIC_TESTING |
| `scenario` | grants (bypass: `SCENARIO` READ) | — (`base_assets_side` stripped) | `base_id` → SCENARIO |
| `simulation` | grants (bypass: `ACCESS_ASSESSMENT`) | — (`base_assets_side` stripped) | `base_id` → SIMULATION, scenario side → SCENARIO |
| `asset-group`, `tag`, `team`, `domain`, `attack-pattern` | `ASSET_GROUP`, open types | — | — |
| snapshot observations | `SNAPSHOT_OBSERVATION` | `base_asset_side` | scenario, simulation sides |

**DoD**: a unit test fails if an `@Indexable` index is missing from the registry.

#### Step 3 — The scope object and its resolver ✅ done

- `EngineReadScope` (`openaev-model`): `unrestricted` flag (admin), readable `base_entity` values,
  grant filters per index (field → allowed ids), hidden asset ids, and `legacyGrantUser` for the es9 /
  OpenSearch snapshot export until they are ported.
- `EngineReadScopeResolver` (`openaev-api`), built from the current user and the request's `TxCtx`:
  - **capability**: for each index of the registry, `PermissionService` READ on its resource type;
  - **grant**: when the capability is missing and the type is grant-managed, the user's grants of
    the matching `Grant.GRANT_RESOURCE_TYPE` (`RawUserAuth.getUser_grants()` already carries it);
  - **marking**: the hidden asset ids, under the same clearance as the SQL rewrite.
- **No cache** in the POC: the scope is resolved on every call. Caching is a step 9 decision,
  driven by the measurements.

**DoD**: unit tests per dimension (admin, `ACCESS_ASSESSMENT`, grant-only, no capability, fail
closed); an asset re-marked RED appears in the hidden list on the next call.

#### Step 4 — Filters in `buildQuery` (es8) ✅ done

Next to `tenantFilter(ctx)`:

1. **capability**: one clause per readable type;
2. **grant**: per grant-managed index, `(base_entity = X AND grant-key ∈ allowed ids) OR …`;
3. **marking**: `must_not terms(<asset field of X>, hidden ids)`, per index.

`buildQuery` refuses a missing scope (fail closed). The commented-out `buildQueryRestrictions` call
and its "issue/3768" TODO are gone from es8.

**DoD**: step 1's document-level tests pass.

#### Step 5 — Buckets, labels and returned documents ✅ done

A **bucket** is one group of an Elasticsearch aggregation, i.e. one bar or slice of a chart. In a
"scenarios broken down by asset" widget there is one bucket per asset: its key is the asset id,
its count the number of documents; the label shown (the asset name) is resolved afterwards.

What `buildQuery` does not cover:

- **aggregations** on asset-id fields (`base_assets_side`, `base_asset_side`, `base_endpoint_side`…):
  `exclude: <hidden ids>` on the terms aggregation;
- **labels**: `resolveIdsRepresentative` goes through `buildQuery`, so hidden assets do not resolve;
- **returned documents**: hidden asset ids stripped from every `*_side` array of `entities` results.

**Known gap**: for a user without `ACCESS_ASSETS`, the asset names of a breakdown do not resolve and
the bars are labelled **"deleted"** (§4.5). Fixed in step 10, once Soumaya picks Option 1 or 2.

**DoD**: a scenario targeting `ASSET_GREEN` and `ASSET_RED` shows only `ASSET_GREEN` in its "by
asset" breakdown and in its returned `base_assets_side`.

#### Step 6 — Plumbing the scope to every caller ✅ done

`EngineService` read methods take an `EngineReadScope` instead of `RawUserAuth`. Callers:
`DashboardService` (stored widgets, ad-hoc, search, attack paths), `CustomDashboardService` /
`CustomDashboardTenantService` (home, scenario and simulation dashboards), `AttackPatternService`
(coverage), `SnapshotObservationService` (its own `grantFilter` replaced by the resolver).

**DoD**: no `EngineService` read is callable without a scope (compile time); existing dashboard,
coverage and snapshot tests still pass.

#### Step 7 — Everything behind the `MARKING` feature flag ✅ done (2026-10-20)

**Decided** (2026-10-20): the whole dashboard filter is behind `MARKING`, not only the markings. The
PR cannot be merged on `main` unless the flag-off path behaves exactly as `main`.

Before this step only the **marking** part followed the flag (`MarkingFilteringConfig` returns
`MarkedTables.EMPTY` when `MARKING` is off); the **capability** and **grant** parts applied to every
user, and es8 had lost `main`'s grant filter of the OpenGRC snapshot export.

| Where | Flag off: exactly `main` | Flag on: the POC |
|---|---|---|
| `EngineReadScopeResolver.resolve` | returns `EngineReadScope.unrestricted(null)` right away: no reader filter, no SQL | capabilities, grants, markings (steps 1–6) |
| `SnapshotObservationService` (OpenGRC export) | `main`'s `grantFilter()` / `currentUserAuth()`, kept as is, passed as `EngineReadScope.unrestricted(grantFilter())` | `scopeResolver.resolve(ctx)` |
| es8 `searchCursorPaged` | `main`'s `buildQueryRestrictions(scope.legacyGrantUser())`, kept as is, applied when the scope is unrestricted and carries a legacy user (es9 and OpenSearch already work this way) | the reader filter |
| es8 dashboards (`buildQuery`, aggregations, labels, stripping) | nothing to change: an unrestricted scope adds no clause, no `exclude`, and stripping is a no-op | unchanged |
| es9 / OpenSearch | unchanged (already `main`'s behaviour) | unchanged until steps 11–12 |

The flag is read with `PreviewFeatureService.isFeatureEnabled(MARKING)` (cached, `MARKING` or `*` in
`openaev.enabled-dev-features`).

**Effort: about 1 day (S).**

| Task | Size |
|---|---|
| Flag check in the resolver | ~10 lines |
| `main`'s `grantFilter` back in `SnapshotObservationService`, behind the flag | ~30 lines, from `main` |
| `main`'s `buildQueryRestrictions` back in es8, for the legacy user | ~40 lines, from `main` |
| Resolver unit tests: flag off → unrestricted | 1–2 tests |
| `SnapshotObservationServiceTest`: `main`'s "Grant filter" tests back as a flag-off group, the POC test as the flag-on group | restore + regroup |
| One integration class with the flag off (`@TestPropertySource(properties = "openaev.enabled-dev-features=")`, own Spring context): a role with only `ACCESS_DASHBOARDS` sees assets as on `main`, the resolver returns an unrestricted scope | 1 class |
| Targeted runs in both modes, spotless | ~1 h |

**Risks.** "Exactly `main`" must be proven by tests, not assumed. The flag-off class adds one Spring
context (a minute or two of CI).

**Built** (2026-10-20):
- `EngineReadScopeResolver.resolve`: returns `EngineReadScope.unrestricted(null)` when `MARKING` is
  off, before any user, permission or SQL lookup.
- `SnapshotObservationService`: `main`'s `grantFilter()` / `currentUserAuth()` back, used when
  `MARKING` is off (`readScope(tenantId)`).
- es8 `ElasticService`: `main`'s `buildQueryRestrictions` back, applied by `searchCursorPaged` to an
  unrestricted scope carrying a legacy user.
- Tests:
  - `EngineReadScopeResolverTest`: flag off → unrestricted, no user lookup;
  - `SnapshotObservationServiceTest`: `main`'s "Grant filter" tests back, as the flag-off group;
  - `CursorPagedSearchTest`: `main`'s three restriction tests back, as the flag-off group, on real
    Elasticsearch;
  - new `DashboardMarkingFeatureOffTest` (own Spring context, `openaev.enabled-dev-features=` empty):
    a reader with `ACCESS_DASHBOARDS` only finds an endpoint through the search, as on `main`.
- Run in a worktree: 318 dashboard, engine, snapshot and attack-pattern tests, all green.

**DoD**:
- with the flag **off**, `main`'s expectations hold, including `DashboardSearchApiTest`'s original
  role (`ACCESS_DASHBOARDS` only) and `main`'s snapshot "Grant filter" tests;
- with the flag **on** (the test default, `openaev.enabled-dev-features=MARKING`), the §4 matrix and
  the POC tests pass.

#### Step 7a — Grants: a simulation is read through its own grant ✅ done (2026-10-21)

**Use case.** Group `Analysts` (no `ACCESS_ASSESSMENT`) is granted on scenario `S1`.

1. `S1` runs every day: each new simulation gets a **copy** of `S1`'s grants, so `Analysts` can read
   them.
2. The admin removes `Analysts`' grant from `S1`. Simulations created **afterwards** are not granted
   to `Analysts`; the **earlier** ones keep their copied grant.
3. The admin also revokes `Analysts`' grant on one earlier simulation `X`.

| What `Analysts` sees | REST API | Dashboards before 7a | Dashboards after 7a |
|---|---|---|---|
| simulations created while `S1` was granted | ✅ (copied grant) | ✅ | ✅ |
| simulations created after the grant was removed | ❌ (no grant) | ❌ | ❌ |
| a simulation of `S1` created **before** `S1` was granted | ❌ | ✅ **leak** | ❌ |
| simulation `X`, its own grant revoked, `S1` still granted | ❌ | ✅ **leak** | ❌ |
| their injects, expectations, findings | same as their simulation | leaked with it | same as their simulation |

**Why it leaked.** Dashboards accepted a document if *any* of its grant fields matched, so a grant
on `S1` opened every simulation of `S1`. The REST API never does that: a simulation is read through
its own grant only, and a scenario grant reaches it only by being copied at creation.

**Fix.** A document is checked on the grant of its **parent resource**: its simulation, else its
scenario, else its atomic testing (registry keys in that order; es8 `grantedQuery` uses the first one
set on the document). With `MARKING` off, nothing changes.

**Tests.** `EngineReadScopeGrantIntegrationTest` (real grant rows): scenario grant only, simulation
grant, simulation grant revoked, atomic testing. `CursorPagedSearchTest`: a simulation grant reveals
its observation, a scenario grant alone does not. Matrix rows G2, G6, G8–G11 (step 8).

#### Step 7b — Filters on targeted assets: only visible assets count ✅ done (2026-10-21)

**Use case.** Asset `RED` is `TLP:RED`, asset `GREEN` is `TLP:GREEN`. Scenario `S1` targets
`GREEN` and `RED`; scenario `S3` targets `RED` only. A `TLP:GREEN` reader opens a dashboard whose
widget counts "scenarios targeting `RED`" (built by an admin, or through a dashboard parameter).

| What the `TLP:GREEN` reader sees | REST API | Dashboards before 7b | Dashboards after 7b |
|---|---|---|---|
| `S1` in a list | ✅, targets: `GREEN` | ✅, targets: `GREEN` | ✅, targets: `GREEN` |
| scenarios targeting `RED` | none | **2** (reveals `RED` and who targets it) | none |
| scenarios with no target | `S3` (its only target is hidden) | not `S3` | `S3` |
| scenarios targeting `GREEN` | `S1` | `S1` | `S1` |

**Why it leaked.** The returned targets were already cleaned, but a widget's filter was evaluated on
the stored document, hidden targets included.

**Fix.** A filter on a target field only looks at the visible targets: hidden ids are removed from
`eq` / `not_eq` values (after dashboard parameters are resolved), and the other operators (`empty`,
`not_empty`, `contains`, ranges) run a small script that skips hidden ids. Readers with no hidden
asset get the same query as before.

**Tests.** `EngineReadScopeIntegrationTest`, group "Filters on dependency fields": 10 cases on
inject targets, the same field and rule as scenarios (`eq`, `not_eq`, `empty`, `not_empty`,
`contains` on part of an id, ranges, a parameter, an unrestricted reader); 7 of them fail without
the fix. Matrix rows M14–M16 (step 8).

#### Step 7c — Findings on several endpoints: one visible endpoint is enough (review fix) ✅ done (2026-10-23)

**Use case.** Asset `RED` is `TLP:RED`, asset `GREEN` is `TLP:GREEN`. A scan produced finding `F1` on
`GREEN` and `RED`, finding `F2` on `RED` only, finding `F3` on no endpoint. A `TLP:GREEN` reader opens
the findings list and a "findings by endpoint" widget.

| What the `TLP:GREEN` reader sees | REST API | Dashboards before 7c | Dashboards after 7c |
|---|---|---|---|
| `F1` (GREEN + RED) | ✅, endpoint: `GREEN` | ❌ **missing** | ✅, endpoint: `GREEN` |
| `F2` (RED only) | ❌ | ❌ | ❌ |
| `F3` (no endpoint) | ✅ | ✅ | ✅ |
| findings count | 2 | 1 | 2 |
| "findings by endpoint" | `GREEN` only | `GREEN` only | `GREEN` only |

**Why it differed.** A finding's endpoints are one multi-valued field (`base_endpoint_side`), and
dashboards dropped the document as soon as **one** value was hidden. The REST API keeps a finding
with **one** visible asset (`MarkingDerivedTables.FROM_ASSETS`, `MarkingLinkedTablesRowsTest`).

**Fix.**
- Registry: `linkedAssetFields` next to `assetFields`, like link rows vs a parent link in
  `MarkingDerivedTables`. Findings declare `base_endpoint_side` there.
- es8: a document is excluded through a linked field only when it has values there and none is
  visible (option 1: the visible-references script of 7b, `exists` negated, only for readers with
  hidden assets).
- Returned findings: hidden endpoint ids stripped (`stripHiddenAssets`).
- If the script costs too much on a large findings index (step 9), option 2: an indexed endpoint
  count and a `terms_set` query, no security data in the index.

**Tests.** `EngineReadScopeResolverIntegrationTest`, group "Findings linked to several endpoints"
(real markings): `F1` listed with `GREEN` only, count 2, no `RED` bucket, both endpoints once `RED` is
granted, `F1` hidden once `GREEN` is re-marked `RED`. Matrix rows M6, M10 (step 8). Regression run
(engine, dashboards, findings, 551 tests): green, apart from `FindingRefeedEndToEndTest`, brought by
the `main` merge, which read the index with no scope; it now passes an unrestricted one.

#### Step 7d — Security platforms on a visible asset: a hidden platform is not shown (review fix) ✅ done (2026-10-23)

**Use case.** Asset `GREEN` is `TLP:GREEN`. Security platform `EDR-RED` is `TLP:RED` (a security
platform is an asset, so it can be marked), `EDR-X` is unmarked. Both report a detection on an
expectation of `GREEN`. A `TLP:GREEN` reader opens a dashboard with the expectations, an "expectations
by security platform" widget and a widget filtered on `EDR-RED`.

| What the `TLP:GREEN` reader sees | REST API | Dashboards before 7d | Dashboards after 7d |
|---|---|---|---|
| the expectation on `GREEN` | ✅ | ✅ | ✅ |
| its security platforms | `EDR-RED` not in the security platforms list | `EDR-X`, **`EDR-RED`** | `EDR-X` |
| "expectations by security platform" | — | `EDR-X`, **`EDR-RED`** | `EDR-X` |
| expectations reported by `EDR-RED` | — | **1** | none |
| an observation's platforms (reporting, succeeded) | — | with **`EDR-RED`** | without |

**Why it leaked.** Expectations and attack observations keep the ids of the platforms that reported
(`base_security_platforms_side`) and, for an observation, of those that succeeded
(`attack_observation_platforms_succeeded`). These fields were not in the asset reference fields, so a
hidden platform kept its bucket, matched filters and stayed in the returned documents.

**Fix.**
- Registry: both fields join `ASSET_REFERENCE_FIELDS`, so they get the bucket `exclude` and the 7b
  filter handling (visible values only).
- Returned expectations and observations: hidden platform ids stripped (`stripHiddenAssets`).
- The document itself is kept: it is about its asset (`base_asset_side`); the platforms are sources,
  like the targets of a scenario.

**Tests.** `EngineReadScopeResolverIntegrationTest`, group "Security platforms reporting on a visible
asset" (real markings): the expectation listed with `EDR-X` only, no `EDR-RED` bucket, a filter on
`EDR-RED` matches nothing, all back once `RED` is granted. `EngineReadScopeTest` (unit): both
observation fields stripped, nothing stripped for an unrestricted reader; 5 of them fail without the
fix. Matrix row M17 (step 8).

#### Step 7e — Observations whose run is not named: fail closed (review fix) ✅ done (2026-10-23)

**Use case.** Bob has the snapshot observation capability, no `ACCESS_ASSESSMENT`, and a grant on
simulation `S` only.
- Atomic testing `T` (not granted to Bob) finds `CVE-X` on asset `A`: the vulnerability observation
  `(A, CVE-X)` comes from `T`.
- Simulation `S2`, not linked to a scenario, is granted to Bob; its observation `(B, CVE-Y)` is
  exported, then `S2` is deleted.

| What Bob sees | REST API | Dashboards / export before 7e | after 7e |
|---|---|---|---|
| the observation of `T` | the findings of `T` need the `T` grant | ✅ **visible** | ❌ |
| the observation of `S2`, `S2` deleted | — | ✅ **visible, to every reader of observations** | ❌ |
| an admin, or a reader with `ACCESS_ASSESSMENT` | — | ✅ | ✅ |

**Why it leaked.** Grants are checked on the document's parent resource, read from its grant keys
(7a). A document with no key at all was read as "linked to nothing grantable", hence open. An
observation always comes from a run, but does not always name it: it keeps no atomic testing key, and
loses its simulation key when the simulation is deleted (the observation survives it). With `MARKING`
off, the export's `base_restrictions` filter kept the deleted-simulation case restricted.

**Fix.** Registry: `parentRequired` on the two observation types. es8 `grantedQuery`: for those types,
a document with no grant key matches no grant, so only the bypass reads it. The other grant-managed
types always carry a key (their own id, or their inject).

**Later, if product asks:** index the atomic testing (`base_inject_side`) on vulnerability observations,
with a re-feed, so an atomic testing grant opens them.

**Tests.** `CursorPagedSearchTest`, group "restrictions": a reader with no grant sees neither
observation; a simulation grant does not open the atomic testing observation, the admin reads it; once
its simulation is deleted (`bulkDelete`), the observation is closed to the reader granted on it and to
a reader with no grant, and stays readable by the admin. All three fail without the fix.

#### Step 7f — Quick fixes from the PR review ✅ done (2026-10-23)

From the review of 2026-10-23 (code, security, multi-tenancy and performance reviewers).
- **A finding's `base_dependencies` holds its hidden endpoints.** A finding on `GREEN` + `RED` is
  returned with `RED` in `base_dependencies`. Strip hidden ids from `base_dependencies` for every type
  (not `@JsonIgnore`: cascade delete relies on it).
- **`eq` in `and` mode with a hidden value widens the match.** `eq [GREEN, RED]` (and) becomes "targets
  `GREEN`"; it must match nothing.
- **`hiddenAssetIds()` rebuilt per document** (Copilot's open comment): check `unrestricted` first,
  compute the union once.
- **Keep `ASSET_REFERENCE_FIELDS` and `stripHiddenAssets` in sync:** a reflection test (every field
  exists, every model holding one gets it stripped), so a 7d-like gap fails a test.

**Tests.** `EngineReadScopeTest` (findings group, dependencies, the guard), the `and`-mode case in
`EngineReadScopeIntegrationTest`, no hidden id in the returned JSON in
`EngineReadScopeResolverIntegrationTest`. Each fix reverted alone fails its own test; the guard
catches a strip case removed (`EsScenario`).

#### Step 7g — To decide from the PR review 🔴 not started

- **Assets carry the ids of simulations, scenarios and findings the reader has no grant on**
  (`EsAsset.base_simulation_side`, `base_scenario_side`, `base_findings_side`): a reader with asset read
  only can bucket assets by simulation. Options: filter these fields by grants like the asset
  references, or only let readers who see every simulation, scenario and finding use them.
- **Before step 9 (otherwise the measure is mostly theirs):** pre-filter the Painless script with the
  hidden ids (`terms`) and look them up with `binarySearch`; send the hidden ids once (not per asset
  field and again in the no-tenant clause) and fail the same way on every widget past
  `index.max_terms_count`.
- **Smaller:** `sharedRules()` untested and the audit-log index has no `base_tenant_side`;
  observation coverage for 7d through the snapshot export; `PermissionService` reads `TenantContext`;
  grants are one union across tenants (not exploitable, contradicts the Javadoc).

#### Step 8 — End-to-end tests: RBAC, grants, markings 🟡 API tests partly done

Through the real endpoints (API integration tests), plus a Playwright pass in `tests_e2e/` on the
custom dashboard, the Statistics tab and an asset page. The matrix is in §6.

- **RBAC (capabilities)**: rows R1–R7.
- **Grants**: rows G1–G11 (G8–G11: no scenario-to-simulation inheritance, step 7a).
- **Markings**: rows M1–M16 (M6, M8–M13: findings, including multi-asset ones; M14–M16: filters on dependency fields, step 7b).
- **Across the three**: rows X1–X3.
- Manual two-browser check, as in Chunk 2 of `implementation-plan.md`: an admin and a `TLP:GREEN`
  user side by side. The admin marks an asset RED, then removes the user's grant on S1. Each change
  shows on the user's next refresh.

**DoD**: every row of §4 green on es8; manual check recorded here with its date.

#### Step 9 — Performance measurement 🔴 not started

Following §4.7 of the design.

- **Data**: a restored **main-environment dump** (the `dev-db-restore` procedure), reindexed, plus the
  generated small / medium / large profiles of §4.7.
- **Readers**: admin (baseline, equal to `main`), a user with every capability and hidden assets, and a
  grant-only user with hidden assets (worst case).
- **Measures**:
  - scope build time (user load, hidden-assets query, `EXPLAIN ANALYZE`);
  - Elasticsearch `took` and `profile` on one widget;
  - endpoint p50 / p95, for a 10-widget dashboard with 1 and 20 concurrent users;
  - Elasticsearch query-cache hit rate and heap;
  - the size of the grant and hidden-asset lists.
- Add `@LogExecutionTime` to the dashboard endpoints first (the convention requires it; they lack
  it).

**AC**:
- the numbers are recorded here, per profile and per reader, against the admin baseline;
- they are turned into an **SLO** (e.g. widget p95 within X % of the baseline on the medium
  profile), written in the dashboard ADR (step 14);
- the threshold above which a list fails closed (`terms` limit) is decided;
- if the SLO is missed, the mitigations of §4.7 are planned: scope cache per (user, tenant), an
  indexable hidden-assets query, shorter grant lists.

#### Step 10 — Asset labels in breakdowns 🔴 waiting for Soumaya

Implement the option chosen in §4.5. **Option 1**: resolve the asset names with the tenant filter and
the hidden asset ids only, not the `ASSET` capability (consistent with Option B: The editor follows
the parent). **Option 2**: label unresolved bars "restricted" and keep "deleted" for assets that are
really deleted.

**DoD**: row R2 of §4 green with the chosen label; never "deleted" for an existing asset.

#### Step 11 — Port to es9 (dashboards and OpenGRC export) 🔴 not started

- Dashboards: same filters, buckets, labels and stripping in `es9/ElasticService` as in es8 (steps
  4–5).
- Tests: remove `@DisabledIfEnvironmentVariable(named = "ENGINE_ENGINESELECTOR", ...)` from
  `EngineReadScopeIntegrationTest`, `DashboardMultiTenantReaderScopeTest` and the flag-on
  "restrictions" group of `CursorPagedSearchTest` once both engines are ported (they skip es9 and
  OpenSearch in the nightly until then).
- OpenGRC snapshot export: `searchCursorPaged` still applies the former grant-only filter
  (`buildQueryRestrictions(scope.legacyGrantUser())`, no marking). Replace it with the reader filter,
  as es8 does since step 6.
**DoD**: step 1's engine tests, `SnapshotObservationServiceTest` and the §4 matrix pass with
`engine.engine-selector=elk9`.

#### Step 12 — Port to OpenSearch (dashboards and OpenGRC export) 🔴 not started

- Same as step 11, in `os3/OpenSearchService`.
- Once es9 and OpenSearch are both ported: delete `EngineReadScope.legacyGrantUser` and the last
  `buildQueryRestrictions`.

**DoD**: step 1's engine tests, `SnapshotObservationServiceTest` and the §4 matrix pass with
`engine.engine-selector=opensearch`; no `legacyGrantUser` left.

#### Step 13 — Full test suite and CI 🔴 not started

- Run the full backend suite (about 3 h locally) and the CI pipeline.
- Fix the existing tests that relied on dashboards ignoring permissions. Like `DashboardSearchApiTest`,
  a role with only `ACCESS_DASHBOARDS` no longer sees assets.

**DoD**: CI green on the three engines.

#### Step 14 — ADR and documentation 🔴 not started

- **ADR**: dashboards filtered by capability, grant and marking, with security resolved in SQL and
  ids passed to Elasticsearch. It records the one-rule decision (§4.5), the sync-lag analysis (§4.6)
  and the SLO from step 9.
- **User documentation** (`docs/`) and **release note**: this is a behaviour change. Users who only
  hold `ACCESS_DASHBOARDS` will see less than today: no assets without `ACCESS_ASSETS`, no findings
  without `ACCESS_FINDINGS`, only their granted scenarios without `ACCESS_ASSESSMENT`, no data of
  assets above their clearance. The OpenGRC snapshot export changes the same way: it now also hides the
  observations of assets above the caller's clearance (observations copy `asset_name` and
  `asset_hostname`, so the whole observation is excluded). Confirm with the OpenGRC side which user
  the export runs as, and that its role holds `SNAPSHOT_OBSERVATION` (and `ACCESS_ASSESSMENT` if it
  must see every scenario).

#### Steps 15–20 — Related bugs, one GitHub issue each 🔴 not started

Found while writing the design, not fixed by this plan. Each step below is ready to paste into the
**Bug report** issue template (`.github/ISSUE_TEMPLATE/1-bug_report.md`, labels `needs triage`,
`bug`). Common environment for all of them:

- **OS**: any (seen on macOS, local dev).
- **Version**: `main` at `3c68cbd53` (2026-10-20); unrelated to the `MARKING` preview feature.
- **Users**: a non-admin user, for example **Amber** (group `Manager`), whose role is edited as each
  step says.

#### Step 15 — `fix(atomic-testing): edit form shows no target asset without ACCESS_ASSETS`

**Description.** In an atomic testing, the detail view lists the target assets through the atomic
testing's own permission, but the edit form loads them from the asset catalogue, which needs
`ACCESS_ASSETS`. A user allowed to read and edit the atomic testing sees and edits no target.
Scenarios and simulations do not have the bug: their forms use endpoints scoped to the parent.

**Reproducible steps**
1. Edit the role `Manager`: remove `ACCESS_ASSETS`, `MANAGE_ASSETS`, `DELETE_ASSETS`; keep
   `ACCESS_ASSESSMENT` and `MANAGE_ASSESSMENT`.
2. As an admin, create an atomic testing targeting an endpoint `E1`.
3. Log in as Amber and open the atomic testing: `E1` is listed in its targets.
4. Open the atomic testing's edit form, Targets section.

**Expected output.** `E1` is listed, and can be removed (as in a scenario or a simulation).

**Actual output.** No asset is listed. DevTools: `POST /api/endpoints/find` → `403`.

**Additional information.**
- `EndpointContextForAtomicTesting.ts` calls the global `findEndpoints` / `findAssetGroups`;
  `EndpointContextForScenario.ts` / `EndpointContextForExercise.ts` call
  `/api/scenarios/{id}/endpoints/find` / `/api/exercises/{id}/endpoints/find`, checked on the parent.
- Fix: add `/api/atomic-testings/{id}/endpoints/find` and `/asset-groups/find`, checked on
  `ATOMIC_TESTING` READ and limited to the assets the inject references; point the atomic-testing
  context at them. Size S.
- Design: `tech-design-dashboard.md` §1.1–1.3.

#### Step 16 — `fix(injects): "Add assets" offered without ACCESS_ASSETS`

**Description.** The "Add" button of an inject's targets opens the asset picker, which searches the
asset catalogue (`ACCESS_ASSETS`). It is shown to users without that capability, in scenarios,
simulations and atomic testings, and opens an empty picker.

**Reproducible steps**
1. Same role as step 15 (no `ACCESS_ASSETS`, `MANAGE_ASSESSMENT` kept).
2. Log in as Amber, open a scenario, edit one of its injects, Targets section.
3. Click "Add assets" (or "Add asset groups").

**Expected output.** The button is hidden or disabled without `ACCESS_ASSETS`.

**Actual output.** An empty picker opens. DevTools: `POST /api/endpoints/search` → `403`.

**Additional information.** `InjectAddEndpoints.tsx` and `InjectAddAssetGroups.tsx` have no ability
check; gate them with `ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)`. Size XS.

#### Step 17 — `fix(findings): embedded findings rows link to a page the user cannot open`

**Description.** The Findings tab of a scenario, simulation or atomic testing lists findings through
the parent's permission, but every row links to the finding detail page, which needs
`ACCESS_FINDINGS`.

**Reproducible steps**
1. Edit the role `Manager`: remove `ACCESS_FINDINGS`; keep `ACCESS_ASSESSMENT`.
2. As an admin, run a simulation that produces findings.
3. Log in as Amber, open the simulation, Findings tab: the findings are listed.
4. Click a finding.

**Expected output.** Without `ACCESS_FINDINGS`, the rows are not links (or the link is disabled).

**Actual output.** The browser goes to `/admin/findings/{findingId}` and the page is refused (route
guarded by `ACCESS_FINDINGS`; `GET /api/findings/{id}` → `403`).

**Additional information.** `FindingList.tsx:244` (row `Link`); route `findings/:findingId` in
`admin/Index.tsx` (`ProtectedRoute` on `ACCESS_FINDINGS`). Size XS.

#### Step 18 — `fix(dashboards): "Select dashboard" hidden for MANAGE_ASSESSMENT without a grant`

**Description.** In the Statistics tab of a scenario or simulation, "Select dashboard" only checks a
grant on the resource. A user who can manage every scenario through `MANAGE_ASSESSMENT` does not see
it, although the backend accepts the change.

**Reproducible steps**
1. Give a role `MANAGE_ASSESSMENT` and `ACCESS_DASHBOARDS`, and no grant on scenario `S1`.
2. Log in as that user, open `S1`, Statistics tab.

**Expected output.** "Select dashboard" is shown, as for a user with a PLANNER grant on `S1`.

**Actual output.** The button is not shown. Yet `PUT /api/scenarios/{S1}` with
`scenario_custom_dashboard` succeeds for this user (`SCENARIO` WRITE through the capability).

**Additional information.** `ScenarioAnalysis.tsx:141,158` and `SimulationAnalysis.tsx:98,115` use
`ability.can(MANAGE, RESOURCE, id)` alone; use `useScenarioPermissions` /
`useSimulationPermissions` (`canManage` = grant OR capability). Size XS. Design: §2.5.

#### Step 19 — `fix(assets): asset posture data checked on ACCESS_DASHBOARDS instead of ACCESS_ASSETS`

**Description.** The asset pages (detail, asset group detail, posture score column of the lists,
security platform detail) load their posture and statistics through
`POST /api/dashboards/adhoc/series`, checked on `ACCESS_DASHBOARDS`. One screen then needs two
capabilities, and the asset data is reachable without the asset capability.

**Reproducible steps**
1. Role A: `ACCESS_ASSETS` without `ACCESS_DASHBOARDS`. Log in, open an endpoint's detail page,
   then its Statistics tab.
2. Role B: `ACCESS_DASHBOARDS` without `ACCESS_ASSETS`. As an admin, copy from DevTools the body of
   the `POST /api/dashboards/adhoc/series` call made by step 1's page; send it as role B.

**Expected output.** Role A sees the posture score and the charts; role B gets a `403`.

**Actual output.** Role A: posture score and charts empty, `adhoc/series` → `403`. Role B: `200`
with the asset's posture data.

**Additional information.** `useExpectationPosture` (`AssetDetail.tsx:136`),
`PostureScoreOverTimeChart` / `InjectsPlayedOverTimeChart` (`AssetDetail.tsx:258-262`),
`usePostureScores` (`Endpoints.tsx:138`).
- **Do not restrict `/adhoc/series` itself**: the default home page (`DefaultHomeDashboard.tsx:70,
  166`) uses it for every user with `ACCESS_DASHBOARDS`, assets or not, and the security platform
  page is reached with `ACCESS_SECURITY_PLATFORMS`, not `ACCESS_ASSETS`.
- Fix: posture endpoints scoped to the resource, which build the widget configuration server-side
  (the browser no longer chooses index and filters): endpoint and asset group checked on `ASSET` /
  `ASSET_GROUP` READ, security platform on `SECURITY_PLATFORM` READ, plus one bulk call for the list
  columns. The asset pages then stop calling `/adhoc`. Size S.
- With `MARKING` on, the dashboard reader filter already limits role B's response to the
  expectations it may read (`ACCESS_ASSESSMENT` or a grant); role A's 403 remains.

Design: §3.1.

#### Step 20 — `fix(dashboard): custom home dashboard widgets need ACCESS_TENANT_SETTINGS`

**Description.** Follow-up of
[OpenAEV-Platform/openaev#8367](https://github.com/OpenAEV-Platform/openaev/issues/8367): the
`/adhoc` endpoints of the default home dashboard now check `ACCESS_DASHBOARDS`, but a tenant's
**custom** home dashboard is still served by `TenantSettingsApi`
(`/api/tenants/{tenantId}/tenant-settings/home-dashboard/{count,average,series,entities,entities-runtime,attack-paths}/{widgetId}`),
checked on `TENANT_SETTING` READ, i.e. `ACCESS_TENANT_SETTINGS`, an administration capability.

**Reproducible steps**
1. As an admin, set a custom dashboard as the tenant's home dashboard.
2. Log in as a user whose role has `ACCESS_DASHBOARDS` but not `ACCESS_TENANT_SETTINGS`.
3. Open the home page.

**Expected output.** The widgets load, as on the default home dashboard.

**Actual output.** The widgets stay empty; `POST /api/tenants/{tenantId}/tenant-settings/home-dashboard/entities/{widgetId}` →
`403`.

**Additional information.** `TenantSettingsApi.java` (`@AccessControl(READ, TENANT_SETTING)` on the
six `home-dashboard/*` data endpoints); same change as [OpenAEV-Platform/openaev#8368](https://github.com/OpenAEV-Platform/openaev/pull/8368). Size XS.


#### Step 21 — *Optional* refactor, no behaviour change: one tenant filter, from the scope 🔴 not started

Today `buildQuery` applies `tenantFilter(ctx)` next to `readerFilter(scope)`, whose per-tenant
clauses already cover the request's tenants: the outer filter is redundant for a restricted reader,
and the only isolation for an unrestricted one (design §4.2, "Where the tenant filter stands").
Best done once es9 and OpenSearch are ported (steps 11–12), so the three engines move together.

- The scope always carries the request's tenants: an unrestricted reader (`MARKING` off, admin,
  sees everything) gets `TenantRules.ALL` for each tenant of the `TxCtx`, instead of
  `EngineReadScope.unrestricted(...)` without tenants.
- `readerFilter(scope)` becomes the only tenant filter; `tenantFilter(ctx)` is deleted from
  `buildQuery` and from `searchCursorPaged`, in the three engines. With `MARKING` off, the query
  matches the same documents as `tenantFilter` (one `base_tenant_side` clause per tenant instead of
  one `terms`).
- The tied per-tenant clauses stay: they are what applies each tenant's rights to its own documents.
- Once no engine filters on the `TxCtx`, drop the `TxCtx` parameter from the `EngineService` read
  methods: the scope is the only input.

**DoD**: no behaviour change, proven by tests passing unchanged on the three engines: the flag-off
tests (`DashboardMarkingFeatureOffTest`, the flag-off groups of `SnapshotObservationServiceTest` and
`CursorPagedSearchTest`), the engine's tenant isolation tests (`EngineServiceIntegrationTest`
"Tenant scope") and the §4 matrix; no `tenantFilter(ctx)` left.
---

## 4) End-to-end test matrix

**Data**: tenant T, scenarios `S1` and `S2` with one simulation each, atomic testing `AT1`, assets
`A-GREEN` (`TLP:GREEN`), `A-AMBER` (`TLP:AMBER`), `A-RED` (`TLP:RED`). Each scenario targets the three
assets, with findings and expectations on each asset. One finding is on both `A-GREEN` and `A-RED`,
another on both `A-GREEN` and `A-AMBER`.

**Users**

| User | Role capabilities | Grants | Clearance |
|---|---|---|---|
| `admin` | admin | — | all |
| `full-green` | every capability | — | `TLP:GREEN` |
| `amber` (§1.1, §4.5) | Manager without `ACCESS_ASSETS` | — (has `ACCESS_ASSESSMENT`) | `TLP:AMBER` |
| `granted` | `ACCESS_DASHBOARDS`, `ACCESS_ASSETS`, `ACCESS_FINDINGS`, no `ACCESS_ASSESSMENT` | OBSERVER on `S1` | `TLP:GREEN` |
| `dash-only` | `ACCESS_DASHBOARDS` only | — | `TLP:GREEN` |

**RBAC (capabilities)**

| # | User | Where | Expected |
|---|---|---|---|
| R1 | `dash-only` | custom dashboard, "Endpoints" list widget | no asset |
| R2 | `amber` | custom dashboard, scenarios broken down by asset | bars for `A-GREEN` and `A-AMBER`; label per step 10, never "deleted" |
| R3 | `dash-only` | custom dashboard, "Findings" list widget | no finding |
| R4 | `dash-only` | full-text search on `A-GREEN`'s hostname | no hit |
| R5 | `amber` | `S1` Statistics tab with an asset dashboard attached | same result as R2: the tab does not widen what the dashboard shows |
| R6 | `full-green` | every widget of the dashboard | same as `admin`, minus `A-AMBER` and `A-RED` |
| R7 | `admin` | every widget | everything (baseline, equal to `main`) |

**Grants**

| # | User | Where | Expected |
|---|---|---|---|
| G1 | `granted` | scenarios widget | `S1` only |
| G2 | `granted` | simulations, injects, expectations, findings widgets | `S1`'s own documents, and those of `S1`'s simulations created after the grant only |
| G3 | `granted` | `S1` Statistics tab, `simulation` parameter changed to `S2`'s simulation | nothing of `S2` |
| G4 | `granted` | atomic testings widget | nothing (no grant on `AT1`); `AT1` appears once granted |
| G5 | `granted` | after the admin removes the grant on `S1` | `S1` gone on the next refresh |
| G6 | `granted` | a new simulation of `S1`, created after the grant | visible (grant copied at creation, Part 2) |
| G7 | `amber` | scenarios widget | `S1` and `S2` (`ACCESS_ASSESSMENT`, no grant needed) |
| G8 | `granted` | a simulation of `S1` created **before** the grant on `S1` | hidden, with its injects, expectations and findings (no inheritance, as in the REST API) |
| G9 | `granted` | after the admin revokes the grant on a simulation of `S1`, keeping `S1`'s | that simulation and its children gone on the next refresh |
| G10 | user granted on a simulation `X` of `S1` only | scenarios and simulations widgets | `X` and its children visible, `S1` hidden |
| G11 | `granted` | same simulation: REST simulations list vs dashboard simulations widget | same answer |

**Markings**

| # | User | Where | Expected |
|---|---|---|---|
| M1 | `full-green` | endpoints list, breakdown by asset, findings, expectations, vulnerable endpoints, attack paths, search | no `A-AMBER` or `A-RED` anywhere, counts included |
| M2 | `amber` | the same widgets | `A-GREEN` and `A-AMBER`, never `A-RED` |
| M3 | `full-green` | inject and scenario documents targeting the three assets | the inject is visible; `A-AMBER` and `A-RED` are stripped from its targets |
| M4 | `full-green` | after the admin marks `A-GREEN` as `TLP:RED` | `A-GREEN` and its history gone on the next refresh |
| M5 | `full-green` | after the admin gives the user's group `TLP:RED` | `A-RED` appears on the next refresh |
| M6 | `full-green` | the finding on `A-GREEN` and `A-RED` | visible with `A-GREEN` only, as in the REST API (step 7c) |
| M7 | `full-green` | OpenGRC snapshot export (`BULK_SNAPSHOT_EXPORT`) | no observation of `A-AMBER` or `A-RED`; only granted scenarios for a caller without `ACCESS_ASSESSMENT` |
| M8 | `full-green` | findings list widget, finding on `A-GREEN` only | visible |
| M9 | `full-green` | findings list widget, finding on `A-RED` only | hidden |
| M10 | `full-green` | findings count widget on `S1` | counts the findings with at least one `TLP:GREEN` endpoint: the two multi-asset findings included, not the `A-AMBER`-only or `A-RED`-only ones |
| M11 | `full-green` | the `A-GREEN` + `A-RED` finding: REST findings tab of `S1` vs dashboard | REST shows it (permissive), the dashboard hides it (strict): record the difference until the strict/permissive question is settled (§4.3 note of the design) |
| M12 | `amber` | the `A-GREEN` + `A-AMBER` finding | visible: both assets are within `TLP:AMBER` |
| M13 | `full-green` | vulnerable endpoints widget on `S1` | `A-GREEN` only: the vulnerable endpoint of `A-RED` is hidden with its copied hostname and platform |
| M14 | `full-green` | admin-authored widget: count of scenarios filtered on `base_assets_side = A-RED` | 0, while the admin sees the real count |
| M15 | `full-green` | scenarios widget filtered `base_assets_side` empty / not empty, a scenario targeting `A-RED` only | counted as empty, like its visible (stripped) targets |
| M16 | `full-green` | widget filtered on `base_assets_side` through a dashboard parameter set to `A-RED` | nothing |
| M17 | `full-green` | expectation on `A-GREEN` reported by a `TLP:RED` security platform: list, "by security platform" widget, filter on that platform | expectation listed without the platform, no bucket, the filter matches nothing |

**Across the three**

| # | User | Where | Expected |
|---|---|---|---|
| X1 | `granted` | `S1` findings count: REST findings tab vs dashboard widget | same number |
| X2 | `amber` | default home dashboard (`/adhoc`) | filtered like R2 and M2 |
| X3 | every user | the same widget on es8, es9 and OpenSearch (steps 11–12) | identical results |
