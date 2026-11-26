# #8555 - Marking clearance in chained simulations and autonomous runs

> Issue: https://github.com/OpenAEV-Platform/openaev/issues/8555
> Parent: #8552 (US2) - Epic #8171 - Task.4 Chunk 7 (size M) - Labels: backend, feature, feature-flag
> Assignee: damgouj - Branch: `issue/8555`
>
> Legend: **Done** = code exists on the branch. **TODO** = not done, not verified, or not decided.

## 1. The issue in one paragraph

Chained simulations and autonomous runs are both *chained scenarios*, and both create simulations. The PoC
stamps the current user as launcher on the autonomous entry points (create, restart, promote to real run) but
it was never verified end to end. Goal: confirm the expected behaviour with Vertical 1, and if it doesn't work,
make chained scenarios behave like time-based ones.

## 2. Where we are

| Block | Content | State |
|---|---|---|
| A | Snapshots of the allow / deny rules frozen at system clearance | **Done** (launch, end, cancel, reset: integration-tested as a GREEN user) |
| B | Read path: rules and group photos of hidden assets cut, step template ids cut | **Done** (unit + integration as a GREEN reader) |
| C | Write path: a restricted writer never removes or rewrites what it cannot see | **Done** (unit + integration as a GREEN editor) |
| D | Autonomous entry points copy the scenario through the faithful copy, launcher = live operator | **Done** (unit tests + one integration test) |
| E | Launcher read from the loaded inject when the database does not know it yet (empty Attack Path tab) | **Done** (one integration test) |
| F | Dispatch parity test (chained) | **Done** |
| G | Attack paths (S1) | **Removed from this PR**, to be redone differently (section 7) |
| H | Flag-off tests, reviewer guide, Vertical 1 confirmation | Flag-off tests **Done**. Reviewer guide and Vertical 1 manual confirmation: **TODO** (manual, no code) |

## 3. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | **Chaining does not create assets**, and none is planned in the near future. Newly discovered endpoints are shown by their IP only; they are discoveries, not asset objects. **No marking policy for created assets is needed** (`grep` in `service/chaining` finds no asset-creation call) | ✅ |
| D2 | Marking must still be enforced on every chaining **read surface**: execution page, findings page, statistics, allow/deny lists (section 4). | ✅ |
| D7 | **The launcher's clearance decides what a chained run executes on, and what it does not (allow and deny list).** The same scenario still executes differently for a GREEN and a RED launcher, through the **allowlist** and **denylist**: The launcher's clearance decides what a chained run executes on. A GREEN user never sees a RED asset in the scope: the allow / deny rules, the group photos and the step templates are returned without it. At execution, the engine expands the scope at system clearance and applies the deny list, then each inject goes through the same clearance gate as a classic simulation. A RED asset on the allow list runs for a RED launcher. For a GREEN launcher, it is dropped at the gate and traced as "Asset not targeted". A RED asset on the deny list never runs, for either launcher. | ✅ |
| D8 | **A rule on a hidden asset is not returned** (no placeholder, no `RESTRICTED` status, no chip, no API type, no i18n key), as a hidden asset is absent from a classic simulation. The write path protects it (block C) | ✅ |
| D9 | **No new feature flag.** The `MARKING` preview feature (`openaev.enabled-dev-features`) already empties `MarkedTables`; the read-path filter works from "visible ids under the reader's scope", so it is inert when the flag is off | ✅ (flag-off tests: `ChainedRunMarkingFlagOffTest`) |
| D10 | **No admin / bypass case in this PR**: handled by another PR (say so in the PR description; the issue DoD says "admin sees everything") | ✅ |
| D11 | **Conditional steps on a hidden finding**: do nothing special. The downstream step shows its normal state, derived from the `Asset not targeted` trace. The test asserts that no RED finding value, name or output leaks | ✅ (no chaining-specific test: findings and agents follow `assets` through `MarkingDerivedTables`, covered on real rows by `MarkingLinkedTablesRowsTest`; **TODO**: a full conditional-step run asserting no RED value, name or output leaks) |

## 4. What must be filtered (chaining read surfaces)

Chaining stores its own denormalized copies (names, IPs, snapshots, values) in tables that are **not marked
tables**, so the statement inspector does not filter them. A surface needs an explicit read-side filter, with
the *reader's* clearance, unless its data already comes from a derived table.

| # | Surface | Finding | Status |
|---|---|---|---|
| S1 | **Attack paths** | Out of this PR (section 7) | Out of PR |
| S2 | **Execution page** | Not chaining-specific: `simulation/Index.tsx` routes `execution/*` to the same `ExecutionOverview` for chained and time-based simulations. Data comes from `injects_assets`, `injects_expectations`, `agents`, `execution_traces`, all already derived from `assets` | Already covered: the derived tables follow `assets` (`MarkingLinkedTablesRowsTest`, real rows). No chaining-specific page test, the route is shared with time-based simulations |
| S3 | **Findings page** | Same shared route, `FindingDistinctSearchService`; `findings` / `findings_assets` already derived from `assets`. Matches the first manual test (finding hidden for GREEN) | Already covered (static read) |
| S4 | **Statistics** (dashboard chaining stats) | Not traced. Aggregates can leak counts built from unmarked tables | **Sub-issue** (dashboards POC), not in #8555 |
| S5 | **Allow / deny lists** (`workflow_scope_rules`) and **step templates** | Stored label, ids and JSON snapshots bypass the inspector; `computeStatus` read a hidden asset as "deleted" | **Done**, see blocks A to C |

### 4.1 First manual tests (issue comment 1)

| Case | Result |
|---|---|
| Creating a chained scenario with a scope filtered by markings | Works |
| Any marked asset (general) | **Attack Path tab is empty**: block E fixes the cause found, to re-check manually |
| Asset RED, user RED | Other tabs and results OK |
| Asset RED, user GREEN | Scope tab: "The asset XXX has been deleted during execution." Scope allow list not filtered by markings (blocks A, B). Finding not visible (good). Attack Path tab: out of this PR |
| Asset GREEN, user GREEN or RED | Other tabs and results OK |

## 5. What the code does today

### Block A - Snapshots at system clearance (`ScopeSnapshotService`)

Why: the launch photo is frozen in the launching user's scope, the end photo in whoever ends the run (timeout
job, queue consumer, the user who stops it). A GREEN launcher's photo held GREEN members only, the system's end
photo held all of them, so a group read `MODIFIED_DURING_EXECUTION` for everyone, and a GREEN reader could see
the names of RED members.

- `freezeLaunch` and `freezeEnd` resolve all their photos in one `tenantTx.executeNew(TxCtx.forTenant(tenantId), ...)`
  (`atSystemClearance`); only plain photos leave the transaction. Without a tenant, the caller's own scope.
  This is **not** `withSystemClearance` of #8767 (as first planned): the tenant transaction is used directly.
- `hiddenAssetIds(tenantId, ids)`: the assets that **exist but the reader cannot see**. The reader's scope answers
  "visible" (`AssetService.visibleAssetIds`, chunks of 1000, `AssetRepository.findIdsByIdIn`, JPQL so tenant and
  marking apply); only the leftovers are looked up again at system clearance. A deleted asset is **not** hidden:
  its rule keeps reading as deleted.
- `withoutHidden(photo, hidden)` returns a copy of a photo without the hidden members; the frozen photo is never
  touched. `computeStatus(rule, hidden)` compares photos with the hidden members taken out, so a visible group no
  longer reads modified because of a member the reader cannot see.
- Tests (`ScopeSnapshotServiceTest`, Mockito): launch photo and end photo at system clearance, no simulation =
  caller's scope, hidden vs visible vs no extra transaction, copy not mutation, group status with a hidden member
  and with a visible member removed.
- **Done, claim dropped**: "cancel / reset / start by a restricted user mutates the snapshots" does not hold.
  `ChainedRunDispatchParityTest.FrozenSnapshots` (real DB, GREEN user, snapshots read back through the repository at
  system clearance): start and cancel freeze the RED asset exactly as a RED user does (resolved, not "deleted"),
  and a reset writes no end photo. Only `freezeLaunch` and `freezeEnd` write photos, both at system clearance.
- **Done**: flag off (`MarkedTables` empty). `ChainedRunMarkingFlagOffTest` (real DB, `MARKING` turned off, RED asset,
  user with no clearance): no asset reported hidden, every rule of `/configuration` returned, and the request scope
  and `withSystemClearance` read the same assets, so system clearance is a no-op. Group photos are cut by the same
  hidden set, so nothing is cut either.

### Block B - Read path: hidden assets dropped

- `WorkflowConfigurationMapper.toOutput`: one batched `hiddenAssetIds` call for every asset a rule or a group photo
  names. An `ASSET` rule on a hidden asset is not returned; `ASSET_GROUP` rules stay, with launch / end photos cut;
  status computed with the hidden set. IP / subnet / domain / manual / CSV rules are not assets: untouched (an
  author-typed IP that matches a hidden endpoint is out of scope).
- Group name (`ruleValueLabel`) is kept: not sensitive. `asset_groups` has no `marking_ids`, only its **members** are
  filtered. Live members are already filtered by the inspector (`asset_groups_assets` derived, dynamic members are a
  query on `assets`).
- Step templates: `StepApi` (create, find, list, update) now maps through `StepService.toReaderOutput(s)`:
  one lookup for every template, `StepMapper.toOutput(step, hidden)` cuts the hidden ids from `inject_assets`
  (ids only, no names, but the inspector does not see JSON). `StepMapper.assetIdsOf` lists the ids of a template.
- Tests: `WorkflowConfigurationMapperTest`, `StepMapperTest`, `StepServiceTest` (Mockito / plain unit tests).
- **Done**: integration tests as a GREEN reader (`ChainedRunDispatchParityTest.ScopeConfiguration`, real DB):
  `/configuration` does not return the RED rule, the group photo has no RED member, the step template data has
  no RED id, a RED reader (control) gets everything. `valid-assets` and `scope/endpoints` (`ScopeService`, JPA
  reads of the marked `assets` table under the reader's `TxCtx`) return only the GREEN asset.
- **Not tested**: `scope/asset-groups` (+ `/find`) returns the group itself, which is not marked (`asset_groups`
  has no `marking_ids`); its members are the live ones, already filtered by the inspector.
- The tests call the mapper and the services in the request transaction of the user, not MockMvc (same choice as
  the rest of the class): the endpoints add only `TxCtx` and `@AccessControl` on top.

### Block C - Write path protection (`WorkflowService`)

Why: the read path drops hidden rules, so a restricted user saving the form would otherwise delete the admin's
RED rules, and realigning templates under the writer's clearance would strip the RED assets.

- `applyScopeRules`: `hiddenAssetRuleIds` finds the rules naming a hidden asset; they are neither removed (absent
  input, or an emptied list) nor rewritten (an input carrying their id).
- `realignTemplateActionTargets` keeps the hidden ids already on the templates (`withAssetsTheWriterCannotSee`,
  `StepService.assetIdsOnStepTemplates`).
- `cleanScopeRulesSimulation` (reset): a rule is a ghost only when its asset does not exist; a rule on an asset
  that exists but is hidden from the caller is kept.
- Tests: `WorkflowServiceTest` (4 cases + reset), `ChainedRunDispatchParityTest` reset section (GREEN keeps the RED
  rule, RED control, deleted asset still drops its ghost rule).
- **Done**: integration test as a GREEN editor (`ScopeConfiguration`): the editor drops the group rule it can see,
  the RED rule survives, the removed group rule is gone, and the realigned step template keeps the RED asset it
  carried. The unchanged-save case (no scope change, so no realignment) is the unit test only.

### Block D - Autonomous entry points (`AutonomousRunService`)

- `resolveLaunchedBy()` already stamped the operator on create, restart and promote to real run (launcher also
  stamped in `ExerciseService`, `ExerciseApi`, `ScenarioApi`, `ScenarioToExerciseService`, `InjectService`).
- New: `copyScenarioToSimulation` copies the scenario through `FaithfulCopyTransaction`, like a scenario launch, so
  the copy keeps the targets the operator is not cleared for (the dispatch does not execute them). Used by create,
  restart and promote. `AutonomousRunService` is added to the allowed callers in `FaithfulCopyCallersArchTest`.
- Tests: `AutonomousRunServiceTest` (Mockito) for the three entry points: stamping + `faithfulCopy.execute`.
  `ChainedRunDispatchParityTest` (autonomous section, real DB): copy then dispatch, GREEN operator keeps the RED
  target and drops it at the gate, RED control executes both.
- Integration through the real entry points: `AutonomousRunScenarioRequestScopeTest` (MockMvc, real DB) now runs
  restart, promote and convert-to-manual end to end, which caught the read-back of the new simulation in the
  ambient tenant (fixed: `exerciseService.exercise(ctx, ...)`). **Still open**: a RED target kept per entry
  point. That test runs as an admin (the admin / bypass case is another PR, D10), so it cannot show a GREEN
  operator's copy keeping a RED target; the copy-then-dispatch parity test covers that behaviour once, with a
  real GREEN user, and the mocks cover the stamping on each entry point.

### Block E - Launcher of an uncommitted inject (`InjectService.launcherIdOf`)

A chained step creates its inject in the run's transaction, then records its attack path from an isolated
`REQUIRES_NEW` one that resolves the targets again. That transaction does not see the uncommitted inject, finds no
launcher, resolves a zero clearance, and writes no attack-path row for a marked asset (empty Attack Path tab).
`launcherIdOf` reads the launcher from the database, and from the loaded inject (simulation's `launchedBy`, else the
inject's own) only when the database does not know it; a closed session returns empty (fail-closed).
Test: `InjectLaunchClearanceOutsideSessionTest.given_injectNotCommittedYet_should_resolveTheLauncherClearance`
(integration).

### Block F - Dispatch parity (execution path, `ChainedRunDispatchParityTest`)

A chained step runs through the **same dispatch as a classic inject**:

| Aspect | Classic simulation | Chained step | Same? |
|---|---|---|---|
| Launcher | `Exercise.launchedBy`, stamped at start | The step's inject gets `setExercise(simulation)` (`InjectExecutionStep`), so `findLauncherId` returns the simulation's launcher | Yes |
| Dispatch | `Executor.execute` -> `resolveAssetsToExecuteByClearance` (cleared / restricted split) | `InjectExecutionStep.run` -> `executor.directExecute` -> same `Executor.execute` | Yes |
| Restricted target | Dropped, traced `ASSET_CLEARANCE_RESTRICTED` "Asset not targeted" | Same code | Yes |
| Target entity | Real asset entity | `applyChainingTarget` loads it with `assetService.asset(id)`: real entity, not a stub | Yes |
| Targets per inject | One inject, all targets inside | `expandTargetBatches`: **one step and one inject per scoped asset**, hidden ones included | Different shape, same outcome |

Conclusion: **do not filter the expansion.** Dropping hidden assets there would erase the "not targeted" trace that
cleared users get in a classic simulation; the dispatch gate stays the single enforcement point.

The test covers: expansion at system clearance (allowed / denied RED asset), the first evaluation at launch in the
launcher's request, dispatch gate with a GREEN launcher and a RED control, autonomous copy, reset.
`resolveLaunchedByClearance` reads the launcher from the database (never from the HTTP context); a missing
launcher gives zero clearance (fail-closed). `filterByMarkingClearance`, quoted in the issue comments, does not
exist on this branch: `resolveAssetsToExecuteByClearance` / `launchableUnderClearance` are the code.

Small points, left as they are:
- `WorkflowService.extractScopeStateSeed` puts hidden asset / group ids in the global state: engine-only (no
  endpoint), the dispatch gate still blocks any run on them.
- The agentless "not targeted" trace is keyed by the asset id (`saveAssetsWithoutClearanceTraces`): existing
  classic behaviour, shared; if it needs masking, fix it once for both.

### Coverage matrix: the chaining tables

None is in `MarkingDerivedTables` (only the tables derived from `assets`: `injects_assets`, `asset_groups_assets`,
`findings_assets`, `findings`, `injects_expectations`, `agents`, `execution_traces`).

| Table | Holds | Read over HTTP | Verdict |
|---|---|---|---|
| `workflows`, `workflow_scope_rules` | Rules: asset ids, names, snapshots | `GET /workflows/{id}/configuration` | Block B |
| `scope_variables` | Named variables (credential values already masked) | Same response | Not asset data |
| `steps` TEMPLATE | `step_data` JSON incl. `inject_assets` | `StepApi` | Block B |
| `steps` READY / RUN, `step_delay_queue` | Per-asset target in `step_data`, delayed steps | No controller | Engine only |
| `workflow_state`, `workflow_state_entries` | Global pool: IPs, finding values, outputs | No controller | Engine only; reaches readers through derived views (findings) |
| `conditions`, `condition_steps` | Key, operator, comparison value | `ConditionApi` -> `ConditionOutput.value` raw | Closed (low). `ConditionKeyType` no longer exists: the asset key types are `PrimitiveType.AssetId` / `AssetGroupId` / `TargetedAsset`. `ConditionMapper` returns `value` as the author typed it, never read from an asset: at most an opaque id the author put there, no name, no snapshot. Not filtered, nothing leaks that the author did not write |

### Audit log and export

- Audit log (`@AuditDiffTracked`): only `Group` and `Role`. Nothing to filter.
- Export (`WorkflowExportInitializer`, via `ScenarioService` and `ExportService`): exports the TEMPLATE workflow;
  `filterAssetScopeRules` drops every ASSET / ASSET_GROUP rule and `normalizeStepDataFieldsForExport` removes
  `inject_assets` / `inject_asset_groups`. `WorkflowScopeRuleExport` (`Mixins.java`) whitelists the value label but
  no snapshot, and the asset rules are dropped before output: no asset name or id leaves. Closed.
- Closed: `inject_content` (`targets_assets`, `targets`) is not stripped on export, and does not need to be. The
  Configure-action drawer stores the targeted assets in `inject_assets` / `inject_asset_groups` (stripped), never
  in `inject_content`. `targets` holds the raw IP / subnet / host of a manual selector (author-typed, not an
  asset). `targets_assets` is only written by a MAPPER condition at runtime, on READY steps; the export reads
  the TEMPLATE workflow.

## 6. Tests on the branch

| Test | Kind | Covers |
|---|---|---|
| `ChainedRunDispatchParityTest` | Integration | Blocks A (expansion, frozen photos), B and C as a GREEN user (`ScopeConfiguration`), D (copy then dispatch), F, reset of block C |
| `AutonomousRunScenarioRequestScopeTest` | Integration (MockMvc) | Block D: restart, promote, convert-to-manual in the request scope |
| `ChainedRunMarkingFlagOffTest` | Integration | D9: flag off, nothing hidden, system clearance a no-op |
| `InjectLaunchClearanceOutsideSessionTest` | Integration | Block E |
| `ScopeSnapshotServiceTest` | Mockito | Block A, hidden set, status |
| `WorkflowConfigurationMapperTest`, `StepMapperTest`, `StepServiceTest`, `StepApiTest` | Unit / Mockito | Block B |
| `WorkflowServiceTest` | Mockito | Block C |
| `AutonomousRunServiceTest` | Mockito | Block D |
| `FaithfulCopyCallersArchTest` | ArchUnit | Block D (allowed caller) |

## 7. TODO

**Out of this PR**
- S1 attack paths: redone differently (section 7)
- S4 statistics: sub-issue, linked to the dashboards POC
- Admin / bypass cases: another PR
- `graphVersion` nudge: attack-path related, left with it
