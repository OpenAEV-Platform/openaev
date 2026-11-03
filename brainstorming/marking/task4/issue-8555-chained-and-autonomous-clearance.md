# #8555 - Marking clearance in chained simulations and autonomous runs

> Brainstorm notes. Issue: https://github.com/OpenAEV-Platform/openaev/issues/8555
> Parent: #8552 (US2) - Epic #8171 - Task.4 Chunk 7 (size M) - Labels: backend, feature, feature-flag
> Assignee: damgouj - Branch: `issue/8555`
> Status: scope confirmed (section 8), checklist decisions recorded (section 9), open points and checks in sections 9.2 and 10, **no code yet**. Elsewhere in this file, nothing is decided unless marked ✅.

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
| D4 | S1 is fixed by deriving `attackpath_execution` / `attackpath_finding` from `assets` in `MarkingDerivedTables` (section 5). Feasibility checked statically (section 5). | ✅ |
| D5 | `attackpath_execution` is linked to `assets` through **one generated column** holding the row's single asset (`CASE` on the source kind, then source or target asset): real ingestion never writes two different assets in one row (5.4) | ✅ |

## 3. What must be filtered (chaining read surfaces)

Because chaining stores its own denormalized copies (names, IPs, snapshots, values) in tables that are
**not marked tables**, the statement inspector does not filter them. Each surface below needs an
explicit read-side filter or mask, using the *reader's* clearance.

| # | Surface | What leaks / is wrong today | Direction | Status |
|---|---|---|---|---|
| S1 | **Attack paths** | `attackpath_execution` / `attackpath_finding` store `targetHostname`, `targetIp`, `targetPlatform`, `sourceHostname/Ip/Platform`, `agentName`, `targetRawValue`, finding values. Only `SIMULATION READ` is checked. A GREEN reader sees a RED host's name/IP/findings if a RED user launched. Separate symptom: the tab is **empty** for any marked asset (see section 5) | Hide a path when its asset is filtered for the reader. Filter/mask in `AttackPathGraphService`, or derive the tables from `assets` in `MarkingDerivedTables` (`targetAssetId` exists) | To fix |
| S2 | **Execution page** | Not a chaining-specific page: `simulation/Index.tsx` routes `execution/*` to the same `ExecutionOverview` (injects, expectations, teams, traces) for chained and time-based simulations. Its data comes from `injects_assets`, `injects_expectations`, `agents`, `execution_traces`, all already in `MarkingDerivedTables` | Nothing to add | **Already covered** (static read, confirm in the RED/GREEN test) |
| S3 | **Findings page** | Same shared route: `findings` renders `SimulationFindings` -> `searchDistinctFindingsForSimulations` -> `FindingDistinctSearchService` (JPA specification on `findings`), and `findings` / `findings_assets` are already derived from `assets`. Matches Damien's test (finding hidden for GREEN). The chaining-only view of findings is the attack path (S1) | Nothing to add | **Already covered** (static read) |
| S4 | **Statistics** (e.g. dashboard chaining stats) | Not traced yet. Aggregates can leak counts of hidden executions/findings if built from unmarked tables | Compute stats from filtered data only | **Sub-issue** (linked to the dashboards POC), not in #8555 |
| S5 | **Asset allow / deny lists** (`workflow_scope_rules`) | Not a marked table: saved label and JSON snapshots at launch/end bypass the inspector. `WorkflowConfigurationMapper.toScopeRuleOutput` has no visibility check; `ScopeSnapshotService.computeStatus` reads a hidden asset as "deleted". Deny rules naming a RED asset leak its existence the same way | Two paths, see section 6: read = reader's clearance (drop the rules of hidden assets in the mapper), execution = launcher's clearance at dispatch, already the same as classic: do **not** filter the step expansion (6.3). Not derivable like S1 (polymorphic rule value) | To fix |


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
- `filterByMarkingClearance` (quoted in the issue comments) is **not** found on this branch; probably renamed. Use `resolveAssetsToExecuteByClearance` / `launchableUnderClearance`.

## 5. S1: derive the attack-path tables from `assets` (feasibility checked, static read only)

### 5.1 What `AttackPathApi` does today

- 8 read endpoints (`graph`, `graph/delta`, `simulations` picker, `endpoint/findings`, `endpoint/relations`,
  `findings`, `execution`, plus the real-time nudge), all `skipRBAC = true`, gated only by
  `assertCanReadSimulation` (`SIMULATION READ`). **Nothing filters on the reader's marking clearance.**
- Reads are plain JPQL projections of `attackpath_execution` / `attackpath_finding` (no join to `assets`),
  and neither table is in `MarkingDerivedTables`. This is the leak; it does **not** explain the empty tab
  (rows never written: launcher not resolved, or the swallowed `Attack-path ingestion skipped` warning).
- Worst endpoint: `execution` returns the raw `command` and `terminal_output` of the run (credential
  masking only). Then `endpoint/findings?ref=<assetId>` and the picker's endpoint/execution counts.

### 5.2 Chosen design: add the tables to `MarkingDerivedTables.FROM_ASSETS`

```java
// a row is hidden when its single asset is hidden: generated column, see 5.4
linkedTo("attackpath_execution", "attackpath_execution_asset_id", "assets", "asset_id"),
linkedTo("attackpath_execution_finding", "execution_id", "attackpath_execution", "attackpath_execution_id"),
throughLinkRows("attackpath_finding", "attackpath_finding_id", "attackpath_execution_finding", "finding_id")
```

A hidden asset hides its executions, the links to them, and the findings only they produced. Same
pattern as `findings` / `agents`: nothing is copied, re-marking applies instantly, and the filter sits in
SQL so it covers all 8 endpoints, the collapsed-mode aggregates, the delta and the picker counts.

### 5.3 Feasibility checks

| Check | Result |
|---|---|
| NULL asset id | Visible: the predicate is `fk IS NULL OR EXISTS (...)`. Discoveries, injector sources, team and person rows stay visible. **Not the seed rows**: they carry fake asset ids that are not in `assets`, so they are hidden once marking is active (5.4) |
| Reader's clearance reaches these reads | Yes. Every endpoint declares `TxCtx`; `HttpMarkingScopeSupplier` derives the clearance from the current user and the tenant scope (admin/bypass sees all; no clearance still sees unmarked rows) |
| Columns exist | `..._target_asset_id`, `..._source_asset_id` and the link table `attackpath_execution_finding` exist (`V6_20260723160000000__Add_attack_path_tables`); **`attackpath_execution_asset_id` is new** (generated column, migration of 5.4). The derived-table schema test checks the columns |
| Activation | `openaev.marking.active-tables=assets` is already the default; derived tables follow it. The whole marking filter is also gated by the `MARKING` preview feature (`openaev.enabled-dev-features`, `MarkingFilteringConfig.markedTables` returns `MarkedTables.EMPTY` when off), so the new entries switch off with it. No new flag |
| Chain resolution | `attackpath_execution_finding` -> `attackpath_execution` -> `assets` resolves hop by hop (`MarkedTables.withDerived`) |
| Query shapes | Attack-path reads are JPQL `@Query`; the only native query is in `AttackPathGraphVersionRepository` and has no asset link. The same statements already pass the tenant v2 inspector, and the marking predicate has the same shape. **Not run** |
| Ingestion writes | Run under system clearance (every marking of the tenant), and the write predicate equals the read one, so they still pass (from the earlier trace of `onRun`) |

**Verdict: implementable**, with the one constraint below.

### 5.4 One asset per row: a generated column ✅ (D5)

**Why one link is enough.** `attackpath_execution` has a source and a target asset id, but real ingestion
never writes two different assets in one row:

| Shape (written by) | Source asset | Target asset |
|---|---|---|
| Agent runs on asset A: executable, file drop, AI attack, command, DNS, traffic | A | A (same asset), or **NULL** when the target is a discovered host |
| Injector hits asset B (`setSourceInjectorTargetAsset`) | NULL (an injector) | B |
| Injector to a discovered host, team or person; team to person | NULL, or a **team id** (see below) | NULL |

So each row is judged alone by its one asset: no cascade between rows is needed. A target-only link misses
"agent on RED scans a discovered host" (target NULL); a source-only link misses "injector hits RED"
(source NULL). The row needs the asset of whichever side has one.

**Pitfall found while checking: team rows.** `setSourceTeamInformation` stores a **team id in
`source_asset_id`**. A plain link on that column would hide every team -> person row (phishing steps) from
every non-bypass reader, because a team id matches no asset and the `EXISTS` is false. Hence the `CASE`.

**Decision: a generated column**, linked to `assets` in `MarkingDerivedTables` (5.2):

```sql
ALTER TABLE attackpath_execution ADD COLUMN IF NOT EXISTS attackpath_execution_asset_id varchar(255)
  GENERATED ALWAYS AS (
    CASE WHEN attackpath_execution_source_kind = 'AGENT'
         THEN COALESCE(attackpath_execution_source_asset_id, attackpath_execution_target_asset_id)
         ELSE attackpath_execution_target_asset_id END) STORED;
```

Flyway Java migration (`add-migration` skill). The entity does not need to map the column: the marking
predicate is SQL. Existing rows are computed by the table rewrite, no backfill.

| Point | Status |
|---|---|
| Discovery made from a hidden asset | Hidden: its row is an agent row, the source asset is on it |
| Write side | `ScopeStatementInspector.rewriteInsert` validates only `INSERT ... SELECT`; a plain `VALUES` insert (what JPA writes) is not checked, and update / delete / read use the column of the existing row. Read from the code, **not run** |
| Invariant it relies on | "A row never holds two different assets". If a future ingestion case breaks it, the source wins and the target may leak. **Guard test** on the real ingestion paths (6 cases above) |
| Migration cost | `ADD COLUMN ... GENERATED ... STORED` rewrites the table under an exclusive lock. Tables date from `V6_20260723`, size unknown on large installs. Accepted unless they are big; alternative: plain nullable column, batched backfill, setters keeping it in sync (more code). Needs PostgreSQL 12+ (check the minimum supported version) |
| Seed data | `AttackPathCausalSeedService` (reached through `AttackPathApi`, demo / test seeder) writes **fake asset ids** (`ep-dc-01`, transient endpoints not in `assets`): once marking is active these rows are hidden for non-bypass readers. Its `agentPivotExecution` is the only writer of two different assets (the source wins). The seed must create real assets, or reviewers must not rely on it |
| Rows to test | injector -> RED asset hidden for GREEN; agent on RED -> discovered hidden; agent on RED local hidden; team -> person visible to all; GREEN rows visible; hidden `ref` empty |

### 5.5 Remaining points (not blockers)

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

### 5.6 Next steps

1. Scope confirmed (section 8). In order: the generated-column migration and its guard test, then the attack-path entries in `MarkingDerivedTables`.
2. Integration test: RED-launched simulation read as GREEN (graph, execution, findings, relations,
   picker counts) is empty; as RED it is complete (no admin case here: the bypass is handled by another PR).
3. Separately, the empty-tab experiment (grep `Attack-path ingestion skipped`, count `attackpath_execution`
   rows): it is not fixed by this change.


## 6. S5 brainstorm: allow / deny lists (static read only, nothing run)

Two paths, with two different actors. Marking is not a column of `workflow_scope_rules` (the rule value is a
polymorphic string: asset id, group id, IP, subnet, domain...), so the table **cannot** be derived from
`assets` like S1: a rule that holds an IP would read as "points to a hidden asset". The filter lives in the
service layer, with the right clearance for each path.

### 6.1 Read path: the reader's clearance (HTTP)

| Endpoint | Today | Verdict |
|---|---|---|
| `GET /workflows/{id}/configuration` -> `WorkflowConfigurationMapper.toScopeRuleOutput` | Returns every rule: `ruleValue` (asset id), `ruleValueLabel` (asset name), and the launch/end snapshots (asset id, name, agents count, executors), all stored copies. `computeStatus` re-reads the live asset under the reader's scope: a hidden asset throws `ElementNotFoundException` -> `DELETED_DURING_EXECUTION` (the "has been deleted during execution" message), a group with hidden members -> `MODIFIED_DURING_EXECUTION` | **Leak + wrong status** |
| `GET .../valid-assets`, `scope/endpoints`, `scope/asset-groups` (+ `/find`) | `getValidAssets` / `resolveEndpoints` / `resolveAssetGroups` go through `assetService` / `endpointService` / `assetGroupService`, i.e. JPA reads of the marked `assets` table under the reader's `TxCtx` | Filtered by the inspector (static read; cover with a test) |

Design: **drop what the reader cannot see**, as in a classic simulation.
- One batched `assetService.assets(ids)` call under the reader's scope gives the visible asset ids (no
  per-rule lookup, no N+1). Pass that set to the mapper and to `computeStatus`.
- An `ASSET` rule whose asset is hidden is **not returned**: no placeholder, no new status, its name, id and
  snapshots never leave. The save endpoint must not delete it (6.2).
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
    names of the RED members. Hence both fixes: freeze at system clearance (step 1b of 6.5) so the photo is
    deterministic, and cut it to the reader's visible ids on read.
  - The rule's `ruleValueLabel` is the group name: not sensitive, kept.
- IP / subnet / domain / manual / CSV rules are not assets: untouched (an author-typed IP that happens to
  match a hidden endpoint is out of scope here).
- Why dropping is safe on the write side: see 6.2.

### 6.2 Write path traps (the same endpoints, edited by a restricted user)

- `WorkflowService.applyScopeRules` removes every existing rule absent from the input (`removeIf(!inputIds.contains(id))`).
  Since the read path **drops** hidden rules, a GREEN user saving the form would delete the RED rules of the
  admin. So `applyScopeRules` must only remove or update the rules whose asset the writer can see (one batched
  visible-ids read under the writer's scope); hidden rules are left untouched. **Critical now.**
- `realignTemplateActionTargets` -> `StepService.syncScopeAssetsOnStepTemplates` overwrites `inject_assets`
  of every asset-centric step template with `getValidAssets(...)` computed under the **writer's** clearance:
  a GREEN edit strips the RED assets from the templates. Compute it at system clearance, or keep the hidden
  ids already on the template.
- Together with the snapshots written at system clearance (step 1b of 6.5), this is the protect-on-write rule.

### 6.3 Execution path: same gate as a classic simulation (static read)

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
- Background paths (queue consumer, `QueueChainingJob` delay queue, `WorkflowTimeoutJob`): no user session is
  needed. `InjectExecutionStep.run` saves the inject linked to the simulation, and `InjectService.resolveLaunchedByClearance`
  reads the launcher from the database (`InjectRepository.findLauncherId`: the simulation's `launchedBy` when the
  inject has one, the inject's own only for atomic testing, so `InjectUtils:373` does not apply). A missing launcher
  (deleted user, old row) gives zero clearance, fail-closed. Static read: pin it with a test of a step resumed from
  the delay queue.
- A test must pin the parity: chained **and** autonomous run, RED + GREEN asset with agents, GREEN launcher:
  the RED asset gets its inject and the trace, nothing executes, the GREEN reader sees no RED asset.

### 6.4 Coverage matrix: the chaining tables

None of these tables is in `MarkingDerivedTables` (it only lists tables derived from `assets`: `injects_assets`,
`asset_groups_assets`, `findings_assets`, `findings`, `injects_expectations`, `agents`, `execution_traces`).
What matters is whether an endpoint exposes them and whether they hold asset data.

| Table | Holds | Read over HTTP | Verdict |
|---|---|---|---|
| `workflows`, `workflow_scope_rules` | Allow/deny rules: asset ids, names, launch/end snapshots | `GET /workflows/{id}/configuration` | **Leak (S5)**, fix in 6.1 / 6.2 |
| `scope_variables` | Named variables (credential values already masked) | Same config response | Not asset data |
| `steps` TEMPLATE | `step_data`: the whole inject JSON, including `inject_assets` (asset ids) | `StepApi` (`findById`, `findByWorkflowId`) returns `StepOutput.data` raw | **Minor leak**: a GREEN reader sees the ids of hidden assets (ids only, no names). In a classic simulation the same links sit in `injects_assets` (derived, hidden); here they are JSON, so the inspector does not see them |
| `steps` READY / RUN | Per-asset target baked into `step_data` | No controller | Engine only |
| `workflow_state`, `workflow_state_entries` | Global pool: IPs, finding values, outputs | No controller | Engine only; reaches readers only through the derived views (findings, attack path S1) |
| `step_delay_queue` | Delayed steps | No controller | Engine only |
| `conditions`, `condition_steps` | Key, key types, operator, comparison value, case sensitivity | `ConditionApi` -> `ConditionOutput.value` raw | **Low / unverified**: the value is author-typed text (IP, port...). There is an `Asset` key type; I did not trace what an `Asset` condition value holds (an asset id?) |

Fix for the template ids (same idea as 6.1): `StepMapper.toOutput` is static and has no clearance, so pass it
the visible asset ids (one batched read under the reader's scope) and cut `inject_assets` to them. Same trap as
6.2 on the write side: a template saved by a restricted editor must not drop the hidden ids (that is the
`realignTemplateActionTargets` fix).

### 6.5 Plan

> Detailed content of the S5 work. **Section 8 is authoritative for the order** (six commits, the attack-path tables of 5.4 are its commit 2, the parity test sits in its commit 6).

| Step | Content |
|---|---|
| 1 | Pin the dispatch parity with a RED/GREEN test (chained and autonomous): inject created, no execution, trace, GREEN reader sees nothing |
| 1b | **Test-first** (settles whether a restricted user's cancel / reset mutates the snapshots, unconfirmed): as GREEN, cancel / reset / start a simulation that targeted a RED asset, then read the snapshots through the repository (no admin read: the bypass is another PR) and assert they are unchanged; it must fail before the fix and pass after (if it already passes, drop the cancel / reset part). Also check that `filterByMarkingClearance` (quoted in the issue comments) is the same code as `resolveAssetsToExecuteByClearance`. **Fix**: write the scope snapshots at system clearance with `TenantScopedTransaction.withSystemClearance` (mechanism from #8767, commit `59ceebf685`, already on this branch): `ScopeSnapshotService.freezeLaunch` (call site `WorkflowService:549`), `freezeEnd` (`WorkflowEndService:225`), cancel, reset, start. `computeStatus` compares the system-clearance snapshots, then the read path cuts them per reader. #8767 only wraps the 7 copy endpoints (`FaithfulCopyTransaction`, restricted by `FaithfulCopyCallersArchTest`): chaining has no call yet. Check first whether the scenario-launch path already runs `freezeLaunch` inside the faithful copy |
| 2 | Read path: drop the rules of hidden assets in `WorkflowConfigurationMapper`; cut group snapshots and live members to the reader's visible ids; visible-set-aware `computeStatus`. No new status, no front change |
| 3 | Read path: cut `inject_assets` in `StepMapper` to the reader's visible ids; check what an `Asset` condition value holds |
| 4 | Write path (critical: the read path drops hidden rules): `applyScopeRules` ignores rules the writer cannot see; `realignTemplateActionTargets` keeps hidden ids on the templates |
| 5 | Tests per the `add-test` skill, GREEN reader and GREEN editor only (no admin cases: the bypass is handled by another PR; "still there" assertions go through the repository or a RED reader). Groups: snapshots at system clearance (launch / cancel / reset / start), group status, hidden asset rules not returned on `/configuration`, unchanged save keeps the RED rule, template save keeps hidden ids, `/scope/asset-groups` and `/valid-assets` return visible members only |

### 6.6 Audit log and export (static read only)

| Channel | What I found | Verdict |
|---|---|---|
| Audit log (`@AuditDiffTracked`) | Only `Group` and `Role` carry it. Workflows, scope rules, steps, conditions and assets are not audit-diff tracked | Nothing to filter |
| Export (`WorkflowExportInitializer`, used by `ScenarioService` and `ExportService`) | Exports the **TEMPLATE** workflow, not the run. `filterAssetScopeRules` drops every ASSET / ASSET_GROUP rule (`WorkflowScopeRuleUtils.isAssetScopeRule`), and `normalizeStepDataFieldsForExport` removes `inject_assets`, `inject_asset_groups`, `inject_exercise`, `inject_scenario` from the step data. No asset name or id leaves through the workflow export. IP / subnet / domain rules stay (not assets) | OK. One loose end: `inject_content` (`targets_assets`, `targets`) is not stripped; check whether the Configure-action drawer ever stores asset ids there. **Traced `Mixins.java` (the "export of labels" point of the issue comments)**: `WorkflowScopeRuleExport` whitelists mode, source, value, value type and value label (the asset name) but no snapshot; both export paths (`ScenarioService:969`, `ExportService:121`) call `enrichWorkflowDataForExport`, whose `filterAssetScopeRules` drops every ASSET / ASSET_GROUP rule (by source or value type) before output, and no other code serializes the rules. No asset name or id leaves through the label export: closed, the `inject_content` point above stays to check |


## 7. Reviewer guide (to write, 9.2 A2)

Not written yet: setup (markings, RED / GREEN users, assets with agents), the runs (chained, 3 autonomous entry points, RED control) and what to check per tab as GREEN and RED. To be put in the PR description (`### Testing Instructions`) once the behaviour is final.


## 8. Scope of the PR ✅ (confirmed, no code yet; the implementation plan comes later)

> AGENTS.md "scope before code". File names come from the notes and greps: confirm each path when opening it.
> One commit per block, in this order.

| # | Commit | Content |
|---|---|---|
| 1 | **Generated column for the row's asset** | Flyway migration adding `attackpath_execution_asset_id` (generated, `CASE` on the source kind), `add-migration` skill. Guard test on the real ingestion paths: a row never holds two different assets, and a team row never gets an asset (5.4). Seed fix if kept (real assets) |
| 2 | **Attack-path tables (S1)** | `MarkingDerivedTables`: `attackpath_execution` linked through the new column, the finding link tables, `attackpath_execution_collector` (5.2, 5.5). Derived-table schema test, rows tests (5.4), attack-path read tests, GREEN reader only |
| 3 | **Snapshots at system clearance** (test-first) | Test first (GREEN cancel / reset / start, snapshots unchanged). `ScopeSnapshotService` `freezeLaunch` (`WorkflowService:549`) and `freezeEnd` (`WorkflowEndService:225`), cancel / reset / start paths, `withSystemClearance` from #8767. Maybe `FaithfulCopyCallersArchTest` if the wrapper is reused. Flag-off test |
| 4 | **Read path: hidden rules dropped** | `WorkflowConfigurationMapper`, `ScopeSnapshotService.computeStatus` (visible-set aware, group members cut), `StepMapper` (`inject_assets` cut). No front change, no new status |
| 5 | **Write-path protection** | `WorkflowService.applyScopeRules` (never remove or update a rule the writer cannot see), `realignTemplateActionTargets` / `StepService.syncScopeAssetsOnStepTemplates` (keep hidden ids) |
| 6 | **Tests** | Dispatch parity: chained and the 3 autonomous entry points (create, restart, promote to real run), GREEN launcher, RED launcher as control. Matrix of 6.5 step 5, GREEN reader and editor only |

**Checks and investigations, done in this PR**
- Empty Attack Path tab: grep `Attack-path ingestion skipped`, count `attackpath_execution` rows, fix here (5.6 point 3).
- Background paths: add a test that a step resumed from the delay queue runs under the simulation's launcher (6.3).
- What an `Asset` condition value holds (6.4); `inject_content` `targets_assets` / `targets` (6.6).
- Remove the `// TODO filter here assets from markings` on `Workflow.getAllowlist()` (6.3).

**Out of this PR**
- S4 statistics: sub-issue, linked to the dashboards POC.
- Admin / bypass cases: handled by another PR (mention it in the PR description, the issue DoD says "admin sees everything").
- `graphVersion` nudge: minor, left as is (5.5).


## 9. Decisions of the issue checklist (review of the notes against #8555, 2026-11-03)

> Result of comparing these notes with the issue body and its two comments (damgouj 28 Oct, corinnekrych 29 Oct).
> The issue text was read through a condensed page fetch: re-check the Definition of Done wording on GitHub.

### 9.1 Decided ✅

> Refs A1 to D4 are the items of this review's checklist. The D ones are written CL-D1 to CL-D4 so they are not confused with D1 to D5 of section 2.

| Ref | Topic | Decision |
|---|---|---|
| A3 | Parked items | **S4 statistics: sub-issue** (linked to the dashboards POC). Everything else stays in this PR: empty Attack Path tab (investigate then fix), `inject_assets` ids in step templates, `Asset` condition value, `inject_content`, permissive link-row rule (recorded in the issue) |
| A4 | DoD 2 test | One parameterised test, chained + autonomous groups, agents on both targets, RED-launcher control case. Autonomous covers the **3 entry points** (create, restart, promote to real run). **No admin read here**: the bypass is handled by another PR (say so in the PR description) |
| A5 | Conditional steps on a hidden finding | **Option 1**: do nothing special. The downstream step shows its normal state, derived from the `Asset not targeted` trace. The test asserts no RED finding value, name or output leaks |
| B1 | Snapshots at system clearance | Added as step 1b of 6.5 and as commit 3 of section 8: snapshots written at system clearance with `withSystemClearance` |
| B2 | #8767 dependency | Resolved: commit `59ceebf685` is on `main` and on this branch. It provides `withSystemClearance` but wraps only the 7 copy endpoints, chaining has no call yet |
| B3 | Unconfirmed claim (cancel / reset by a restricted user mutates the snapshots) | **Test-first**: GREEN cancel / reset / start, snapshots unchanged. Must fail before the fix. Also check `filterByMarkingClearance` is the same code as `resolveAssetsToExecuteByClearance` |
| B4 | Test matrix | Accepted (snapshots, group status, hidden rules not returned, unchanged save, template save, group filtering), **GREEN reader and editor only** (no admin) |
| B5 | `Mixins.java` | Traced, closed (row 6.6): no asset name or id leaves through the export of labels. `/valid-assets` is covered by the B4 matrix |
| B6 | No `RESTRICTED` status | **Dropped**: a rule on a hidden asset is not returned, as a hidden asset is absent from a classic simulation. No status, chip, API type or i18n key. The write-path protection (commit 5) becomes critical |
| C1 / C2 | Asset creation, asset resolution | "Chaining does not create assets and none is planned. Discovered endpoints are shown in the attack path by IP only, so no marking policy for created assets is needed" (code search of `service/chaining`). Resolution of assets: see 6.3 |
| CL-D1 | Feature flag | No new flag. The `MARKING` preview feature (`openaev.enabled-dev-features`) already empties `MarkedTables`, so the SQL filter and derived tables switch off with it. The read-path filter works from "visible ids under the reader's scope", so it is inert when the flag is off. **Add flag-off tests**: no rule dropped, no group cut, and `withSystemClearance` stays a no-op (not checked yet: what `systemClearanceFor` returns when `MarkedTables` is empty) |
| CL-D2 | Scope before code | Confirmed, see section 8 |
| CL-D3 | Docs | No docs in this PR, done later (run `review-docs` once the code exists) |
| CL-S1 | S1 design (D5) | **Generated column** (5.4), commit 1 is the migration. Why: a row never holds two different assets in real ingestion, and a team row stores a team id in `source_asset_id` (a plain link would hide it for everybody). Guard test on the ingestion paths; seed data hidden unless it uses real assets |
| CL-D4 | Performance | No new index expected (the asset id columns have no FK, reads filter on `simulation_id` first, the predicate is one PK lookup per row). **Measure**: `EXPLAIN (ANALYZE)` on 100k+ rows after commit 2, predicate on and off. Add an index migration only if needed. Record the result in the PR description |

### 9.2 Still open

| Ref | Topic | What is missing |
|---|---|---|
| A1 | Meeting with Vertical 1 / 3 | The DoD requires it to be held and the expected behaviour recorded in the issue. Status unknown, TODO "Check with Eva" is still there. Needs an issue comment recording the expected behaviour (9.1) and asking Vertical 1 to confirm three points: a discovery is hidden with the path of the hidden asset it was made from; a rule on a hidden asset is dropped, not shown as restricted; a finding produced by a visible and a hidden step stays visible |
| A2 | Reviewer worked example | DoD 5. Not written yet (setup, RED / GREEN users, what to check per tab). Plan: PR description `### Testing Instructions` |
| CL-D1 | `systemClearanceFor` with flag off | To check before commit 3 (see section 10) |


## 10. To check before the implementation plan

**Facts to verify in the code**
- [ ] Locate the cancel, reset and start paths that must write snapshots at system clearance (commit 3): only `freezeLaunch` (`WorkflowService:549`) and `freezeEnd` (`WorkflowEndService:225`) are identified.
- [ ] What `systemClearanceFor` returns when `MarkedTables` is empty (flag off): must stay a no-op.
- [ ] Whether the scenario-launch path already runs `freezeLaunch` inside the faithful copy of #8767.
- [ ] Re-confirm every line number quoted in these notes when opening the files (`InjectService ~565`, `InjectExecutionStep l.737`, `AutonomousRunService`, ...).
- [ ] Generated column (commit 1): minimum supported PostgreSQL version (generated columns need 12+); size of `attackpath_execution` on big installs (table rewrite under an exclusive lock); run, not only read, that update / delete / read statements pass the inspector with the new column.
- [ ] Seed data (`AttackPathCausalSeedService`, through `AttackPathApi`): fake asset ids are hidden once marking is active; decide to create real assets in the seed or to keep it out of the review flow.
- [ ] Confirm on the code the `linkedTo(...)` call used in 5.2 (arguments: table, column, parent table, parent key).

**Risks to decide**
- [ ] Empty Attack Path tab: no known cause, so no size. Time-box it; if the fix is more than a small change, it becomes a sub-issue.

**Process**
- [ ] Signed commits, Conventional Commits with the issue reference, `format-java.ps1` after Java edits, Docker services up for `mvn test`, PR body from the template.
