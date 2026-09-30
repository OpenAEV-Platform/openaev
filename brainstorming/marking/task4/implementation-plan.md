# Task 4 — Handle Side Effects of Asset Markings: Implementation Plan

**Design doc**: [`tech-design.md`](./tech-design.md) — Option 1 (partial/scoped launch), the four new
`scheduled_by`/`launched_by` fields, dispatch-time clearance resolution, and the marking-bypass guardrail.

**Depends on**: [Task 2 — Assign Markings to Groups](../task2/tech-design.md) (`MarkingScopeResolver`,
`MarkingClearanceCacheManager`); [Task 3 — Marking-based Access Control for Assets](../task3/tech-design.md)
(asset marking = read filter, enforced via the statement-inspector rewrite).

**Status**: not started.

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

#### Step 4.1 — Data model: four new columns + migration 🔴 next

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

#### Step 4.2 — Write `scheduled_by` on recurrence configuration 🔴 not started

- `ScenarioApi.updateScenarioRecurrence` (`ScenarioApi.java:577`) — stamp
  `scenario.setScheduledBy(currentUser())` whenever the call actually configures a schedule
  (`schedules == true`, same condition already guarding `throwIfScenarioNotLaunchable`).
- `AtomicTestingService.updateRecurrence()` — same treatment for `Inject.scheduled_by`.

**DoD**: unit test — configuring/updating a recurrence stamps the current user; clearing a recurrence
does not (nothing to gate anymore, but the field is deliberately left as-is rather than nulled, so a
later re-enable doesn't silently lose the last confirmed owner).

#### Step 4.3 — Write `launched_by` on Exercise creation 🔴 not started

`ScenarioToExerciseService.toExercise()` (`ScenarioToExerciseService.java:54`) gains a new actor
parameter — it has no way to know its own caller today. Both call sites resolve it differently:

- `ScenarioApi.createRunningExerciseFromScenario` (`ScenarioApi.java:668`) → `userService.currentUser()`
  (covers both the normal and the autonomous/chaining branch, lines 672-682 — still the same HTTP call).
- `ScenarioExecutionJob.createScheduledExercise` (`ScenarioExecutionJob.java:117`) →
  `scenario.getScheduledBy()`.

**DoD**: unit test per call site confirming the right actor lands on the created `Exercise`; existing
`ScenarioToExerciseService` tests updated for the new parameter.

#### Step 4.4 — Write `launched_by` on Atomic Testing launch/relaunch 🔴 not started

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

#### Step 4.5 — Dispatch-time enforcement (the core check) 🔴 not started

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

#### Step 4.6 — Guardrail test against the bypass leak 🔴 not started

A targeted test proving `AGENT_RUNTIME_ACCESS` alone does **not** grant bypass in this new path — i.e.
that step 4.5 calls `MarkingClearanceCacheManager` directly and never through
`HttpMarkingScopeSupplier`. This is the regression this PoC exists to prevent, per `tech-design.md`'s
bypass section, so it gets its own explicit test rather than relying on code review alone.

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
   target/result; no count, placeholder, or error hints that a second target exists.
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

- New unit tests from steps 4.2–4.6, green.
- `ScenarioToExerciseServiceTest`, `AtomicTestingServiceTest`, `InjectsExecutionJobTest` — existing
  suites updated for the new parameter/fields, green.
- Tenant isolation suite unaffected — this PoC adds no new statement-inspector dimension; the
  dispatch-time filter is a plain Java check inside asset resolution, not a SQL rewrite.
- No *new* frontend UI is built in this PoC — the launch/relaunch actions and the marking-assignment
  screens all already exist (Task 1/2/3). Step 4.7's e2e test *exercises* that existing UI as proof
  and as permanent CI regression coverage; it does not add or change any product UI.

### 6) Traceability to user stories

- **US2** (Scenarios, Simulations, Atomic testing: restricted assets hidden in targets, execution
  details, results, scores, findings, remediations) — **execution/dispatch enforcement done** by this
  PoC (steps 4.1–4.6); target/result/score/finding display-filtering already runs through Task 3's
  existing per-asset read filter. Whether the *entity itself* is hidden or filtered when it has mixed
  targets (Row 2) is explicitly **not** decided or built here — see §4.
- **US1** (Asset Groups) — **not addressed**; deferred, §4.
- **US0** (Dashboards) — **not addressed**; per `user-stories.md`'s own open question #8, dashboards
  reuse US2's result filtering once Row 2 is settled, so this PoC is a prerequisite input, not a
  completion, for US0.

---

## POC 2 — planned, not yet drafted

Will cover the items POC 1 deliberately defers (§4 above): Asset Group behaviour (US1), entity-level
hide/filter for mixed-target entities (Row 2), Finding marking inheritance (Q4), and partial-run
reporting. Scoped once POC 1 has shipped and those user-story-level decisions are confirmed.
