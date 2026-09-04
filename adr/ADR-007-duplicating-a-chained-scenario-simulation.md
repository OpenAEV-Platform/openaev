# ADR-007: Duplicating a chained scenario / simulation

|         |                                                         |
|---------|---------------------------------------------------------|
| Status  | Accepted                                                |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/5775 |

## 1. Context

Time-based scenarios and simulations can already be duplicated: `ScenarioService.getDuplicateScenario`
(`service/scenario/ScenarioService.java:1013`) and `ExerciseService.getDuplicateExercise`
(`rest/exercise/service/ExerciseService.java:256`) copy the *authored* content (metadata, injects,
teams, articles, variables, objectives, lessons, grants) and deliberately drop every *execution
artifact* (statuses, results, expectations, findings, dates). The endpoints are
`POST /api/scenarios/{id}` and `POST /api/exercises/{id}`, guarded by
`@AccessControl(actionPerformed = Action.DUPLICATE)`.

A **chained** object carries a second body of authored content those services know nothing about: its
**workflow** (`workflows`, `steps`, `conditions`, `conditions_steps`, `workflow_scope_rules`,
`scope_variables`). It also carries execution state the copy must never inherit: RUN workflows,
runtime steps, `workflow_state` rows, `steps_delay_queue` rows, scope snapshots (ADR-006), in-flight
RabbitMQ messages and runtime-generated injects.

Two half-built pieces are the starting point of this decision:

1. **The backend endpoints exist but are unreachable.** `ChainingApi.duplicateExercise`
   (`ChainingApi.java:154-176`) and `ChainingApi.duplicateScenarioChaining` (`:241-261`) already call
   `getDuplicateExercise` / `getDuplicateScenario` then `workflowService.duplicateSimulation` /
   `duplicateScenario` + `stepService.copyStepTemplate`. Note that `ChainingApi` is mapped on
   `TENANT_CHAINING_URI` (`:46`), so these routes exist **only** in their tenant-scoped form
   (`/api/tenants/{tenantId}/chaining/...`) — unlike the time-based ones, which are exposed on both
   `/api/scenarios/{id}` and `/api/tenants/{tenantId}/scenarios/{id}` (`ScenarioApi:187`,
   `ExerciseApi:526`).
2. **The frontend hides the action.** `Scenarios.tsx:299-303`, `ScenarioHeader.tsx:305-308` and
   `ExerciseHeader.tsx:408-410` strip `'Duplicate'` when the object is chained or autonomous.
   `openaev-front/src/actions/chaining/*` contains no duplicate action, and the existing actions
   (`Exercise.ts:43`, `scenario-actions.ts:96`) call the non-tenant time-based routes.

So the feature is *declared* but not wired — and, as §3 shows, the copy path behind it is not correct
enough to be exposed as-is. The reference implementation for correctness is not the copy path but the
**export/import** path (`V1_DataImporter.importWorkflow` / `importWorkflowSteps` /
`importConditionNodes`), which already rebuilds a workflow graph with fresh ids through an explicit
`Map<oldId, new>` remapping.

## 2. Decision drivers

1. **Fidelity of the authored graph** — the copy must be a *behaviourally identical* logic map: same
   steps, same event/condition trees, same sharing of a root event across several actions, same
   `DEPEND_ON` ordering, same scope, same variables.
2. **Zero execution bleed** — the copy is a brand-new, never-run object: no RUN workflow, no runtime
   step, no state, no delay queue, no snapshot, no runtime inject, no result.
3. **No dangling cross-object references** — no condition and no `step_data` / `step_input` field in
   the copy may point at an entity of the source object.
4. **One mental model with the time-based duplication** — same naming, same RBAC, same audit event; a
   user should not have to think about whether an object is chained.
5. **No duplicated remapping logic** — the graph-cloning rules must live in one place.

## 3. What is broken today

`StepService.copyStepTemplate` (`StepService.java:367`) → `copyStepsTemplate` (`:660`) →
`copyStepConditionTemplate` (`:704`) is the **single cloning primitive** of the engine. It has five
production call sites:

| Call site | Purpose |
|---|---|
| `ChainingApi.java:173` / `:258` | duplication (the feature being built) |
| `WorkflowService.java:1395` | **launch** — scenario TEMPLATE → simulation TEMPLATE |
| `WorkflowService.java:1426` | autonomous plan-mode provisioning |
| `WorkflowService.java:958` | convert-to-manual |

Its defects are listed below. D1–D3 are graph-copy defects in that shared primitive, so they affect
**every** call site; D4–D5 are duplication semantics; D6 is tenancy.

| # | Defect | Evidence | Impact |
|---|---|---|---|
| D1 | **`DEPEND_ON` condition values are not remapped.** `.value(condition.getValue())` is copied verbatim, but for a `DEPEND_ON` condition the value **is a step template id**. | `StepService.java:767` / `:803`; `AttackPathKillChainResolver.java:14-16,44` | **The dependent step never executes, silently** — see §3.1. Not cosmetic metadata: a permanently blocked branch of the attack path, with no error, only a `log.debug`. |
| D2 | **Standalone conditions are not copied.** Conditions with no `conditions_steps` link (events authored but not yet attached) are skipped. | Explicit `// Todo add condition not linked to a step`, `StepService.java:370` | The copy silently loses authored events. The import path *does* handle them (`V1_DataImporter:2609-2618`). |
| D3 | **`step_data` / `step_input` are only partially re-pointed.** Only `inject_exercise` is rewritten in `step_data`; `step_input` is copied verbatim. | `StepService.java:667-671`, `:677` | Every other id frozen in either blob is carried over to the copy. Each id-bearing field must be classified: **re-point** (owner scoping, e.g. `inject_exercise` / `inject_scenario`), **keep** (same-tenant shared reference: injector contract, asset, asset group, team, document, tag) or **clear** (runtime-only, e.g. `inject_id`). The resulting table is an implementation deliverable and goes in the PR description. |
| D4 | **Injects are duplicated for a chained simulation.** `getDuplicateExercise` calls `getListOfDuplicatedInjects` unconditionally — but a chained simulation's injects are **created at runtime** by `InjectExecutionStep`. | `ExerciseService.java:265`; `ChainingApi.java:165` | Duplicating a launched chained simulation clones its runtime injects into the copy. Execution bleed. Note this defect lives in `ExerciseService`, so **it must be fixed there whichever entry point is chosen** (§4.1). |
| D5 | **Copy hygiene of the workflow row itself is unverified.** `copyWorkflowTemplateToScenario` / `...ToSimulation` (`WorkflowService.java:548-605`) copy config + scope rules + variables via `WorkflowScopeRule.copyOf` / `ScopeVariable.copyOf`. | | The resulting state must be asserted, not assumed — see the contract in §6. |
| D6 | **Multi-tenancy is implicit.** Chaining entities are **not** tenant-scoped (no `TenantBase`, no `tenant_id`, no `@Filter`); tenancy is derived by joining to the owning `Exercise` / `Scenario`. | `StepRepository.java:130-148` | The duplication must resolve source and target through the tenant-filtered `Scenario` / `Exercise` and must never join two objects of different tenants. Needs a dedicated isolation test. |

One further flaw of the current code needs **no fix**: `ChainingApi.java:168-170` / `:252-254` throw
`ChainingException` when the source has no TEMPLATE workflow, and since `ChainingException extends
Exception` with no `@ResponseStatus` and no `@ExceptionHandler`, that surfaces as **HTTP 500** for a
legitimate client situation. The entry point chosen in §4.1 dissolves it: with a single endpoint,
"no workflow" simply means the chained branch is not entered. Recorded so it is not re-introduced as a
"graceful degradation" branch.

### 3.2 The endpoints are not REST-compliant

This applies to the chained *and* the time-based duplicate endpoints. All four return **HTTP 200** and a
**raw JPA entity** (`Scenario` / `Exercise`, both `@Entity`), which breaks
`api.instructions.md:10` / `:32` — *"never expose JPA entities in API responses"* — and couples the
response shape to the schema, with lazy-loading cost at serialization. None returns `201 Created` with a
`Location` header, although the newer code does exactly that (`StepApi:53`, `TenantApi:37`,
`PlatformRoleApi:37`). `POST /collection/{id}` is itself the wrong shape: the URI names the *source*, not
what is created. In addition, the two `ChainingApi` duplicates carry no `@Operation` (so they are absent
from the OpenAPI schema and from `api-types.d.ts`) and orchestrate four service calls inline, against
`api.instructions.md:29`.

The aggravating factor: the time-based endpoints live in `io.openaev.rest`, the **legacy** package that
is explicitly grandfathered, whereas the `ChainingApi` ones live in `io.openaev.api`, the **new** package
where these conventions are fully in force. See §6 for the scope decision on this.

### 3.1 Why D1 is the decisive defect

Three facts combine:

- **(A)** A `DEPEND_ON` condition stores a **step template id in `condition_value`** — not in a FK
  column (`AttackPathKillChainResolver.java:14-16,44`).
- **(B)** At runtime, `ConditionService.evaluateDependOnConditions:686` resolves it as
  `stepRepository.existsByStepTemplateIdAndWorkflowId(condition.getValue(), workflowRun.getId())`
  (`StepRepository.java:246`) — *"has a runtime step with this `step_template_id` already been created
  in this RUN?"*
- **(C)** Runtime steps are created with `stepTemplate = <the template being executed>`
  (`StepService.createReadyStepFromBatch:510`), i.e. a template of the **destination** workflow.

The stored value must therefore be a step template id **of the destination workflow**. Copying it
verbatim leaves an id belonging to the **source** workflow, the `exists` query can never return true,
`ConditionService.checkCondition:637` returns `emptyList()`, and the step is never promoted to READY.

**Why it has not been observed yet.** The Logic UI does not author `DEPEND_ON`; in practice it is
produced only by autonomous attack-path authoring (`WorkflowService:1804` / `:1975`), which writes
conditions **directly onto the simulation TEMPLATE**, with no copy in between. It is nonetheless
already reachable through convert-to-manual (`:958`) and autonomous plan provisioning (`:1426`), and
becomes reachable for everyone the moment duplication ships — duplicating an attack-path scenario is
precisely the target use case.

This is what makes the primitive *broken code*, not *working code to be protected* — the pivotal input
to §4.2.

## 4. Considered options

Two independent questions: where the user-facing entry point lives (§4.1), and where the D1–D3 fix
lives (§4.2).

### 4.1 The entry point

**Option A — keep the existing `ChainingApi` endpoints.** Fix
`POST /api/tenants/{tenantId}/chaining/{scenarios,simulations}/{id}` and let the frontend choose the
endpoint based on `isScenarioChaining` / `isSimulationChaining`.
*Pros*: the endpoints and their declarative EE gate already exist. *Cons*: two endpoints for one
user-visible action, so the frontend must know the object's nature before duplicating, every future
caller (bulk actions, API clients) must replicate that dispatch, and `Action.DUPLICATE` audit events are
emitted from two controllers. It also perpetuates two endpoints that breach the conventions of their own
package (§3.2), and leaves chained duplication available *only* on the tenant-scoped route while the
frontend calls the non-tenant one. **Its "smallest diff" argument does not hold**: D4 lives in
`ExerciseService.getDuplicateExercise`, so that service must be modified either way. **Rejected.**

**Option B — one entry point, the caller never dispatches** ← **CHOSEN**. Keep
`POST /api/scenarios/{id}` and `POST /api/exercises/{id}` (and their `/api/tenants/{tenantId}/…`
variants) as the only duplicate endpoints, handle chaining behind them, and delete the `ChainingApi`
duplicates.
*Pros*: one endpoint, one audit event, one RBAC check, one frontend action per object type; best fit for
driver #4; **dissolves the "no workflow ⇒ 500" defect** (§3); covers the tenant-scoped and non-tenant
routes at once; removes two convention-breaching endpoints instead of entrenching them. Cheap to wire,
because the dependency it needs already exists: `ExerciseService` injects `WorkflowService` (`:151`) and
`StepService` (`:156`), `ScenarioService` injects `WorkflowService` (`:158`), one-way and with no bean
cycle — only `StepService` must be added to `ScenarioService`.
*Cons*: EE validation stops being declarative (§5); deleting two public routes is formally a breaking
change; and the branch placement needs care because of the `AutonomousRunService` caller (§5).

**Option C — duplicate through the export/import pipeline.** Serialize with the exporter, re-import in
the same tenant.
*Pros*: reuses the only correct remapping implementation. *Cons*: **disqualifying** — export is lossy
by design. `WorkflowExportInitializer.filterAssetScopeRules` drops asset scope rules, `step_data` is
rewritten into contract snapshots, and the importer re-resolves contracts and **skips** unresolvable
steps. A same-tenant duplicate must lose nothing.

### 4.2 Where the D1–D3 fix lives

**Option A1 — a duplication-only copy method** (e.g. `copyStepTemplateForDuplication`), leaving
`copyStepTemplate` untouched.
*Pros*: zero regression risk on launch. *Cons*: ships the cloning logic twice — corrected in one
branch, broken in the other — which guarantees drift and leaves §3.1 live on launch. Its rationale was
*"do not disturb working code"*, but §3.1 shows there is no working behaviour to protect here.
**Rejected.**

**Option A2 — fix the primitive in place, one single version** ← **CHOSEN**.
*Pros*: one implementation, all five call sites corrected at once, no drift. *Cons*: touches the launch
path — the highest-risk code in the engine (mitigated in §5).

**Option D — extract a shared `WorkflowGraphCloner`** used by launch, duplication, convert-to-manual
and ideally the importer.
*Pros*: the clean end state; satisfies drivers #1, #3 and #5 structurally. *Cons*: creating a component
and re-pointing five call sites is a **refactor**, and this ADR is scoped to shipping a working
duplication. Rejected as *scope*, not as *intent* — A2 captures the same correctness benefit at a
fraction of the cost, since it adds a `Map<String oldStepId, Step newStep>` inside a method that
already carries a `copiedConditionsByOriginalId` map: no new class, no moved call site, no signature
change visible to callers.

## 5. Decision

**Option B + Option A2**: make the existing time-based duplicate endpoints the single entry point for
both kinds of object, and fix the graph-copy defects **in place, in the single existing cloning
primitive**.

1. **The primitive is fixed, not forked.** In `copyStepsTemplate` (`:660`), build an explicit
   `Map<String oldStepId, Step newStep>` as each step copy is saved, thread it into
   `copyStepConditionTemplate` (`:704`), and consume it in three places: the D1 `DEPEND_ON` value
   rewrite, the D3 `step_data` / `step_input` re-pointing, and the D2 standalone-condition pass.
   Cloning order mirrors `V1_DataImporter`: workflow → steps (build the id map) → conditions (consume
   it, two passes so parents resolve) → `conditions_steps` links.
2. **All five call sites inherit the fix**, deliberately. Launch and autonomous plan mode stop silently
   dropping dependent steps; convert-to-manual produces a copy whose attack path actually fires.
3. **`POST /api/scenarios/{id}` and `POST /api/exercises/{id}` handle both cases.** When the source has
   a TEMPLATE workflow, the workflow is cloned as part of the same transaction; when it has none, the
   existing time-based behaviour is unchanged. The two `ChainingApi` duplicate endpoints are **deleted**.
4. **The frontend simply stops hiding the action** — the three gating ternaries are removed and the
   existing `duplicateScenario` / `duplicateExercise` actions are reused. No new action, no dispatch.
5. **One PR.**

### Where the chained branch is placed — and the `AutonomousRunService` trap

The obvious implementation ("detect chaining inside `getDuplicateScenario` / `getDuplicateExercise`") is
**wrong as stated**, and this is the main implementation risk of Option B.

`AutonomousRunService.convertToManual(DUPLICATE)` (`:1700`) already calls
`scenarioService.getDuplicateScenario(scenarioId)` on an autonomous **chained** scenario, and then
*deliberately* copies the workflow itself via `workflowService.copyScenarioChainingWorkflowAsManual`
(`:1702`) with `keepAlive` forced off. If the workflow copy is made implicit inside
`getDuplicateScenario`, that caller performs **two workflow copies** and the duplicate ends up with two
TEMPLATE workflows.

The chained branch must therefore be **explicit, never implicit**. Either:
- keep `getDuplicateScenario(id)` / `getDuplicateExercise(id)` metadata-only and add an overload taking
  an explicit `boolean includeWorkflow` (or a small `DuplicationOptions`), the API layer passing `true`
  and `AutonomousRunService` keeping `false`; or
- orchestrate the chained branch in the API layer, leaving both services untouched on that point.

Either way, **`AutonomousRunService:1700` must keep producing exactly one workflow copy**, and a test
must pin that.

Note that D4 is handled in the same place: `getListOfDuplicatedInjects` must be skipped when the source
is chained, since its injects are runtime artefacts.

### Controlling the risk on the launch path

- The change is **additive**: remapping is introduced where ids were previously copied raw. No control
  flow, ordering or transaction boundary is modified.
- `ChainingIntegrationTest`, `StepServiceScenarioIntegrationTest` and `WorkflowServiceTest` cover the
  affected paths and must be green **before and after**.
- The PR adds a launch-path test asserting that a simulation launched from a scenario carrying a
  `DEPEND_ON` condition actually executes the dependent step. **That test fails on the base commit** —
  the before/after result is the proof D1 is fixed.

### Not in scope

Option D remains the long-term end state. A2 does not deliver it but does not move away from it either:
there is still exactly one implementation to extract later.

## 6. Semantics of the duplicate — the functional contract

The rule mirrors the time-based semantics: **copy what was authored, drop what was run.**

**Always copied** (authored):
- Scenario/simulation metadata, as `copyScenario` / `copyExercise` already do, including the
  `" (duplicate)"` suffix from `StringUtils.duplicateString`.
- The **TEMPLATE** workflow: configuration (rate limit, max attempts, temporal rate, timeout, safe
  mode), scope rules — **including** asset / asset-group / security-platform rules, since the copy stays
  in the same tenant and references remain valid, unlike export — and scope variables.
- All TEMPLATE steps with their `step_data`, outputs, output parsers, inputs and limits.
- The full condition graph: event trees, MAPPER conditions, `DEPEND_ON` links, shared roots (sharing
  must be preserved, not flattened) and standalone conditions.

**Never copied** (execution):
- RUN workflows and every runtime step (`step_template_id != null`, status READY/RUN/END).
- `workflow_state` / `WorkflowStateEntries`, `steps_delay_queue` rows, in-flight queue messages.
- `ScopeRuleSnapshot` `snapshotStart` / `snapshotEnd` (ADR-006 — snapshots are execution-time).
- Runtime-generated injects and their statuses, expectations, findings and traces; attack-path
  executions; `asset_agent_jobs`.
- `workflow_is_edited`, timestamps, the `workflowTemplate` back-reference, `workflowsExecuted`.

**Resulting state** (the D5 assertions): workflow `status = TEMPLATE`, `version` reset to its initial
value, `isEdited = false`, `keepAlive = false`, `timeoutEnabled = true`, `workflowTemplate = null`,
`workflowsExecuted` empty, snapshots null; simulation `status = SCHEDULED` with no start/end date; the
copy passes `WorkflowEditability.assertLogicMapEditable`. `keepAlive` / `timeoutEnabled` follow the
precedent set by `copyScenarioChainingWorkflowAsManual:958-959`.

### Settled sub-questions

**Source status — any status is duplicable** (`SCHEDULED`, `RUNNING`, `FINISHED`, `CANCELED`). The
business case is "re-run this finished or canceled simulation as a new one", not only duplicating
something that never started. The safety guarantee is **not** the source status: it is that the cloner
reads only from the TEMPLATE workflow and never from a RUN workflow, so execution bleed is prevented by
construction. Duplicating a live `RUNNING` simulation must leave it strictly untouched.

**Scenario link — kept.** The duplicated simulation keeps `exercise.scenario`, exactly as the
time-based `copyExercise` does (`ExerciseService.java:280`). The copy stays traceable as "a run of
scenario X" while owning its own independent, editable logic map.

**Autonomous objects — the generic Duplicate stays blocked**, as the frontend does today.
`AutonomousRunService.convertToManual(DUPLICATE)` already covers "give me an editable manual copy of
this AI run", with `keepAlive=false` forced. A second path for the same intent would create two
divergent semantics for one use case, against driver #5.

**Workflow version — reset**, not inherited. The duplicate is a brand-new object with a clean history;
inheriting the version would falsely imply revision continuity with the source and contradict forcing
`isEdited=false` and clearing `workflowTemplate`.

**Invalid EE licence — reject explicitly** with an "Enterprise Edition license required" error. Chaining
is EE-only; silently degrading to a metadata-only copy would make the user lose the logic map with no
signal, violating driver #1.

Option B changes **how** this is enforced. The shared endpoint serves both chained and time-based
objects, so it cannot carry `@AccessControl(..., isEnterpriseEdition = true)` — that flag is
unconditional, applied by `AccessControlAspect:55` before anything else. The check must therefore become
programmatic, inside the chained branch only:

```java
if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
  throw new EnterpriseEditionException("Enterprise Edition license required");
}
```

This is the one place where Option B *weakens* a guarantee: an aspect-enforced invariant becomes
hand-written code. It must therefore have its own test (chained source + inactive licence ⇒ rejected,
and time-based source + inactive licence ⇒ still duplicable).

**REST contract — inherited, not fixed here.** §3.2 shows the surviving endpoints return `200` and a raw
JPA entity rather than `201 Created` and an Output DTO. Changing that is a breaking change for the
frontend and for every API client, and it is unrelated to "duplicate a chained object" — so this PR
**keeps the existing contract** and the debt is recorded in §7. What the PR *does* fix, because Option B
removes the offending code, is the two undocumented, convention-breaching `ChainingApi` endpoints and the
HTTP 500 on the no-workflow case.

## 7. Consequences

### Positive
- Chained scenarios and simulations become first-class duplicable objects; "re-run as new" no longer
  requires an export/import round-trip.
- **One endpoint per object type**, on both the tenant-scoped and non-tenant routes: one audit event,
  one RBAC check, one frontend action. No caller ever has to know whether an object is chained.
- **One cloning implementation, not two** — duplication, launch, autonomous plan provisioning and
  convert-to-manual cannot drift apart.
- **A live execution bug is fixed rather than shipped twice**: D1 stops silently blocking dependent
  steps on every path.
- Two undocumented endpoints that breached the conventions of `io.openaev.api` (§3.2) are removed rather
  than entrenched, and the HTTP 500 on the no-workflow case disappears.

### Negative / trade-offs
- **EE enforcement stops being declarative** for this action (§6). An aspect-enforced invariant becomes
  hand-written code, and must be pinned by its own test.
- **The launch path is modified** — the highest-risk code in the chaining engine. This is the deliberate
  cost of not duplicating the cloner; see the mitigations in §5.
- **Deleting two public routes is formally a breaking change.** In practice they are unreachable from the
  UI, absent from the OpenAPI schema and have no other caller, so the real-world impact is limited to
  direct API users. Announce it in the release notes regardless.
- **The chained branch must stay explicit**, or `AutonomousRunService:1700` silently produces two
  workflow copies (§5).
- Allowing duplication of a `RUNNING` simulation means the read path must be *provably* TEMPLATE-only;
  a regression there would leak live execution state into the copy.

### Neutral
- No schema change expected — no migration, no ES reindex. **[VERIFY]** during implementation.
- Chaining entities stay non-tenant-scoped; isolation keeps relying on the owning `Scenario` /
  `Exercise`. **D6 is the highest silent-risk item of the whole change** and gets its own dedicated
  isolation test in `ChainingRbacIsolationTest` — not a minor checklist entry.
- The duplicate endpoints keep returning `200` and a raw JPA entity (§6). Bringing them up to
  `201 Created` + Output DTO is a separate, breaking piece of work and belongs in its own issue.
