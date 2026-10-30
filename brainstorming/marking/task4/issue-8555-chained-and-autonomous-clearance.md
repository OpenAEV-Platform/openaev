# #8555 - Marking clearance in chained simulations and autonomous runs

> Brainstorm notes. Issue: https://github.com/OpenAEV-Platform/openaev/issues/8555
> Parent: #8552 (US2) - Epic #8171 - Task.4 Chunk 7 (size M) - Labels: backend, feature, feature-flag
> Assignee: damgouj - Branch: `issue/8555`
> Status: **brainstorming** - nothing here is decided unless marked ✅.

## 1. The issue in one paragraph

Chained simulations and autonomous runs are both *chained scenarios*, and both create simulations.
The PoC stamps the current user as launcher on the autonomous entry points (create, restart,
promote to real run) but it was never verified end to end: chained steps, findings, assets.
Goal: confirm the expected behaviour with Vertical 1, and if it doesn't work, make chained
scenarios behave like time-based ones.

### Definition of Done (from the issue)

- [ ] 1. Vertical 1 / Vertical 3 meeting held, expected behaviour recorded in the issue (incl. markings on assets created during a chain)
- [ ] 2. Test: chained simulation **and** autonomous run, a `TLP:RED` and a `TLP:GREEN` target, launched by a `TLP:GREEN` user -> only GREEN executes
- [ ] 3. `TLP:GREEN` viewer sees no finding / asset / chaining outcome from the RED target; admin sees everything
- [ ] 4. Gaps fixed, or split into sub-issues if large
- [ ] 5. A reviewer can run the autonomous/chained flow with a worked example and check results as both users

## 2. What we already know

### 2.1 Damien's first tests (issue comment 1)

| Case | Result |
|---|---|
| Creating a chained scenario with a scope filtered by markings | Works |
| Any marked asset (general) | **Attack Path tab is empty** |
| Asset RED, user RED | Other tabs and results OK |
| Asset RED, user GREEN | Attack Path + Scope tabs: "The asset XXX has been deleted during execution." Scope allow list **not filtered** by markings. Finding not visible (good) |
| Asset GREEN, user GREEN or RED | Other tabs and results OK |

### 2.2 Corinne's findings (issue comment 2)

Drafted bug: *scope rules expose the name and snapshot of assets the reader is not cleared for.*

- `workflow_scope_rules` is **not a marked table** -> denormalized copies (saved label, JSON snapshots at launch/end) bypass the statement inspector.
- Two exposure points: `WorkflowConfigurationMapper.toScopeRuleOutput` (no visibility check) and `ScopeSnapshotService.computeStatus` (launch photo vs. filtered live read -> hidden asset reads as "deleted").
- Copies are written under whichever clearance is active (user vs system depending on the path) -> a restricted user's cancel/reset can mutate what the admin sees (code-based, **unconfirmed**).
- Asset groups: all members frozen in snapshots; visible group can wrongly read `MODIFIED_DURING_EXECUTION`.
- Proposed fix: Part A (write at system clearance via `TenantScopedTransaction.withSystemClearance`) + Part B (mask on read, new `RESTRICTED` status, neutral chip on the front).
- Execution itself unaffected in her test **but** the RED asset had no agent, so it doesn't prove `filterByMarkingClearance` stopped it.

### 2.3 Code facts (checked on branch `issue/8555`)

- Launcher is stamped in: `AutonomousRunService` (`resolveLaunchedBy()` at lines ~424, 1539, 1627), `ExerciseService:813`, `ExerciseApi:632`, `ScenarioApi:682`, `ScenarioToExerciseService:71`, `InjectService:673/701`.
- Dispatch clearance: `InjectService.resolveLaunchedByClearance` (~l.565) reads the launcher via `findLauncherId(inject.getId())` and the live clearance from `MarkingClearanceCacheManager`, **never** from the HTTP context. Partial execution via `resolveAssetsToExecuteByClearance` (cleared / not cleared split).
- `InjectUtils:373`: `launchedBy` deliberately **not** copied on duplication/relaunch.
- Migration `V6_20261016090000000` added `exercise_launched_by` / `inject_launched_by` (+ scheduled_by).
- `grep` in `service/chaining` for asset-creation calls: **nothing found** -> chain steps don't seem to create assets directly (to confirm, see Q1).
- `filterByMarkingClearance` (named in Corinne's report) is **not** found by grep on this branch -> it was probably renamed/refactored; use `resolveAssetsToExecuteByClearance` / `launchableUnderClearance`. Worth checking she tested against the same code.

## 3. Open questions (brainstorm here)

### Q1 - Assets created by a scan inside a chain: marked or not?

Chain steps don't create assets themselves; creation would come from the injector/collector side
(normal asset API). So *who* applies a marking at creation?

| Option | Behaviour | Pros | Cons |
|---|---|---|---|
| A | New asset inherits the **launcher's** markings | Creator never loses sight of their own discovery | Over-marks if launcher is high-clearance; doesn't reflect the target's sensitivity |
| B | Created **unmarked** | Simple | A scan of a RED target can surface assets visible to everyone |
| C | Inherits the markings of the **source/target asset** that produced it | Most conservative; follows data lineage | Needs lineage (which asset/inject produced it); multi-source = AND of markings? |
| D | A + C (union) | Safest | Most restrictive, hardest to explain |

Leaning: **C (or D)**, but depends on Vertical 1's model of discovered assets. Whatever is picked,
the marking must be applied **at creation time**, else a later chain step could run on an asset nobody can see.

*Your take?*

### Q2 - Chain conditions on hidden findings

A conditional trigger on a RED finding: does it fire for a GREEN launcher? If the RED inject never
executed there is no finding, so probably no trigger. But what does the GREEN viewer see for the
downstream step: "skipped", "pending", nothing? (DoD 3: "no chaining outcome from the RED target".)

### Q3 - Allow / deny lists

A deny rule naming a RED asset leaks its existence to GREEN, same class as the allow-list leak.
Do we mask, drop, or show a neutral `RESTRICTED` chip (Corinne's proposal)?

### Q4 - Attack Path tab empty on any marked asset

Separate bug, or the same root cause (path resolves assets through a filtered read)? Needs a trace
of how the tab builds its nodes. Candidate for its own sub-issue.

### Q5 - Where does #8555 stop and the leak bug start?

Proposal:

| Item | Where |
|---|---|
| Scope-rule leak (snapshots, labels, `RESTRICTED`) | New bug issue/PR, sub-issue of #8555 |
| Attack Path empty on marked assets | Investigate, probably own bug |
| Created-asset marking policy | Decision recorded in #8555 after the Vertical 1 meeting |
| Real-agent RED/GREEN test, chained **and** autonomous | #8555 (DoD 2) |
| Stop/reset by a restricted user | Fold into the leak PR if confirmed |

### Q6 - Background paths and the launcher

`WorkflowTimeoutJob`, `QueueChainingJob`, `StepDelayQueueService` resume chains without a user
session. Is the launcher always resolvable there? Do chain steps created at runtime get the right
`launchedBy` (see `InjectUtils:373`: not copied on duplication)?

## 4. Verification plan (draft)

1. Rerun Corinne's scenario with a RED asset **that has a live agent** -> proves the clearance gate, not just "no agent".
2. Same for autonomous mode (Normal vs Autonomous launch).
3. Confirm her unconfirmed cases 5 and 6 (amber stop/reset altering the admin's view).
4. Trace the Attack Path tab asset resolution.
5. Trace launcher resolution in `QueueChainingJob` / `WorkflowTimeoutJob` / delayed steps.
6. Build the worked example for DoD 5 (seed script like `task3/demo/*.sh`: RED asset with agent, GREEN asset, GREEN user, admin).

## 5. Decisions log

| # | Decision | Status |
|---|---|---|
| - | _nothing decided yet_ | |

## 6. Notes / scratch

### 6.1 Attack Path trace (static read only, nothing run yet)

**How the tab works**
- Front: `SimulationAttackPath.tsx` -> `useAttackPathLiveGraph` -> `GET .../graph` (+ `/graph/delta`, endpoint expand, relations).
- Read side: `AttackPathGraphService.fullGraph` = `executionRepository.findGraphRows` + `findingRepository.findGraphRows`. Plain JPQL projections of **`attackpath_execution` / `attackpath_finding`**, which are **not** in `MarkingDerivedTables` -> the marking inspector does **not** filter them. So an empty graph = **no rows were written** (or the endpoint is refused), not "rows filtered on read".
- Write side: `InjectExecutionStep.recordAttackPathExecution` -> `AttackPathExecutionIngestionService.onRun` -> `tenantTx.executeNew(TxCtx.forTenant(..))`. The whole call is wrapped in `try/catch` and only does `log.warn("Attack-path ingestion skipped for inject {} (non-fatal)")`. **Failures are silent.**
- `onRun` returns early if `inject.getExercise() == null` or if `getAttackPathExecution` returns an empty list ("never bump on an empty write").
- Targets come from `injectService.getAgentsAndAgentlessAssetsByInject(inject)` = `resolveAssetsToExecuteByClearance(inject).cleared()` -> **filtered by the launcher's clearance** (`findLauncherId` -> `MarkingClearanceCacheManager`). Restricted targets are dropped, not recorded.

**Ruled out (by reading)**
- "The background tx has no marking scope so it can't see marked assets": **no**. `TenantScopedTransaction.run/executeNew` sets `app.current_markings` to *every marking of the tenant* (`systemClearance`), so reads inside `onRun` see everything. And an unset GUC only hides marked rows (`MarkingCtx.None`), it doesn't fail closed to nothing.
- "The read side hides rows for marked assets": **no**, see above.

**Still open - candidate causes of the empty tab (RED/RED too)**
1. `getAttackPathExecution` returns `[]` because the cleared set is empty. Check `findLauncherId` for chained/autonomous injects: if `exercise.launchedBy` is null -> `MarkingCtx.none()` -> every marked target dropped -> zero rows (but then dispatch would also drop it; Damien saw RED/RED execute, so launcher probably resolves; worth confirming on the exact launch path used).
2. An exception inside `onRun` swallowed by the `log.warn`. Plausible spots: `endpointService.getEndpoint(asset.getId(), tenantId)` returning null/throwing for a marked asset, `Hibernate.unproxy(agent.getAsset())` on a hidden asset, `setTargetAssetInformation(null)`.
3. The `inject` handed to `onRun` is **deserialized from `step.getData()`** (`om.readValue(..., Inject.class)` in `InjectExecutionStep`), so its assets may be id-only stubs. `isVisibleUnderClearance` reads `asset.getMarkingIds()` on the object it is given: on a stub that is `null` = "unmarked" = visible. If true, that is a **clearance bypass** for chained steps, the opposite of an empty graph. **Not verified**, but it is exactly the DoD 2 question (real agent on a RED asset, GREEN launcher).
4. `AttackPathAccessControl.assertCanReadSimulation` refusing the user (403, front shows empty). Unlikely since it is only SIMULATION READ, but cheap to rule out in the network tab.

**Cheapest next experiment (5 min):** run the RED/RED scenario, then
- grep the API log for `Attack-path ingestion skipped` (candidate 2),
- `select count(*) from attackpath_execution where <simulation id>` (rows or no rows),
- compare with the same scenario on an **unmarked** asset.

**Second problem found while tracing (different from the empty tab): attack-path rows leak restricted asset data to any reader of the simulation**
- `attackpath_execution` stores denormalized `targetHostname`, `targetIp`, `targetPlatform`, `sourceHostname/Ip/Platform`, `agentName`, `targetRawValue` (all in the `findGraphRows` projection) and `attackpath_finding` stores values.
- These tables are not marking-derived, and `AttackPathAccessControl` only checks `SIMULATION READ`.
- So: **RED user launches a chain -> RED asset ingested (it is cleared for the launcher) -> a GREEN user with READ on the simulation sees the RED host's name/IP/platform and its findings in the Attack Path.** Same class as Corinne's scope-rule leak (stored copies bypass the inspector). Not run, derived from the code.
- It also means the *write-time* launcher check does not protect *readers*: it only protects the executor.
- Possible fixes (to discuss): (a) mark/derive these tables from `assets` in `MarkingDerivedTables` (needs an asset FK column, `targetAssetId` already exists; discovered endpoints with a raw value have none); (b) filter/mask on read in `AttackPathGraphService` using the reader's clearance; (c) apply the same system-write / reader-mask principle as Corinne's PR #8767 approach.

**Impact on the split (Q5):** the Attack Path leak belongs with the scope-rule leak (same root cause, same fix family), not in #8555 proper.