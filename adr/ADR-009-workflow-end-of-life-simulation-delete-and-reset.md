# ADR-009: Workflow end-of-life on simulation DELETE and RESET

|         |                                                               |
|---------|---------------------------------------------------------------|
| Status  | Accepted                                                      |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/176 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/177 |

## 1. Context

ADR-007 introduced a single, cause-aware pipeline (`WorkflowEndService.manageWorkflowEnd`) for
ending a chaining workflow on `TIMEOUT`, `CANCELED` and `NO_MORE_PROGRESS`, but explicitly left the
`DELETED` cause out of scope: deleting or resetting a simulation only cleared its
`workflow_states`, while the workflow row itself, its steps/conditions, and the attack-path
executions were cleaned up separately (or not at all), through ad-hoc calls scattered in
`ExerciseService`.

This change closes that gap by folding simulation deletion and simulation reset into the same
`manageWorkflowEnd` pipeline, with two dedicated causes, and by making `ExerciseService` delegate
all chaining cleanup to `WorkflowService` instead of orchestrating it itself.

## 2. Decision

A simulation's chaining workflow can be in one of three families of state when the simulation is
deleted or reset:

- **No workflow execution** — the simulation is `SCHEDULED`/`DRAFT` and was never launched: only
  the workflow `TEMPLATE` exists, there is nothing to end.
- **Already ended** (`END`) — the simulation was `CANCELED` or `FINISHED`.
- **Still active** (`RUN`) — the simulation is `RUNNING`.

The first case simply has nothing to do for the "required clean end" / "delete workflow execution"
steps below (`findAllWorkflowExecutionBySimulationId` returns an empty list). Both remaining flows
first make sure the workflow is properly ended (running the same shared cleanup as
ADR-007: end active steps, stop active injects, clear the delay queue, delete asset agent jobs,
delete workflow states) before doing anything destructive. This is the **"required clean end"**
step in the diagrams below — it only does something when the workflow was still active; it is a
no-op (idempotency guard from ADR-007) when the workflow is already `END`.

Two new causes drive the destructive part:

- **`DELETED_BY_SIMULATION_DELETION`** — the simulation itself is being deleted
  (`ExerciseService.deleteById`). The workflow **row is left alone**: the attack-path executions
  are deleted explicitly (no FK to the simulation, so they are never cascade-deleted), and the
  native `exercise` delete then cascades the workflow execution *and* the workflow template rows
  (and everything under them: scope rules, steps, conditions) at the database level.
- **`DELETED_BY_RESET_SIMULATION`** — the simulation is reset back to `SCHEDULED`/`DRAFT`
  (`ExerciseService.resetExercise` family). Only the **workflow execution** row is deleted (plus
  its attack-path executions), by application code (`workflowRepository.delete(workflowRun)`) —
  there is no simulation deletion to cascade from. The **workflow template is kept** so the
  simulation can be relaunched, but its scope rules are cleaned (`cleanScopeRulesSimulation`) so
  stale allow/deny entries pointing at assets that no longer resolve don't leak into the next run.

`WorkflowService` exposes the two orchestrators used by `ExerciseService`:

- `deleteSimulationDeleteWorkflows(simulationId)` — finds every non-template workflow of the
  simulation (`RUN`, `END`, `STOP`) and ends them with `DELETED_BY_SIMULATION_DELETION`, called
  **before** `exerciseRepository.deleteById(...)` so the cascade above applies.
- `resetSimulationDeleteWorkflowExecution(simulationId)` — same lookup, ends them with
  `DELETED_BY_RESET_SIMULATION`, then cleans the template's scope rules.

`ExerciseService` no longer calls `attackPathExecutionService`, `cleanScopeRulesSimulation` or
`workflowRepository` directly for these flows: it only calls into `WorkflowService`.

One extra guard: `cancelSimulationEndWorkflowRun` (used when a still-`RUNNING` simulation is
force-stopped or deleted) now skips workflows already in `TEMPLATE` status — a template is not a
run and must never be forced through the end-of-life pipeline.

### DELETE_SIMULATION

Three actions, gated by the simulation/workflow-execution status on the left:

1. **Required clean end** — only relevant when the simulation is still `RUNNING` / workflow is
   `RUN`: ends the simulation, ends active steps/injects, deletes workflow state, step delay and
   asset agent jobs (the ADR-007 shared cleanup).
2. **Delete workflow execution** — deletes the attack path explicitly, then cascade-deletes the
   workflow execution row and everything under it (scope rules, steps, conditions execution) by
   simulation id.
3. **Delete workflow template** — cascade-deletes the workflow template row and everything under
   it (scope rules, steps, conditions template) by simulation id, as part of the native exercise
   delete.

### RESET_SIMULATION

Only one action applies, since the simulation stays alive (it goes back to
`SCHEDULED`/`DRAFT`, it is not deleted): **delete workflow execution** — same attack-path
deletion, but the cascade is scoped to the workflow id (not the simulation id), because the
workflow template must survive so the simulation can be relaunched. The workflow template itself
is left untouched; only its scope rules are cleaned separately (`cleanScopeRulesSimulation`), to
drop stale allow/deny entries before the next run.

## 3. Consequences

### Positive

- Simulation delete and reset now go through the same audited, idempotent end-of-life pipeline as
  `TIMEOUT`/`CANCELED`/`NO_MORE_PROGRESS`, instead of a separate, partial cleanup in
  `ExerciseService`.
- Attack-path executions are no longer orphaned on delete or reset — they were previously deleted
  in two different, inconsistent places.
- `ScopeSnapshotService.freezeEnd` is skipped for the two deletion causes: freezing an end
  snapshot for a workflow about to be deleted was pure waste and risked writing a snapshot for a
  row that no longer exists a moment later.

### Negative / trade-offs

- `DELETED_BY_SIMULATION_DELETION` relies on the database cascade (FK `ON DELETE CASCADE` from
  `workflow.simulation_id`) to remove the workflow/template rows; a reader must know this to
  understand why `WorkflowEndService` does not call `workflowRepository.delete(...)` on this
  branch, unlike on `DELETED_BY_RESET_SIMULATION`.
- Two deletion causes instead of one (`DELETED`) makes the `WORKFLOW_END_CAUSE` enum longer and
  requires call sites (e.g. `stopActiveInjects`, `stopSimulationByEndWorkflow`) to keep matching
  both consistently.

### Neutral

- No change to the public API surface (`ExerciseController` / DTOs).
- No change to the ADR-007 pipeline for `TIMEOUT` / `CANCELED` / `NO_MORE_PROGRESS`.
