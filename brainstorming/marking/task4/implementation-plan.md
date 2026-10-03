# Task 4 — Handle Side Effects of Asset Markings: Implementation Plan

**Design doc**: [`tech-design.md`](./tech-design.md) — Option 1 (partial/scoped launch), the four new
`scheduled_by`/`launched_by` fields, dispatch-time clearance resolution, and the marking-bypass guardrail.

**Depends on**: [Task 2 — Assign Markings to Groups](../task2/tech-design.md) (`MarkingScopeResolver`,
`MarkingClearanceCacheManager`); [Task 3 — Marking-based Access Control for Assets](../task3/tech-design.md)
(asset marking = read filter, enforced via the statement-inspector rewrite).

**Status**: POC 1 steps 4.1–4.6, 4.8, 4.9 done and verified (unit tests green, integration tests green
against a real Postgres, no regressions in any known caller of the two methods steps 4.8/4.9 touched).
Step 4.5, on its own, was found during manual e2e validation to filter expectations/findings but not
real dispatch — steps 4.8 (agent-routing dispatch, the actual fix for what manual testing caught) and
4.9 (external-push payload) close that gap; see `tech-design.md`'s "Execution dispatch has three
independent asset-resolution paths, not one" for the full finding. One known, deliberately scoped-out
gap remains from step 4.9: asset groups are not marking-filtered in the external-push path, since asset
groups carry no marking of their own yet (depends on US1, POC 2). All changes remain uncommitted on this
branch. Step 4.7 (Playwright e2e) not started.

This plan is organized as a series of PoCs, each scoped narrowly enough to ship and demo on its own.
POC 1 below covers only the launch/execution enforcement path already decided in `tech-design.md`;
further POCs will be added to this same document to cover the items each POC deliberately defers.

---

## POC 1 — Launch scoping for Scenario/Simulation/Atomic Testing with marking clearance

### 1) Scope of this implementation (PoC)

This PoC delivers **Option 1 only** — partial/scoped execution — for the two paths `tech-design.md`
traces end-to-end: **Scenario → Exercise** (manual launch, scheduled recurrence, dispatch) and
**Atomic Testing** (launch, relaunch, scheduled relaunch, dispatch). Concretely, in scope:

- Four new columns: `Scenario.scheduled_by`, `Exercise.launched_by`, `Inject.scheduled_by`,
  `Inject.launched_by` — written at the points shown in `tech-design.md`'s sequence diagrams.
- One new enforcement point: a `MarkingClearanceCacheManager.findClearance(...)` call inside asset
  resolution (`InjectService.resolveAllAssetsToExecute`), gating what actually gets dispatched.
- The `duplicateInject()` fix so a relaunch does not silently carry the previous launcher's identity
  forward.
- The guardrail that this new check must resolve clearance directly, never through
  `HttpMarkingScopeSupplier` (which would fold in the unrelated agent-runtime bypass).

**It deliberately does not resolve** `tech-design.md`'s "Open items carried forward" — those are open
user-story-level decisions, not implementation gaps in this plan, and are tracked as later PoCs in this
same document rather than folded into this one:

1. Asset Group behaviour (US1) — Option 1/2/3 for a group containing a restricted asset.
2. Whether a Scenario/Simulation/Atomic Testing with mixed targets is itself hidden or filtered
   (Row 2) in lists, detail pages, and target pickers.
3. Whether a Finding inherits its asset's marking (Q4).
4. Surfacing a partial run clearly to a higher-clearance viewer — no UI/API shape decided yet.

None of these block this PoC or would force rework of it: the dispatch-time check operates on
whatever flat asset list target resolution already hands it, regardless of how that list was built or
whether the parent entity is fully visible to the launcher. See `tech-design.md`'s own "Open items
carried forward" section for the full reasoning on why each is independent of what's built here.

Section 4 below restates this same boundary against concrete delivery steps — this section is the
authoritative scope statement; that one just points back to it per step.

---

### 2) Confirmed decisions and constraints

- Launch/relaunch/scheduled execution runs in **partial/scoped mode** (Option 1): only targets visible
  to the resolved actor execute; running on all targets and filtering only the read side (Option 2) is
  rejected as a privilege-escalation vector (`tech-design.md`, "Design options for launch behavior").
- The actor whose clearance gates a run is captured **explicitly**, at the security-relevant action
  itself (launch, relaunch, or recurrence configuration) — never inferred from a "last edited/updated"
  field. `inject_user` keeps its existing "last content editor" meaning and is not reused
  (`tech-design.md`, "Why 'last edited/updated user' cannot be the resolved actor").
- All four new columns are nullable FKs to `users` with `ON DELETE SET NULL`, matching the existing
  `inject_user` FK pattern (`V2_60__Delete_fk_users_injects.java`). A deleted/deactivated actor resolves
  to **zero clearance** — every remaining restricted target is skipped, never executed, never an error.
- `Inject.scheduled_by` / `Inject.launched_by` apply only to root/atomic-testing injects
  (`exercise == null && scenario == null`). A Scenario-linked inject has no copy of its own; its
  clearance is resolved via `inject.getExercise().getLaunchedBy()`.
- Clearance is resolved **live**, at dispatch time, via
  `MarkingClearanceCacheManager.findClearance(actorId, tenantId, bypass)` — never cached or
  snapshotted on the entity itself. `bypass` is computed as `actor.isAdminOrBypass()` **only**; the
  check must never route through `HttpMarkingScopeSupplier`, which would incorrectly fold in the
  unrelated `AGENT_RUNTIME_ACCESS` agent-callback bypass (`tech-design.md`, "Interaction with the
  existing marking bypass (service account) — unchanged").
- `InjectUtils.duplicateInject()` must not blindly copy `launched_by` forward the way it already does
  for `user` — a relaunch has to explicitly overwrite it (current user for a manual relaunch, the
  original inject's `scheduled_by` for a scheduled one).
- The per-tenant service account (`ServiceAccountPrivilegeService`) holds only
  `{AGENT_RUNTIME_ACCESS, AGENT_DOCUMENT_ACCESS, INSTALL_AGENT}` — none of which satisfies
  `@AccessControl(Action.LAUNCH)`. It cannot authenticate a launch/relaunch/recurrence call today, so it
  structurally cannot become `launched_by`/`scheduled_by`. This is a **verified invariant** this PoC
  relies on, not an assumption — if that role's capabilities ever change, this must be re-checked.

### 3) Delivery steps

#### Step 4.1 — Data model: four new columns + migration ✅ done

| Column | Table | Type |
|---|---|---|
| `scenario_scheduled_by` | `scenarios` | FK → `users(user_id)`, nullable, `ON DELETE SET NULL` |
| `exercise_launched_by` | `exercises` | FK → `users(user_id)`, nullable, `ON DELETE SET NULL` |
| `inject_scheduled_by` | `injects` | FK → `users(user_id)`, nullable, `ON DELETE SET NULL` |
| `inject_launched_by` | `injects` | FK → `users(user_id)`, nullable, `ON DELETE SET NULL` |

Flyway migration alongside the corresponding `@ManyToOne @JoinColumn` additions on `Scenario.java`,
`Exercise.java`, `Inject.java`. No backfill: existing rows get `null`, which resolves to zero clearance
until the next launch/relaunch/recurrence-update re-stamps them.

**DoD**: migration applies cleanly on a populated dev DB; entity mapping round-trips in a repository test.

**Done**: `V6_20260930120000000__Add_scheduled_by_and_launched_by_columns.java` — indexed, matches the
existing `inject_user` FK idiom. Verified via the integration suites in step 4.3/4.5 actually loading a
Spring context against it (Postgres via Podman, not Testcontainers — this repo uses a plain compose-managed
test database).

#### Step 4.2 — Write `scheduled_by` on recurrence configuration ✅ done

- `ScenarioApi.updateScenarioRecurrence` (`ScenarioApi.java:577`) — stamp
  `scenario.setScheduledBy(currentUser())` whenever the call actually configures a schedule
  (`schedules == true`, same condition already guarding `throwIfScenarioNotLaunchable`).
- `AtomicTestingService.updateRecurrence()` — same treatment for `Inject.scheduled_by`.

**DoD**: unit test — configuring/updating a recurrence stamps the current user; clearing a recurrence
does not (nothing to gate anymore, but the field is deliberately left as-is rather than nulled, so a
later re-enable doesn't silently lose the last confirmed owner).

**Done**: both call sites stamp as planned; covered by the "launched_by / scheduled_by stamping" nested
tests in `InjectServiceTest` (4 tests, green) for the Atomic Testing side.

#### Step 4.3 — Write `launched_by` on Exercise creation ✅ done

`ScenarioToExerciseService.toExercise()` (`ScenarioToExerciseService.java:54`) gains a new actor
parameter — it has no way to know its own caller today. Both call sites resolve it differently:

- `ScenarioApi.createRunningExerciseFromScenario` (`ScenarioApi.java:668`) → `userService.currentUser()`
  (covers both the normal and the autonomous/chaining branch, lines 672-682 — still the same HTTP call).
- `ScenarioExecutionJob.createScheduledExercise` (`ScenarioExecutionJob.java:117`) →
  `scenario.getScheduledBy()`.

**DoD**: unit test per call site confirming the right actor lands on the created `Exercise`; existing
`ScenarioToExerciseService` tests updated for the new parameter.

**Corrections found while building, not while assuming**: `toExercise()` has a *third* caller family
this plan didn't account for — `AutonomousRunService` calls it from three places (`doCreate`,
`restart`, `promoteToRealRun`), not just `ScenarioApi`. All three are live-user, capability-gated
operator actions, so resolving current-user there is consistent with the design, but it's more surface
than planned: `AutonomousRunService.java` gained a `resolveLaunchedBy()` helper wired into all three
call sites, and `AutonomousRunServiceTest` needed a new `UserRepository` mock plus
`SecurityContextHolder` setup/teardown to exercise it.

**Done**: `ScenarioToExerciseServiceTest`, `ScenarioToExerciseDocumentAttributionTest`,
`AutonomousRunServiceTest` all green (integration suites run against Postgres via Podman — see §Status).

#### Step 4.4 — Write `launched_by` on Atomic Testing launch/relaunch ✅ done

- `AtomicTestingService.launch()` (`AtomicTestingService.java:231-234`) currently writes nothing —
  add `inject.setLaunchedBy(currentUser())`.
- `AtomicTestingService.doRelaunch()` (`AtomicTestingService.java:254-262`) — **after**
  `InjectUtils.duplicateInject()` runs, explicitly overwrite `launched_by` on the new inject: current
  user for a manual relaunch (`checkLaunchable = true`), or the original inject's `scheduled_by` for a
  scheduled one (`checkLaunchable = false`, `AtomicTestingExecutionJob.java:101-112`). This is the
  `duplicateInject()` trap fix from §2 — do not let it fall through to a copy-forward.

**DoD**: regression test proving a manual relaunch by user B does **not** inherit user A's
`launched_by` from the original inject; scheduled-relaunch test proving it inherits `scheduled_by`
instead.

**Corrections found while building, not while assuming**:

- `AtomicTestingService.launch()`/`doRelaunch()` don't touch the `Inject` entity directly — they
  delegate to `InjectService.launch()`/`InjectService.doRelaunch()`, which is where the actual entity
  mutation and the `duplicateInject()` call live. The stamping landed there instead, per the plan's
  intent, not literally inside `AtomicTestingService`.
- Not in the original plan text but necessary for correctness: `duplicateInject()` also needed to copy
  `scheduledBy` forward (recurrence-adjacent, like `recurrence`/`recurrenceStart`/`recurrenceEnd`) so a
  recurring atomic testing doesn't lose its schedule owner after its first scheduled relaunch — only
  `launchedBy` is deliberately withheld from the copy.

**Done**: 14 new tests in `InjectServiceTest` cover the launch/relaunch stamping and the
`duplicateInject()` trap specifically (manual relaunch does not inherit the prior launcher).

#### Step 4.5 — Dispatch-time enforcement, expectations/findings only ⚠️ done, but not the fix this PoC needs

Inside `InjectService.resolveAllAssetsToExecute`, called from `InjectsExecutionJob.executeInject()`
(`InjectsExecutionJob.java:149`):

1. Resolve the actor: `inject.getExercise() != null ? inject.getExercise().getLaunchedBy() :
   inject.getLaunchedBy()`.
2. Null actor (deleted/never stamped) → treat as zero clearance, skip every marked target.
3. Otherwise: `bypass = actor.isAdminOrBypass()`; call
   `markingClearanceCacheManager.findClearance(actor.getId(), tenantId, bypass)`.
4. Drop every resolved asset whose marking is not covered by the returned `MarkingCtx`; unmarked
   assets are never filtered (per Task 3).

**DoD**: unit tests covering full clearance (all targets run), partial clearance (`ASSET_GREEN` runs,
`ASSET_RED` silently skipped — the canonical worked example from `user-stories.md`), zero clearance (no
targets run), bypass actor (all targets run regardless of grants), and null actor (no targets run).

**Done**: all five scenarios covered in `InjectServiceTest`'s "dispatch-time marking clearance
enforcement" nested class; also verified against real Postgres via `ScenarioExecutionJobTest` /
`InjectsExecutionJobTest` / `InjectsExecutionJobUnitTest` / `AtomicTestingExecutionJobTest` (26 tests,
green) — these exercise the full creation → dispatch path end to end, not just the mocked unit slice.

**Found during manual e2e validation, not caught by any of the above**: this method
(`resolveAllAssetsToExecute`) only feeds expectation/scoring/finding computation. It is not what
decides which agents actually get commanded to execute, nor what's serialized into the external-push
dispatch payload — those are two further, independent asset-resolution points (`InjectService.java:1111-1126`
and `ExecutableInjectDTOMapper.java:23-39`) that this step never touched. A `TLP:RED` agent targeted
alongside an unmarked asset, launched by a `TLP:GREEN` user, genuinely executed the payload — confirmed
via `execution_traces`. See steps 4.8 and 4.9, and `tech-design.md`'s "Execution dispatch has three
independent asset-resolution paths, not one" for the full finding. None of the 26+54 tests above caught
this because none of them asserted anything about the real agent dispatch path or the external-push DTO
— they only ever exercised `resolveAllAssetsToExecute()`'s own return value.

#### Step 4.6 — Guardrail test against the bypass leak ✅ done

A targeted test proving `AGENT_RUNTIME_ACCESS` alone does **not** grant bypass in this new path — i.e.
that step 4.5 calls `MarkingClearanceCacheManager` directly and never through
`HttpMarkingScopeSupplier`. This is the regression this PoC exists to prevent, per `tech-design.md`'s
bypass section, so it gets its own explicit test rather than relying on code review alone.

**Done**: `given_agentRuntimeAccessOnlyActor_should_notBypass()` in `InjectServiceTest` — no production
code change was needed, the guardrail was already correctly implemented (`bypass =
launchedBy.isAdminOrBypass()` only); this test exists purely to pin that property against regression.

#### Step 4.8 — Filter the agent-routing dispatch path (the actual fix) ✅ done

`InjectService.getAgentsAndAgentlessAssetsByInject(inject)` (`InjectService.java:1111-1126`), called
from `ExecutionExecutorService.launchExecutorContext(inject)` unconditionally before the
internal/external split — this is the method that builds the real `Set<Agent>` commanded to execute,
and it reads `inject.getAssets()` / expanded `inject.getAssetGroups()` directly, with no marking
awareness. Fix:

1. Extract the clearance-resolution half of step 4.5's `filterByMarkingClearance` into its own
   reusable method, e.g. `resolveLaunchedByClearance(Inject inject): MarkingCtx` (same actor
   resolution, same live `MarkingClearanceCacheManager.findClearance` call, same null-actor →
   `MarkingCtx.none()` fallback) — shared by both this step and step 4.5, rather than duplicated.
2. In `getAgentsAndAgentlessAssetsByInject`, resolve clearance once, then skip
   `extractAgentsAndAssetsAgentless(...)` for any asset not visible under that clearance — both in the
   direct-assets loop and the asset-group-expansion loop.

**DoD**: the canonical worked example as an actual execution test, not just an asset-list assertion —
launch (or directly call `launchExecutorContext`) with a `TLP:GREEN` actor against one unmarked and one
`TLP:RED` endpoint; assert the returned `Set<Agent>` excludes the `TLP:RED` endpoint's agent entirely.
Regression test: an admin/bypass actor still gets both. Ideally re-run the exact manual e2e scenario
that found this (two assets, one `TLP:RED` agent, `TLP:GREEN` launcher) and confirm no execution trace
is produced for the restricted agent at all — not just that it's hidden from a result view.

**Done**: `resolveLaunchedByClearance(Inject): MarkingCtx` extracted from step 4.5's
`filterByMarkingClearance` and shared by both. New `InjectServiceTest` nested class
(`AgentRoutingDispatchFilterTests`, 3 tests): partial clearance excludes the restricted agent from the
returned `Set<Agent>` entirely (not just from a display list), bypass actor gets both, unmarked asset
included under zero clearance. Regression across every known caller of
`getAgentsAndAgentlessAssetsByInject` — `ExecutionExecutorServiceTest`, `InjectExecutionStepTest`,
`AttackPathExecutionIngestionServiceTest` (82 tests) — green.

#### Step 4.9 — Filter the external-push dispatch payload (non-agent connectors) ✅ done

`ExecutableInjectDTOMapper.toExecutableInjectDTO()` (`ExecutableInjectDTOMapper.java:23-39`) builds
`.assets(...)`/`.assetGroups(...)` from `executableInject.getAssets()`/`getAssetGroups()` — the
original fields set once in `InjectHelper.toExecutableInject()`, independent of anything
`resolveAllAssetsToExecute()` computed. This is the path for any injector classified `isExternal()`
that isn't agent-based (email, SMS, OpenCTI, etc.). Fix: consume the filtered
`executableInject.getAssetsToExecute()` instead, falling back to calling
`injectService.resolveAllAssetsToExecute(inject)` when null — the same fallback pattern
`AbstractTechnicalBehavior` already uses for direct callers that don't pre-cache
(`AbstractTechnicalBehavior.java:92-97`).

**To resolve during implementation, not assume**: whether `.assetGroups(...)` in the DTO is used
downstream only for target expansion (in which case it should become empty once `.assets(...)` carries
the already-expanded, filtered flat list — passing both would double-submit and could leak unfiltered
group members through that second channel) or serves another purpose the connector needs (e.g.
labeling/reporting) that would be lost by emptying it. Check every downstream consumer of
`ExecutableInjectDTO.assetGroups` before deciding.

**DoD**: unit test on the mapper proving a restricted asset present in `executableInject.getAssets()`
is absent from the built DTO when a filtered `assetsToExecute` excludes it; existing mapper tests
(non-marking cases) unaffected.

**Done**: `ExecutableInjectDTOMapper` now takes an `InjectService` dependency and builds `.assets(...)`
from `executableInject.getAssetsToExecute()`, falling back to `injectService.resolveAllAssetsToExecute(inject)`
when null. New `ExecutableInjectDTOMapperTest` (3 tests): cached-filtered-list path, fallback-to-resolve
path, non-marking case unaffected.

**`.assetGroups(...)` decision — left unfiltered, deliberately, not resolved the way the DoD assumed a
clean answer existed:** the mapper's own existing comment confirms non-endpoint assets reached via a
group (e.g. AI targets) are resolved by the downstream injector *from the group itself*, independent of
the filtered flat asset list. Asset groups carry no marking of their own today — that's Task 4's own
explicitly-deferred US1 — so there is no clearance rule to apply to this set yet; emptying it would
silently break the AI-target-via-group path for external injectors, not just de-duplicate. **Known,
scoped gap, not an oversight**: a marked AI-target asset reachable only through an asset group,
dispatched to a non-agent external connector, is not covered by this fix. Revisit once US1 (POC 2)
gives asset groups their own marking semantics.

#### Step 4.7 — End-to-end proof, as a Playwright e2e test (CI-covered) 🔴 not started

Replaces the earlier `curl`-based demo-script idea: Task 2/3's UI is now actually implemented
(`GroupManageMarkings.tsx`, `MarkingDefinitions.tsx`, `ItemMarkings.tsx`), Playwright is already wired
into `core-ci.yml`/`nightly-ci.yml` (`yarn test:e2e`), and no e2e test currently touches markings at
all — so a real through-the-UI test both proves this PoC and becomes permanent regression coverage,
for free, instead of a one-off manual demo.

New file: `openaev-front/tests_e2e/tests/marking/marking-scoped-launch.spec.ts` (new `marking/` folder —
none exists yet, alongside the existing `scenario/`, `threat-arsenals/` groupings).

Reproduces the `FULL_ADMIN` / `USER_GREEN` / `ASSET_RED` / `ASSET_GREEN` worked example from
`user-stories.md`, fixtures created via API (matching `scenario-teams.spec.ts`'s pattern of API setup +
UI-driven assertions, not clicking through every setup step):

1. *(API setup)* `FULL_ADMIN` creates `ASSET_RED` (`TLP:RED`) and `ASSET_GREEN` (unmarked), a group
   cleared for `TLP:GREEN` with `USER_GREEN` as a member, and a Scenario with an inject targeting both.
2. *(UI, as `USER_GREEN`)* open the Scenario — assert `ASSET_RED` never appears in the target list
   (Task 3's existing read filter, incidentally exercised here too).
3. *(UI, as `USER_GREEN`)* click Launch.
4. *(UI, as `USER_GREEN`)* open the resulting Simulation — assert only `ASSET_GREEN` shows as a
   target/result; no count, placeholder, or error hints that a second target exists. **Also assert no
   execution trace exists for the `TLP:RED` agent** (via API, not just UI) — a UI-only assertion here
   would have passed even with the step 4.5-only gap manual testing found, since the overview already
   hid the restricted asset while it was still actually executing underneath.
5. *(UI, as `FULL_ADMIN`)* open the same Simulation — assert both `ASSET_GREEN` and `ASSET_RED` are
   still configured as targets, proving the underlying data wasn't altered, only filtered per-viewer
   (the "configurations and results are not altered or corrupted" acceptance principle in
   `user-stories.md`).

**Genuinely new test infrastructure this requires** (not a rename of something that already exists):

- A second authenticated session for `USER_GREEN`. Every project today shares one `storageState`
  (`tests_e2e/.auth/user.json`) written once by the `setup` project (`auth.setup.ts`) — there's no
  existing multi-user fixture in this suite, so a second `browser.newContext()` + login (or a second
  storageState file) has to be added.
- New API helpers under `tests_e2e/api-helpers/` for Asset, Group/User, and Marking setup — only
  `ScenarioApiHelpers`, `TeamApiHelpers`, `DocumentApiHelpers`, `TenantApiHelpers` exist today.

**DoD**: spec green in CI under the existing `test:e2e` job, no new pipeline required.

### 4) Deliberately deferred (not this task)

Restated from §1 against concrete steps — none of these have a step above, by design:

- Asset Group behaviour (US1) — Option 1/2/3 for a group containing a restricted asset.
- Whether a Scenario/Simulation/Atomic Testing with mixed targets is itself hidden or filtered
  (Row 2) in lists, detail pages, and target pickers.
- Whether a Finding inherits its asset's marking (Q4).
- Surfacing a partial run clearly to a higher-clearance viewer (no UI/API shape decided).

### 5) Validation matrix

**Steps 4.1–4.4, 4.6 ✅** — green and verified:

- New/updated unit tests: `InjectServiceTest` (54 tests total, including 14 new — stamping, the
  `duplicateInject` trap, and the step 4.6 guardrail).
- Existing suites updated for the new `toExercise()` parameter and fields, green:
  `ScenarioToExerciseServiceTest`, `ScenarioToExerciseDocumentAttributionTest`, `AutonomousRunServiceTest`
  (the step 4.3 correction — new `UserRepository` mock + `SecurityContextHolder` setup).
- Full creation path verified against a real Postgres (Podman-managed compose stack, not
  Testcontainers — this repo doesn't use it): `ScenarioExecutionJobTest`, `InjectsExecutionJobTest`,
  `InjectsExecutionJobUnitTest`, `AtomicTestingExecutionJobTest` (26 tests, green), plus a regression
  check of `AtomicTestingServiceTest` (unaffected, green).
- Tenant isolation suite unaffected — this PoC adds no new statement-inspector dimension.

**Step 4.5 ⚠️ green, but proven insufficient on its own** — all 54+26 tests above stayed green through
manual e2e validation that found a real agent still executes a restricted target. None of them asserted
anything about the real agent-dispatch path or the external-push DTO, only about
`resolveAllAssetsToExecute()`'s own return value — a gap in what was tested, not a flaky result. Steps
4.8/4.9 close it.

**Steps 4.8, 4.9 ✅** — green and verified:

- `InjectServiceTest`'s new `AgentRoutingDispatchFilterTests` (3 tests) and `ExecutableInjectDTOMapperTest`
  (3 tests, new file) — green, covering each step's DoD.
- Regression across every known caller of `getAgentsAndAgentlessAssetsByInject`:
  `ExecutionExecutorServiceTest`, `InjectExecutionStepTest`, `AttackPathExecutionIngestionServiceTest`
  (82 tests) — green.
- Full integration regression against real Postgres (Podman): `ScenarioToExerciseServiceTest`,
  `ScenarioToExerciseDocumentAttributionTest`, `ScenarioExecutionJobTest`, `InjectsExecutionJobTest`,
  `InjectsExecutionJobUnitTest`, `AtomicTestingExecutionJobTest`, `AtomicTestingServiceTest`,
  `AutonomousRunServiceTest` (92 tests) — green.
- One failure surfaced on a full-module run, `AccessControlAuditLogAspectTest`
  (`ObjectOptimisticLockingFailureException` during Spring context startup) — isolated and re-run alone
  (13/13 green); confirmed a pre-existing environmental flake from the long-lived, reused test Postgres
  container accumulating state across many runs this session, unrelated to inject/asset/marking logic.
- Known, deliberately scoped-out gap from step 4.9: asset groups are not marking-filtered in the
  external-push path (see step 4.9's "Done" note) — tracked against US1, not a test gap.

**Step 4.7 🔴 not started.**

- No *new* frontend UI is built in this PoC — the launch/relaunch actions and the marking-assignment
  screens all already exist (Task 1/2/3). Step 4.7's e2e test will *exercise* that existing UI as proof
  and as permanent CI regression coverage; it does not add or change any product UI.
- Not yet committed: all changes remain as uncommitted working-tree modifications, deliberately,
  pending review.

### 6) Traceability to user stories

- **US2** (Scenarios, Simulations, Atomic testing: restricted assets hidden in targets, execution
  details, results, scores, findings, remediations) — **execution/dispatch enforcement done** (steps
  4.1–4.6, 4.8, 4.9) for directly-targeted and asset-group-targeted endpoints alike, across agent-routing
  and external-push dispatch. One scoped exception: a marked AI-target asset reachable only through an
  asset group, dispatched via a non-agent external connector, is not yet covered — depends on US1 giving
  asset groups their own marking semantics. Target/result/score/finding display-filtering already runs
  through Task 3's existing per-asset read filter. Whether the *entity itself* is hidden or filtered when
  it has mixed targets (Row 2) is explicitly **not** decided or built here — see §4.
- **US1** (Asset Groups) — **not addressed**; deferred, §4.
- **US0** (Dashboards) — **not addressed**; per `user-stories.md`'s own open question #8, dashboards
  reuse US2's result filtering once Row 2 is settled, so this PoC is a prerequisite input, not a
  completion, for US0.

---

## POC 2 — planned, not yet drafted

Will cover the items POC 1 deliberately defers (§4 above): Asset Group behaviour (US1), entity-level
hide/filter for mixed-target entities (Row 2), Finding marking inheritance (Q4), and partial-run
reporting. Scoped once POC 1 has shipped and those user-story-level decisions are confirmed.
