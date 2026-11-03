# #8555 - Marking clearance in chained simulations and autonomous runs

> Brainstorm notes. Issue: https://github.com/OpenAEV-Platform/openaev/issues/8555
> Parent: #8552 (US2) - Epic #8171 - Task.4 Chunk 7 (size M) - Labels: backend, feature, feature-flag
> Assignee: damgouj - Branch: `issue/8555`
> Status: **brainstorming** - nothing here is decided unless marked ✅.

## 1. The issue in one paragraph

Chained simulations and autonomous runs are both *chained scenarios*, and both create simulations.
The PoC stamps the current user as launcher on the autonomous entry points (create, restart,
promote to real run) but it was never verified end to end: chained steps, findings, attack paths.
Goal: confirm the expected behaviour with Vertical 1, and if it doesn't work, make chained
scenarios behave like time-based ones.

## TODO

- [ ] Check with Eva for the next snapshot feature

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | **Chaining does not create assets**, and none is planned in the near future. Newly discovered endpoints appear in the attack path with their IP only; they are discoveries, not asset objects. **No marking policy for created assets is needed** | ✅ |
| D2 | Marking must still be enforced on every chaining **read surface**: attack paths, execution page, findings page, statistics, allow/deny lists (section 3). | ✅ |
| D3 | Rule for attack paths: if an asset is filtered out for a user by its clearance, **its attack path is hidden too**. | ✅ |
| D4 | S1 is fixed by deriving `attackpath_execution` / `attackpath_finding` from `assets` in `MarkingDerivedTables` (section 6). Feasibility checked statically (section 6). | ✅ |
| D5 | A derived table may carry **several parent links, ANDed** (hidden if any parent is hidden), so `attackpath_execution` links both its target and its source asset (6.4). | ✅ |

## 3. What must be filtered (chaining read surfaces)

Because chaining stores its own denormalized copies (names, IPs, snapshots, values) in tables that are
**not marked tables**, the statement inspector does not filter them. Each surface below needs an
explicit read-side filter or mask, using the *reader's* clearance.

| # | Surface | What leaks / is wrong today | Direction | Status |
|---|---|---|---|---|
| S1 | **Attack paths** | `attackpath_execution` / `attackpath_finding` store `targetHostname`, `targetIp`, `targetPlatform`, `sourceHostname/Ip/Platform`, `agentName`, `targetRawValue`, finding values. Only `SIMULATION READ` is checked. A GREEN reader sees a RED host's name/IP/findings if a RED user launched. Separate symptom: the tab is **empty** for any marked asset (see section 6) | Hide a path when its asset is filtered for the reader. Filter/mask in `AttackPathGraphService`, or derive the tables from `assets` in `MarkingDerivedTables` (`targetAssetId` exists) | To fix |
| S2 | **Execution page** | Not a chaining-specific page: `simulation/Index.tsx` routes `execution/*` to the same `ExecutionOverview` (injects, expectations, teams, traces) for chained and time-based simulations. Its data comes from `injects_assets`, `injects_expectations`, `agents`, `execution_traces`, all already in `MarkingDerivedTables` | Nothing to add | **Already covered** (static read, confirm in the RED/GREEN test) |
| S3 | **Findings page** | Same shared route: `findings` renders `SimulationFindings` -> `searchDistinctFindingsForSimulations` -> `FindingDistinctSearchService` (JPA specification on `findings`), and `findings` / `findings_assets` are already derived from `assets`. Matches Damien's test (finding hidden for GREEN). The chaining-only view of findings is the attack path (S1) | Nothing to add | **Already covered** (static read) |
| S4 | **Statistics** (e.g. dashboard chaining stats) | Not traced yet. Aggregates can leak counts of hidden executions/findings if built from unmarked tables | Compute stats from filtered data only | **On hold**: done in the dashboards POC, not in #8555 |
| S5 | **Asset allow / deny lists** (`workflow_scope_rules`) | Not a marked table: saved label and JSON snapshots at launch/end bypass the inspector. `WorkflowConfigurationMapper.toScopeRuleOutput` has no visibility check; `ScopeSnapshotService.computeStatus` reads a hidden asset as "deleted". Deny rules naming a RED asset leak its existence the same way | Two paths, see section 7: read = reader's clearance (mask in the mapper), execution = launcher's clearance (filter at step expansion). Not derivable like S1 (polymorphic rule value) | To fix |


### 3.1 Corinne's findings on S5 (issue comment 2)

Drafted bug: *scope rules expose the name and snapshot of assets the reader is not cleared for.*

- Two exposure points: `WorkflowConfigurationMapper.toScopeRuleOutput` and `ScopeSnapshotService.computeStatus`.
- Copies are written under whichever clearance is active (user vs system depending on the path), so a
  restricted user's cancel/reset might mutate what the admin sees (code-based, **unconfirmed**).
- Asset groups: all members are frozen in snapshots; a visible group can wrongly read `MODIFIED_DURING_EXECUTION`.
- Proposed fix: Part A (write at system clearance via `TenantScopedTransaction.withSystemClearance`)
  + Part B (mask on read, new `RESTRICTED` status, neutral chip on the front).
- Her test is not conclusive on execution: the RED asset had no agent, so it does not prove the
  clearance gate stopped it.

## 4. What we already know

### 4.1 Damien's first tests (issue comment 1)

| Case | Result |
|---|---|
| Creating a chained scenario with a scope filtered by markings | Works |
| Any marked asset (general) | **Attack Path tab is empty** |
| Asset RED, user RED | Other tabs and results OK |
| Asset RED, user GREEN | Attack Path + Scope tabs: "The asset XXX has been deleted during execution." Scope allow list **not filtered** by markings. Finding not visible (good) |
| Asset GREEN, user GREEN or RED | Other tabs and results OK |

### 4.2 Code facts (checked on branch `issue/8555`)

- Launcher is stamped in: `AutonomousRunService` (`resolveLaunchedBy()` at lines ~424, 1539, 1627), `ExerciseService:813`, `ExerciseApi:632`, `ScenarioApi:682`, `ScenarioToExerciseService:71`, `InjectService:673/701`.
- Dispatch clearance: `InjectService.resolveLaunchedByClearance` (~l.565) reads the launcher via `findLauncherId(inject.getId())` and the live clearance from `MarkingClearanceCacheManager`, **never** from the HTTP context. Partial execution via `resolveAssetsToExecuteByClearance` (cleared / not cleared split).
- `InjectUtils:373`: `launchedBy` deliberately **not** copied on duplication/relaunch.
- Migration `V6_20261016090000000` added `exercise_launched_by` / `inject_launched_by` (+ scheduled_by).
- `grep` in `service/chaining` for asset-creation calls: nothing, consistent with D1.
- `filterByMarkingClearance` (named in Corinne's report) is **not** found on this branch; probably renamed. Use `resolveAssetsToExecuteByClearance` / `launchableUnderClearance`, and check she tested against the same code.

## 5. Open questions

### Q1 - Chain conditions on hidden findings

A conditional trigger on a RED finding: does it fire for a GREEN launcher? If the RED inject never
executed there is no finding, so probably no trigger. What does the GREEN viewer see for the downstream
step: "skipped", "pending", nothing? (DoD 3: "no chaining outcome from the RED target".) Applies to the
Execution page (S2), shared with time-based simulations.

### Q2 - Discovered endpoints behind a hidden asset

Discovered endpoints are not assets, so they carry no marking. If one is discovered *from* a RED asset
and only reachable through it, D3 implies it must disappear with that path. Confirm with Vertical 1 that
"hide the path of a filtered asset" includes the downstream discoveries.

### Q3 - Masking style for allow / deny lists

Drop the rule, or show a neutral `RESTRICTED` chip (Corinne's proposal)? Dropping hides that a rule
exists; the chip keeps the list shape but reveals a rule exists. **Technically the chip is forced**: the
update endpoint deletes every rule missing from the input (`applyScopeRules`), so a dropped rule would be
deleted when a restricted user saves (7.2). Still a product call for the wording and whether a deny rule
looks different from an allow rule.

### Q4 - Background paths and the launcher

`WorkflowTimeoutJob`, `QueueChainingJob`, `StepDelayQueueService` resume chains without a user
session. Is the launcher always resolvable there? Do chain steps created at runtime get the right
`launchedBy` (see `InjectUtils:373`)?

## 6. S1: derive the attack-path tables from `assets` (feasibility checked, static read only)

### 6.1 What `AttackPathApi` does today

- 8 read endpoints (`graph`, `graph/delta`, `simulations` picker, `endpoint/findings`, `endpoint/relations`,
  `findings`, `execution`, plus the real-time nudge), all `skipRBAC = true`, gated only by
  `assertCanReadSimulation` (`SIMULATION READ`). **Nothing filters on the reader's marking clearance.**
- Reads are plain JPQL projections of `attackpath_execution` / `attackpath_finding` (no join to `assets`),
  and neither table is in `MarkingDerivedTables`. This is the leak; it does **not** explain the empty tab
  (rows never written: launcher not resolved, or the swallowed `Attack-path ingestion skipped` warning).
- Worst endpoint: `execution` returns the raw `command` and `terminal_output` of the run (credential
  masking only). Then `endpoint/findings?ref=<assetId>` and the picker's endpoint/execution counts.

### 6.2 Chosen design: add the tables to `MarkingDerivedTables.FROM_ASSETS`

```java
// a row is hidden when its target OR its source asset is hidden (parents are ANDed, see 6.4)
linkedToAll("attackpath_execution",
    new ParentLink("attackpath_execution_target_asset_id", "assets", "asset_id"),
    new ParentLink("attackpath_execution_source_asset_id", "assets", "asset_id")),
linkedTo("attackpath_execution_finding", "execution_id", "attackpath_execution", "attackpath_execution_id"),
throughLinkRows("attackpath_finding", "attackpath_finding_id", "attackpath_execution_finding", "finding_id")
```

A hidden asset hides its executions, the links to them, and the findings only they produced. Same
pattern as `findings` / `agents`: nothing is copied, re-marking applies instantly, and the filter sits in
SQL so it covers all 8 endpoints, the collapsed-mode aggregates, the delta and the picker counts.

### 6.3 Feasibility checks

| Check | Result |
|---|---|
| NULL asset id | Visible: the predicate is `fk IS NULL OR EXISTS (...)`. Discoveries, injector sources and seed rows stay visible |
| Reader's clearance reaches these reads | Yes. Every endpoint declares `TxCtx`; `HttpMarkingScopeSupplier` derives the clearance from the current user and the tenant scope (admin/bypass sees all; no clearance still sees unmarked rows) |
| Columns exist | Yes (`V6_20260723160000000__Add_attack_path_tables`: `..._target_asset_id`, `..._source_asset_id`, `..._endpoint_id`, link table `attackpath_execution_finding`); the derived-table schema test checks them |
| Activation | `openaev.marking.active-tables=assets` is already the default; derived tables follow it, no new flag |
| Chain resolution | `attackpath_execution_finding` -> `attackpath_execution` -> `assets` resolves hop by hop (`MarkedTables.withDerived`) |
| Query shapes | Attack-path reads are JPQL `@Query`; the only native query is in `AttackPathGraphVersionRepository` and has no asset link. The same statements already pass the tenant v2 inspector, and the marking predicate has the same shape. **Not run** |
| Ingestion writes | Run under system clearance (every marking of the tenant), and the write predicate equals the read one, so they still pass (from the earlier trace of `onRun`) |

**Verdict: implementable**, with the one constraint below.

### 6.4 Several parents per table ✅ (D5)

`MarkedTable` today is marked in exactly one way (one parent link), and `withDerived` rejects a table
listed twice. `attackpath_execution` needs **two** links to `assets` (target and source): the frozen
`source_hostname/ip/platform` are on the row, so a target-only link would still show a RED source of a
RED -> GREEN pivot to a GREEN reader, and a discovery made from a hidden source would stay visible (Q2).

**Decision: let a derived table carry several parent links, ANDed** (hidden if any parent is hidden).
This is the strict reading, the opposite of link rows (`throughLinkRows`, permissive: visible if any link
is). It is deliberate: a row that names a hidden asset in any of its columns must not exist.

Scope of the change (all in `openaev-api`, package `io.openaev.config`; no other consumer of the
single-parent API exists, checked by grep):

| File | Change |
|---|---|
| `MarkedTable` | `parent` becomes `parents` (list of `ParentLink`, still exactly one *shape*: own column, parents, or link rows); keep the single-parent constructor and `linkedTo(...)`; add `linkedToAll(table, ParentLink...)`; `linkedTable()` becomes `linkedTables()`; `linkedColumns()` returns the columns of every parent |
| `MarkedTables` | `requireResolvableParents` walks every parent (graph, still rejecting a cycle or a missing/inactive parent); `withDerived` adds a table once **all** its parents are present |
| `MarkingDimension` | `predicate` emits one `(fk IS NULL OR EXISTS (...))` per parent joined with `AND`, aliases kept distinct (`mkp{depth}_{table}`; two sibling sub-selects may share one, like `linkRowsPredicate`) |
| `MarkingDerivedTables` | the entries of 6.2 |
| `MarkingLinkedTablesTest` | predicate strings for two parents; both-visible / one-hidden cases; cycle and missing-parent checks over several parents; the 3-arg constructor call at l.374 |
| `MarkingLinkedTablesRowsTest` | schema check already loops over `linkedColumns()`; add rows tests (target hidden, source hidden, both null) |

Single-parent tables (`agents`, `injects_assets`, ...) must keep their exact SQL: pin it with the
existing predicate-string tests before changing the code.

This touches the shared marking core: ask its owners to review the `MarkedTable` / `MarkingDimension`
change, and keep it in its own commit separate from the attack-path entries.

### 6.5 Remaining points (not blockers)

- Findings through link rows are **permissive**: a finding produced by a visible and a hidden execution
  stays visible, the same product decision as `findings` through `findings_assets`. A strict variant exists.
- A hidden `ref` returns empty, not 403: no existence oracle, it falls out of the SQL filter.
- `attackpath_execution_collector` carries `..._source_asset_id` and alert JSON and is read by
  `securityPlatformResolver` through the execution id; link it through `attackpath_execution` to be safe.
- The shared `graphVersion` and the nudge reveal that something changed on a hidden asset: minor, leave.
- `executionDetail` resolves security-platform names with `assetRepository.findAllById`; a hidden platform
  becomes a null name (correct, add a test).
- Join cost on big simulations (collapse threshold 20000): one indexed `EXISTS` per row; check `EXPLAIN`
  on >= 100k rows (`performance.instructions.md`).

### 6.6 Next steps

1. Scope confirmation (AGENTS.md "scope before code"): the table in 6.4 plus the entries of 6.2. Then, in order: pin the existing single-parent SQL in tests, the multi-parent change to `MarkedTable` / `MarkedTables` / `MarkingDimension`, then the attack-path entries.
2. Integration test: RED-launched simulation read as GREEN (graph, execution, findings, relations,
   picker counts) is empty; as RED and as admin it is complete.
3. Separately, the empty-tab experiment (grep `Attack-path ingestion skipped`, count `attackpath_execution`
   rows): it is not fixed by this change.


## 7. S5 brainstorm: allow / deny lists (static read only, nothing run)

Two paths, with two different actors. Marking is not a column of `workflow_scope_rules` (the rule value is a
polymorphic string: asset id, group id, IP, subnet, domain...), so the table **cannot** be derived from
`assets` like S1: a rule that holds an IP would read as "points to a hidden asset". The filter lives in the
service layer, with the right clearance for each path.

### 7.1 Read path: the reader's clearance (HTTP)

| Endpoint | Today | Verdict |
|---|---|---|
| `GET /workflows/{id}/configuration` -> `WorkflowConfigurationMapper.toScopeRuleOutput` | Returns every rule: `ruleValue` (asset id), `ruleValueLabel` (asset name), and the launch/end snapshots (asset id, name, agents count, executors), all stored copies. `computeStatus` re-reads the live asset under the reader's scope: a hidden asset throws `ElementNotFoundException` -> `DELETED_DURING_EXECUTION` (the "has been deleted during execution" message), a group with hidden members -> `MODIFIED_DURING_EXECUTION` | **Leak + wrong status** |
| `GET .../valid-assets`, `scope/endpoints`, `scope/asset-groups` (+ `/find`) | `getValidAssets` / `resolveEndpoints` / `resolveAssetGroups` go through `assetService` / `endpointService` / `assetGroupService`, i.e. JPA reads of the marked `assets` table under the reader's `TxCtx` | Filtered by the inspector (static read; cover with a test) |

Design: **mask, never drop.**
- One batched `assetService.assets(ids)` call under the reader's scope gives the visible asset ids (no
  per-rule lookup, no N+1). Pass that set to the mapper and to `computeStatus`.
- An `ASSET` rule whose asset is hidden stays in the list with its stable `id`, blank value/label/snapshots
  and a neutral `RESTRICTED` status (Corinne's chip).
- An `ASSET_GROUP` rule stays, with the snapshot assets and the live members both **cut to the visible
  ids** before the signature is computed, so a visible group never reads `MODIFIED_DURING_EXECUTION` just
  because members are hidden. Why this is needed (asset groups traced, static read):
  - The group itself is not a marked entity: `asset_groups` has no `marking_ids` and is not derived, so its
    name, description and `dynamicFilter` are visible to every reader, as in a classic simulation. Only the
    **members** are filtered.
  - Live members are filtered by the inspector: the static members come from the `asset_groups_assets` join
    table (derived from `assets`), and the dynamic members (`computeDynamicAssets`) are a query on `assets`.
    So `/scope/asset-groups` (`assetGroups(ids)` + `AssetGroupMapper`, which only outputs member **ids**) and
    `getAssetsAllowedByAssetGroup` / `getDeniedAssetIds` (`assetsFromAssetGroup`) already return the visible
    members only under the reader's clearance.
  - The **frozen** members are not: `ScopeSnapshotService.resolveAssetGroupSnapshot` stores every member the
    freezer could see (id, name, agents count, executors) in the rule's JSON snapshot. `freezeLaunch` runs in
    the launching user's scope (`WorkflowService` run copy), `freezeEnd` in whoever ends the run (timeout job
    or queue consumer, or the user who stops it). So the launch photo of a GREEN launcher holds the GREEN
    members only, the end photo frozen by the system holds all of them, and the diff reads
    `MODIFIED_DURING_EXECUTION` for everyone, admin included. The same photo, read by a GREEN user, shows the
    names of the RED members. Hence both fixes: freeze at system clearance (7.2) so the photo is
    deterministic, and cut it to the reader's visible ids on read.
  - The rule's `ruleValueLabel` is the group name: not sensitive, kept.
- IP / subnet / domain / manual / CSV rules are not assets: untouched (an author-typed IP that happens to
  match a hidden endpoint is out of scope here).
- Why "mask, never drop" (answers Q3): see 7.2.

### 7.2 Write path traps (the same endpoints, edited by a restricted user)

- `WorkflowService.applyScopeRules` removes every existing rule absent from the input (`removeIf(!inputIds.contains(id))`).
  If the read path **dropped** hidden rules, a GREEN user saving the form would delete the RED rules of the
  admin. So the placeholder keeps its id, and `applyScopeRules` must never remove or update a rule whose
  asset the writer cannot see.
- `realignTemplateActionTargets` -> `StepService.syncScopeAssetsOnStepTemplates` overwrites `inject_assets`
  of every asset-centric step template with `getValidAssets(...)` computed under the **writer's** clearance:
  a GREEN edit strips the RED assets from the templates. Compute it at system clearance, or keep the hidden
  ids already on the template.
- This is Corinne's "Part A" (write at system clearance) plus a protect-on-write rule she did not list.

### 7.3 Execution path: same gate as a classic simulation (static read)

A chained step runs through the **same dispatch as a classic inject**, so most of it is already parity:

| Aspect | Classic simulation | Chained step | Same? |
|---|---|---|---|
| Launcher | `Exercise.launchedBy`, stamped at start | Same: the step's inject gets `setExercise(simulation)` (`InjectExecutionStep` l.737), so `InjectRepository.findLauncherId` returns the simulation's launcher | Yes |
| Dispatch | `Executor.execute` -> `resolveAssetsToExecuteByClearance` (cleared / restricted split) | `InjectExecutionStep.run` -> `executor.directExecute` -> the same `Executor.execute` | Yes |
| Restricted target | Dropped, traced `ASSET_CLEARANCE_RESTRICTED` "Asset not targeted" (`ExecutionExecutorService`) | Same code | Yes |
| Target entity | Real asset entity, markings loaded | `applyChainingTarget` loads it with `assetService.asset(id)`: a real entity, **not a stub**. The stub concern from the earlier trace (cause 3 of the empty tab) does not apply to this path | Yes |
| Targets per inject | One inject, all targets inside it | `expandTargetBatches` creates **one step and one inject per scoped asset**, hidden ones included (background scope sees every asset) | Different shape, same outcome: a restricted asset gets its own inject with one hidden target, nothing runs, one "Asset not targeted" trace |

Conclusion: **do not filter the expansion.** Dropping hidden assets there would erase the "not targeted" trace
that cleared users (admin, auditor) get in a classic simulation, and the dispatch gate stays the single
enforcement point, as in classic. This replaces my earlier proposal (filter at expansion + a shared
launch-clearance component + a launcher-by-workflow query): not needed, unless the allowlist seed below is
filtered.

Remaining execution-path points, all small:
- `WorkflowService.extractScopeStateSeed` puts the ids of hidden assets/groups in the global state. The state is
  engine-only (no endpoint) and the dispatch gate still blocks any execution on them: low. Filtering it would
  need the launcher's clearance (then the shared component and the workflow launcher query come back). Leave
  unless a mapper is found to expose it.
- The `// TODO filter here assets from markings` on `Workflow.getAllowlist()` cannot be done in the entity
  (no clearance there) and the dispatch gate already covers it: drop the TODO. `getDenylist()` has no caller in
  `openaev-api` main.
- The agentless "not targeted" trace is keyed by the asset id (`saveAssetsWithoutClearanceTraces`). That is
  existing classic behaviour, shared: if it needs masking, fix it once for both, not in chaining.
- A test must pin the parity: chained **and** autonomous run, RED + GREEN asset with agents, GREEN launcher:
  the RED asset gets its inject and the trace, nothing executes, the GREEN reader sees no RED asset.

### 7.4 Coverage matrix: the chaining tables

None of these tables is in `MarkingDerivedTables` (it only lists tables derived from `assets`: `injects_assets`,
`asset_groups_assets`, `findings_assets`, `findings`, `injects_expectations`, `agents`, `execution_traces`).
What matters is whether an endpoint exposes them and whether they hold asset data.

| Table | Holds | Read over HTTP | Verdict |
|---|---|---|---|
| `workflows`, `workflow_scope_rules` | Allow/deny rules: asset ids, names, launch/end snapshots | `GET /workflows/{id}/configuration` | **Leak (S5)**, fix in 7.1 / 7.2 |
| `scope_variables` | Named variables (credential values already masked) | Same config response | Not asset data |
| `steps` TEMPLATE | `step_data`: the whole inject JSON, including `inject_assets` (asset ids) | `StepApi` (`findById`, `findByWorkflowId`) returns `StepOutput.data` raw | **Minor leak**: a GREEN reader sees the ids of hidden assets (ids only, no names). In a classic simulation the same links sit in `injects_assets` (derived, hidden); here they are JSON, so the inspector does not see them |
| `steps` READY / RUN | Per-asset target baked into `step_data` | No controller | Engine only |
| `workflow_state`, `workflow_state_entries` | Global pool: IPs, finding values, outputs | No controller | Engine only; reaches readers only through the derived views (findings, attack path S1) |
| `step_delay_queue` | Delayed steps | No controller | Engine only |
| `conditions`, `condition_steps` | Key, key types, operator, comparison value, case sensitivity | `ConditionApi` -> `ConditionOutput.value` raw | **Low / unverified**: the value is author-typed text (IP, port...). There is an `Asset` key type; I did not trace what an `Asset` condition value holds (an asset id?) |

Fix for the template ids (same idea as 7.1): `StepMapper.toOutput` is static and has no clearance, so pass it
the visible asset ids (one batched read under the reader's scope) and cut `inject_assets` to them. Same trap as
7.2 on the write side: a template saved by a restricted editor must not drop the hidden ids (that is the
`realignTemplateActionTargets` fix).

### 7.5 Plan

| Step | Content |
|---|---|
| 1 | Pin the dispatch parity with a RED/GREEN test (chained and autonomous): inject created, no execution, trace, GREEN reader sees nothing |
| 2 | Read path: mask in `WorkflowConfigurationMapper` + visible-set-aware `computeStatus`; new `RESTRICTED` status (API type + front chip) |
| 3 | Read path: cut `inject_assets` in `StepMapper` to the reader's visible ids; check what an `Asset` condition value holds |
| 4 | Write path: `applyScopeRules` ignores rules the writer cannot see; `realignTemplateActionTargets` keeps hidden ids on the templates |
| 5 | Tests per the `add-test` skill: GREEN reader, GREEN editor, admin, on `/configuration`, `/valid-assets`, `/scope/*`, `StepApi` |

### 7.6 Audit log and export (static read only)

| Channel | What I found | Verdict |
|---|---|---|
| Audit log (`@AuditDiffTracked`) | Only `Group` and `Role` carry it. Workflows, scope rules, steps, conditions and assets are not audit-diff tracked | Nothing to filter |
| Export (`WorkflowExportInitializer`, used by `ScenarioService` and `ExportService`) | Exports the **TEMPLATE** workflow, not the run. `filterAssetScopeRules` drops every ASSET / ASSET_GROUP rule (`WorkflowScopeRuleUtils.isAssetScopeRule`), and `normalizeStepDataFieldsForExport` removes `inject_assets`, `inject_asset_groups`, `inject_exercise`, `inject_scenario` from the step data. No asset name or id leaves through the workflow export. IP / subnet / domain rules stay (not assets) | OK. One loose end: `inject_content` (`targets_assets`, `targets`) is not stripped; check whether the Configure-action drawer ever stores asset ids there |
