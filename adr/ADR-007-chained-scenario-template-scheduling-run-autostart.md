# ADR-007: For scheduled chained scenarios, provision the workflow TEMPLATE at scheduling time and create the workflow RUN only at auto-start

|         |                                                        |
|---------|--------------------------------------------------------|
| Status  | Accepted                                               |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7389 |

## 1. Context

A recurring (cron-scheduled) scenario is turned into a concrete simulation (`Exercise`) ahead
of its actual start time: `ScenarioExecutionJob` runs periodically, finds scenarios whose next
occurrence is within its lookahead window, and creates the `Exercise` row in `SCHEDULED` status.
Separately, `InjectsExecutionJob` runs periodically and promotes due `SCHEDULED` exercises to
`RUNNING` (`autoStartDueExercises`), then fires their injects.

For a **chained** scenario, the simulation also needs a workflow: a `Workflow` (TEMPLATE) holding
the step templates/conditions/scope rules, and a `Workflow` (RUN) that is actually evaluated and
whose steps get readied and enqueued for execution
(`WorkflowService#evaluateWorkflowProgress` → ready/enqueue steps).

Per `ADR-005`, a simulation's chaining logic map (step templates, condition trees, scope
rules/variables, timeout, rate-limit, safe mode) is **editable only while the simulation is
`SCHEDULED`** and is frozen once it launches. This means the workflow TEMPLATE must already exist
the moment the `Exercise` is created in `SCHEDULED` status — otherwise there is nothing for the
user to open, view or author during the entire scheduled window.

At the same time, creating the workflow RUN is not a passive record: `WorkflowService#startWorkflow`
seeds global state and immediately evaluates progress, readying and enqueuing the first steps for
execution. Classical (non-chained) injects only start firing once `autoStartDueExercises` has
promoted the exercise to `RUNNING` — chained simulations must respect the exact same timing
contract, otherwise a chained simulation would begin executing steps before its scheduled start
time, ahead of every non-chained simulation scheduled for the same moment.

We therefore have two distinct timing requirements pulling in different directions for the same
feature, and needed to decide where each half of "create the chained simulation's workflow"
belongs.

## 2. Decision drivers

- **Editability window (ADR-005 compliance)**: the workflow TEMPLATE must exist for the entire
  time the simulation is `SCHEDULED`, so the logic map can be viewed/authored before launch.
- **No early execution**: nothing chained-specific may ready or enqueue a step before the
  simulation's actual scheduled start time.
- **Timing parity with classical injects**: chained and non-chained simulations scheduled for the
  same instant must start executing at the same instant, driven by the same `RUNNING` transition.
- **Minimal blast radius**: reuse the two scheduler jobs that already own "is it time to exist"
  (`ScenarioExecutionJob`) and "is it time to run" (`InjectsExecutionJob`) rather than introduce a
  third job or a polling mechanism.
- **Idempotency**: re-running either job must not double-provision a template or double-start a
  run for the same exercise.

## 3. Considered options

### Option A: Create both TEMPLATE and RUN at scheduling time (`ScenarioExecutionJob`)

When the scenario becomes due, provision the workflow TEMPLATE and immediately start the workflow
RUN, well ahead of the simulation's actual start time.

**Pros**: single place, single job, simplest code path.
**Cons**: violates the timing contract — `startWorkflow` readies and enqueues steps immediately,
so a chained simulation would begin executing before its scheduled start and before classical
injects of simulations scheduled for the same instant. Also breaks the "frozen once launched"
guarantee from ADR-005: editing the logic map while `SCHEDULED` would race with a run that has
already started evaluating it.

### Option B: Create both TEMPLATE and RUN at auto-start time (`InjectsExecutionJob`)

Defer everything chained-specific until the exercise is promoted to `RUNNING`.

**Pros**: execution timing is trivially correct — everything starts at the same instant as
classical injects.
**Cons**: no workflow TEMPLATE exists while the simulation is `SCHEDULED`, so the user has nothing
to open/edit during that window — this directly breaks the ADR-005 contract that the logic map
is editable while scheduled.

### Option C (chosen): Split — provision the TEMPLATE at scheduling time, create the RUN at auto-start time

`ScenarioExecutionJob` provisions only the workflow TEMPLATE when the scenario becomes due
(`WorkflowService#provisionSimulationTemplateWorkflow`), so the `Exercise` exists in `SCHEDULED`
status with a fully editable logic map. `InjectsExecutionJob`, right after
`autoStartDueExercises()` promotes exercises to `RUNNING`, starts the workflow RUN only for the
exercises that were just promoted (`WorkflowService#startWorkflowBySimulationIdIfPresent`), in
lockstep with classical injects (`executeClassicalInjects`).

**Pros**: satisfies both timing requirements exactly — editable while `SCHEDULED`, executes only
once `RUNNING`, and reuses the two existing jobs without adding a third.
**Cons**: the chained "creation" of a simulation is now split across two jobs instead of one,
which requires a helper (`startWorkflowBySimulationIdIfPresent`) that is a no-op when no template
was provisioned (e.g. scenario stopped being chained between scheduling and auto-start).

## 4. Decision

We chose **Option C** because it is the only option that satisfies both the ADR-005 editability
window and the execution-timing parity with classical injects.

Concretely:
- `ScenarioExecutionJob.createScheduledExercise` creates the `Exercise` in `SCHEDULED` status and,
  if the scenario is chained, calls `WorkflowService#provisionSimulationTemplateWorkflow` — this
  creates only the workflow TEMPLATE (step templates copied from the scenario template), no RUN.
- `InjectsExecutionJob.execute` calls `autoStartDueExercises()` to promote due exercises to
  `RUNNING`, then `executeChainedSimulations(startedExercises)` calls
  `WorkflowService#startWorkflowBySimulationIdIfPresent` for each just-started exercise — this is
  a no-op if no TEMPLATE was provisioned, and otherwise creates the RUN and evaluates the first
  steps.
- `executeClassicalInjects()` remains unchanged and only fires injects for non-chained
  simulations; chained simulations are driven by the workflow engine from that point on.

## 5. Consequences

### Positive

- A scheduled chained simulation is fully editable (steps, conditions, scope) for its entire
  `SCHEDULED` window, exactly like a manually-launched chained simulation before launch.
- Chained and classical simulations scheduled for the same instant start executing at the same
  instant — no early execution, no drift.
- No new scheduler job, no new polling loop: the split reuses the two responsibilities the
  existing jobs already own.

### Negative / trade-offs

- The chained "creation" of a simulation now spans two jobs and two `WorkflowService` calls
  instead of one, which is slightly harder to trace end-to-end than a single call.
- `startWorkflowBySimulationIdIfPresent` must tolerate "no template" as a valid, silent no-op
  (e.g. a scenario un-chained between scheduling and auto-start), adding one more state to reason
  about.

### Neutral

- Manual (non-scheduled) launches are unaffected: `startWorkflowByScenarioIdAndSimulation` still
  provisions the TEMPLATE and starts the RUN in the same call, since there is no `SCHEDULED`
  window to preserve in that flow.
- No change to the `Exercise`/simulation status model or to classical inject execution.
