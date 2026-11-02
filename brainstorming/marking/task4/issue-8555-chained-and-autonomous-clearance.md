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

### Definition of Done (from the issue)

- [ ] 1. Vertical 1 / Vertical 3 meeting held, expected behaviour recorded in the issue
  (the "markings on assets created during a chain" part is answered, see D1)
- [ ] 2. Test: chained simulation **and** autonomous run, a `TLP:RED` and a `TLP:GREEN` target, launched by a `TLP:GREEN` user -> only GREEN executes
- [ ] 3. `TLP:GREEN` viewer sees no finding / asset / chaining outcome from the RED target; admin sees everything
- [ ] 4. Gaps fixed, or split into sub-issues if large
- [ ] 5. A reviewer can run the autonomous/chained flow with a worked example and check results as both users

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | **Chaining does not create assets**, and none is planned in the near future. Newly discovered endpoints appear in the attack path with their IP only; they are discoveries, not asset objects. **No marking policy for created assets is needed** (the old Q1, options A-D, is closed). | ✅ |
| D2 | Marking must still be enforced on every chaining **read surface**: attack paths, execution page, findings page, statistics, allow/deny lists (section 3). | ✅ |
| D3 | Rule for attack paths: if an asset is filtered out for a user by its clearance, **its attack path is hidden too**. | ✅ |

## 3. What must be filtered (chaining read surfaces)

Because chaining stores its own denormalized copies (names, IPs, snapshots, values) in tables that are
**not marked tables**, the statement inspector does not filter them. Each surface below needs an
explicit read-side filter or mask, using the *reader's* clearance.

| # | Surface | What leaks / is wrong today | Direction | Status |
|---|---|---|---|---|
| S1 | **Attack paths** | `attackpath_execution` / `attackpath_finding` store `targetHostname`, `targetIp`, `targetPlatform`, `sourceHostname/Ip/Platform`, `agentName`, `targetRawValue`, finding values. Only `SIMULATION READ` is checked. A GREEN reader sees a RED host's name/IP/findings if a RED user launched. Separate symptom: the tab is **empty** for any marked asset (see 6.1) | Hide a path when its asset is filtered for the reader. Filter/mask in `AttackPathGraphService`, or derive the tables from `assets` in `MarkingDerivedTables` (`targetAssetId` exists) | To fix |
| S2 | **Execution page** (chaining's own) | Not traced yet | Locate the read path and filter by the reader's clearance | To trace |
| S3 | **Findings page** (chaining's own) | Not traced yet. Damien's test showed the *simulation* finding hidden for GREEN; the chaining page is a different read path | Same | To trace |
| S4 | **Statistics** (e.g. dashboard chaining stats) | Not traced yet. Aggregates can leak counts of hidden executions/findings if built from unmarked tables | Compute stats from filtered data only | To trace |
| S5 | **Asset allow / deny lists** (`workflow_scope_rules`) | Not a marked table: saved label and JSON snapshots at launch/end bypass the inspector. `WorkflowConfigurationMapper.toScopeRuleOutput` has no visibility check; `ScopeSnapshotService.computeStatus` reads a hidden asset as "deleted". Deny rules naming a RED asset leak its existence the same way | See Corinne's plan below | To fix |

Discovered endpoints (no asset, no marking, IP only) need no marking of their own; see Q2 for the
edge case where they are only reachable through a hidden asset.

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
execution page (S2).

### Q2 - Discovered endpoints behind a hidden asset

Discovered endpoints are not assets, so they carry no marking. If one is discovered *from* a RED asset
and only reachable through it, D3 implies it must disappear with that path. Confirm with Vertical 1 that
"hide the path of a filtered asset" includes the downstream discoveries.

### Q3 - Masking style for allow / deny lists

Drop the rule, or show a neutral `RESTRICTED` chip (Corinne's proposal)? Dropping hides that a rule
exists; the chip keeps the list shape but reveals a rule exists. Needs a product call.

### Q4 - Attack Path tab empty on any marked asset

Separate bug, or the same root cause as S1? See the trace in 6.1. Candidate for its own sub-issue.

### Q5 - Background paths and the launcher

`WorkflowTimeoutJob`, `QueueChainingJob`, `StepDelayQueueService` resume chains without a user
session. Is the launcher always resolvable there? Do chain steps created at runtime get the right
`launchedBy` (see `InjectUtils:373`)?

## 6. Where #8555 stops

| Item | Where |
|---|---|
| Read-surface filtering S1 (attack paths) and S5 (allow/deny, snapshots, `RESTRICTED`) | New bug issue/PR, sub-issue of #8555. Same root cause and fix family (stored copies bypass the inspector); align with Corinne's PR #8767 |
| S2 / S3 / S4 (execution page, findings page, stats) | Trace first, then fold into the same sub-issue or split per surface if large |
| Attack Path empty on marked assets | Investigate, probably own bug |
| Real-agent RED/GREEN test, chained **and** autonomous | #8555 (DoD 2) |
| Stop/reset by a restricted user | Fold into the S5 PR if confirmed |

## 7. Verification plan (draft)

1. Rerun Corinne's scenario with a RED asset **that has a live agent**: proves the clearance gate, not just "no agent".
2. Same for autonomous mode (Normal vs Autonomous launch).
3. Confirm her unconfirmed cases (amber stop/reset altering the admin's view).
4. Trace the Attack Path tab asset resolution (6.1 below).
5. Trace the read paths of the chaining execution page, findings page and statistics (S2-S4).
6. Trace launcher resolution in `QueueChainingJob` / `WorkflowTimeoutJob` / delayed steps.
7. Build the worked example for DoD 5 (seed script like `task3/demo/*.sh`: RED asset with agent, GREEN asset, GREEN user, admin) and check every surface S1-S5 as both users.

## 8. Notes / scratch

### 8.1 Attack Path trace (static read only, nothing run yet)

**How the tab works**
- Front: `SimulationAttackPath.tsx` -> `useAttackPathLiveGraph` -> `GET .../graph` (+ `/graph/delta`, endpoint expand, relations).
- Read side: `AttackPathGraphService.fullGraph` = `executionRepository.findGraphRows` + `findingRepository.findGraphRows`. Plain JPQL projections of **`attackpath_execution` / `attackpath_finding`**, which are **not** in `MarkingDerivedTables`, so the marking inspector does **not** filter them. An empty graph therefore means **no rows were written** (or the endpoint is refused), not "rows filtered on read".
- Write side: `InjectExecutionStep.recordAttackPathExecution` -> `AttackPathExecutionIngestionService.onRun` -> `tenantTx.executeNew(TxCtx.forTenant(..))`. The call is wrapped in `try/catch` and only does `log.warn("Attack-path ingestion skipped for inject {} (non-fatal)")`. **Failures are silent.**
- `onRun` returns early if `inject.getExercise() == null` or if `getAttackPathExecution` returns an empty list.
- Targets come from `injectService.getAgentsAndAgentlessAssetsByInject(inject)` = `resolveAssetsToExecuteByClearance(inject).cleared()`, **filtered by the launcher's clearance**. Restricted targets are dropped, not recorded.

**Ruled out (by reading)**
- "The background tx has no marking scope so it can't see marked assets": no. `TenantScopedTransaction.run/executeNew` sets `app.current_markings` to every marking of the tenant (`systemClearance`). An unset GUC only hides marked rows (`MarkingCtx.None`), it doesn't fail closed to nothing.
- "The read side hides rows for marked assets": no, see above.

**Still open - candidate causes of the empty tab (RED/RED too)**
1. `getAttackPathExecution` returns `[]` because the cleared set is empty. Check `findLauncherId` for chained/autonomous injects: if `exercise.launchedBy` is null -> `MarkingCtx.none()` -> every marked target dropped. (Dispatch would also drop it, yet Damien saw RED/RED execute, so the launcher probably resolves; confirm on the exact launch path.)
2. An exception inside `onRun` swallowed by the `log.warn`. Plausible spots: `endpointService.getEndpoint(asset.getId(), tenantId)` returning null/throwing for a marked asset, `Hibernate.unproxy(agent.getAsset())` on a hidden asset, `setTargetAssetInformation(null)`.
3. The `inject` handed to `onRun` is **deserialized from `step.getData()`** (`om.readValue(..., Inject.class)` in `InjectExecutionStep`), so its assets may be id-only stubs. `isVisibleUnderClearance` reads `asset.getMarkingIds()`: on a stub that is `null` = "unmarked" = visible. If true, that is a **clearance bypass** for chained steps (the opposite of an empty graph). **Not verified**, but exactly the DoD 2 question (real agent on a RED asset, GREEN launcher).
4. `AttackPathAccessControl.assertCanReadSimulation` refusing the user (403, front shows empty). Unlikely (only SIMULATION READ), cheap to rule out in the network tab.

**Cheapest next experiment (5 min):** run the RED/RED scenario, then
- grep the API log for `Attack-path ingestion skipped` (candidate 2),
- `select count(*) from attackpath_execution where <simulation id>` (rows or no rows),
- compare with the same scenario on an **unmarked** asset.

**Leak (S1), derived from the code, not run:** RED user launches a chain -> RED asset ingested (cleared for the launcher) -> a GREEN user with READ on the simulation sees the RED host's name/IP/platform and its findings in the Attack Path. The write-time launcher check only protects the executor, not the readers.