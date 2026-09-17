# ADR-007: Workflow end-of-life on CANCELED / TIMEOUT / NO_MORE_PROGRESS

|         |                                                         |
|---------|---------------------------------------------------------|
| Status  | Accepted                                                |
| Related | https://github.com/OpenAEV-Platform/filigran-private/issues/168 |

## 1. Context

A chaining-engine workflow run (`Workflow`, `workflow_status`) reaches `END` for one of four
reasons, modeled as `WorkflowEndService.WORKFLOW_END_CAUSE`:

- **`TIMEOUT`** — `WorkflowTimeoutJob` (Quartz) periodically force-completes any `RUN` workflow
  whose `workflow_timeout_seconds` has elapsed since `workflow_created_at`.
- **`CANCELED`** — the user stops a running simulation (`ExerciseService.changeExerciseStatus`,
  `RUNNING → CANCELED`), which cancels every `RUN` workflow attached to it.
- **`NO_MORE_PROGRESS`** — `WorkflowService.evaluateWorkflowProgress` detects, after processing a
  step event, that no step template produced a new READY step and the delay queue is empty: the
  run has nothing left to do.
- **`DELETED`** — the simulation itself is deleted or reset. **Out of scope for this ADR**: today
  `manageWorkflowEnd` only resets the workflow's `workflow_states` on this path (via
  `WorkflowService.resetSimulationDeleteWorkflow`); the rest of the delete/reset cleanup
  (workflow record itself, scenario relationship) is tracked separately and developed on another
  branch.

Before this change, each cause had **its own, partially duplicated end-of-life implementation**:
`forceCompleteWorkflowByTimeout` inlined step/inject/simulation cleanup for `TIMEOUT`;
`WorkflowService.cancelSimulationEndWorkflowRun` inlined a *different* version of the same
cleanup for `CANCELED` (using mocked/stubbed services in a way that silently stopped mutating
step status once `StepService` was introduced); `NO_MORE_PROGRESS` only called
`stopSimulationByEndWorkflow` and left steps, the delay queue, the injects, and the asset agent
jobs untouched. This drift is exactly how earlier runs ended up in `END` status while their
non-template steps stayed `READY`/`RUN` and their `workflow_states` were never cleared — the
historical rows healed by migration `V6_20260908142053000`.

We want a **single, cause-aware end-of-life pipeline** that every trigger goes through, so the
three causes stay consistent by construction instead of by manual duplication.

## 2. Decision drivers

- **Single source of truth**: one method owns "what happens when a workflow ends", so a future
  cleanup step is added once and applies to every cause automatically.
- **Idempotency**: a workflow already `END` must never re-run its end-of-life side effects (the
  frozen end scope snapshot — ADR-006 — must not be overwritten; deletes must be safe to skip).
- **Cause-appropriate behavior**: not every side effect makes sense for every cause (e.g. a
  `CANCELED` run's simulation was already stopped by the user and must not be re-finished by
  `stopSimulationByEndWorkflow`; `NO_MORE_PROGRESS` finding an active inject is an anomaly, not
  the expected case, and must be logged as such rather than silently swallowed).
- **Fail loud on invariant violations**: if a cause's assumptions do not hold (e.g. `RUN` steps
  still open when the run is deemed `NO_MORE_PROGRESS`), log an error instead of masking the
  inconsistency.
- **Smallest blast radius**: reuse the existing per-concern services (`StepService`,
  `StepDelayQueueService`, `InjectService`/`InjectStatusService`, `AssetAgentJobRepository`,
  `WorkflowStateRepository`) rather than introduce a new orchestration layer.

## 3. Considered options

### Option A: Keep one bespoke method per cause (status quo)

Each caller (`WorkflowTimeoutJob`, `cancelSimulationEndWorkflowRun`,
`evaluateWorkflowProgress`) keeps its own inline sequence of cleanup calls.

**Pros**: no shared code to reason about; a cause's behavior can diverge freely.
**Cons**: proven to drift (see Context) — a cleanup step added for one cause is silently missing
for the others; duplicated step/inject/asset-job/state cleanup logic; harder to test the
idempotency guard once per cause instead of once overall.

### Option B: One shared `manageWorkflowEnd(workflowRun, cause)` entry point, switching on cause

A single package-private method holds the status transition, the idempotency guard, and the end
scope snapshot freeze **unconditionally**, then branches on `cause` for the cause-specific
cleanup (`TIMEOUT`/`CANCELED`/`NO_MORE_PROGRESS` share one branch; `DELETED` its own, currently a
no-op besides workflow-state cleanup driven from a separate call path).

**Pros**: idempotency and snapshot-freeze logic live in exactly one place; the three "real end"
causes share the same cleanup sequence (simulation → steps → delay queue → injects → asset
jobs → workflow states) with only the *inputs* differing per cause (e.g. `CANCELED` skips
re-finishing the simulation, `NO_MORE_PROGRESS` logs an anomaly if it finds active work); adding a
step to the pipeline benefits every cause at once.
**Cons**: the switch's cases are not fully symmetric (`DELETED` behaves differently, and is
still incomplete) — the single-method shape can tempt future maintainers to overload it further
without keeping the cause branches honest. Mitigated by keeping the javadoc explicit about what
each cause does and does not trigger.

### Option C: A generic pipeline of independent "step" interfaces (strategy/chain-of-responsibility)

Define an ordered list of cleanup steps (`EndActiveSteps`, `ClearDelayQueue`,
`StopActiveInjects`, ...) each implementing a common interface, executed unconditionally with a
per-step cause check.

**Pros**: maximal composability; a new cause only declares which steps apply.
**Cons**: over-engineered for four causes and five cleanup concerns; adds an abstraction layer
(interfaces, a registry, ordering guarantees) with no current requirement for pluggability;
harder to read top-to-bottom than a single method with a `switch`.

## 4. Decision

We chose **Option B** because it directly targets the decision driver that matters most here —
a single source of truth that prevents the exact duplication bug that motivated this ADR — while
staying at the complexity level the current four causes actually need.

Concretely, `WorkflowEndService.manageWorkflowEnd(Workflow workflowRun, WORKFLOW_END_CAUSE cause)`
is the single entry point every trigger funnels through (via the public `endWorkflow` /
`forceCompleteWorkflowByTimeout` facades):

```java
void manageWorkflowEnd(Workflow workflowRun, WORKFLOW_END_CAUSE cause) {
  if (WorkflowStatus.END.equals(workflowRun.getStatus())) {
    return; // idempotent: never re-run side effects on an already-ended run
  }
  workflowRun.setStatus(WorkflowStatus.END);
  scopeSnapshotService.freezeEnd(workflowRun); // ADR-006 — exactly once, whatever the cause

  switch (cause) {
    case TIMEOUT, CANCELED, NO_MORE_PROGRESS -> {
      stopSimulationByEndWorkflow(workflowRun, cause);   // no-op for CANCELED
      stepService.endActiveStepsByWorkflowId(workflowRun.getId(), cause);
      stepDelayQueueService.deleteAllByWorkflowRun(workflowRun, cause);
      if (workflowRun.getSimulation() == null) break; // logged, nothing more to clean up
      stopActiveInjects(workflowRun.getSimulation().getId(), cause);
      deleteAllAssetAgentJobsBySimulationIds(simulationId, tenantId, cause);
      deleteWorkflowStatesBySimulationId(simulationId, cause);
    }
    case DELETED -> { /* handled on a separate branch; see Context */ }
  }
}
```

Cause-specific behavior inside the shared branch:

| Step | TIMEOUT | CANCELED | NO_MORE_PROGRESS |
|---|---|---|---|
| Finish simulation (`FINISHED`) | ✅ | ❌ (already stopped by the user) | ✅ |
| End active steps → `END` | ✅ | ✅ | ✅ (should already be none left — logged if any) |
| Clear step delay queue | ✅ | ✅ | ✅ (should already be empty — logged if any) |
| Stop active injects → `ERROR` + trace | ✅ (`TIMEOUT` trace) | ✅ (`INTERRUPTED` trace) | ✅ (`NO_MORE_PROGRESS` trace, logged as anomaly) |
| Delete asset agent jobs | ✅ | ✅ | ✅ |
| Delete workflow states | ✅ | ✅ | ✅ |

The idempotency guard and the end-scope-snapshot freeze run **unconditionally before** the
`switch`, so they apply identically to every cause including `DELETED`.

## 5. Consequences

### Positive

- One place to audit "what happens when a workflow ends" — the table above is the whole
  contract, instead of three diverging call sites.
- The idempotency guard (`workflowRun.getStatus() == END → return`) is now enforced exactly once,
  eliminating the class of bug where a duplicated inline implementation forgot to reload/guard
  and re-ran cleanup on an already-ended run.
- Anomalies are now surfaced: `NO_MORE_PROGRESS` finding active steps/injects/delay-queue entries
  logs an `error`-level line instead of silently skipping or crashing, giving operators a signal
  that the "no more progress" assumption was violated.
- Cleanup that used to be scattered (delete asset agent jobs, delete workflow states) now happens
  for `CANCELED` too, closing the gap that produced the stale data healed by migration
  `V6_20260908142053000`.
- `deleteAllAssetAgentJobsBySimulationIds` still resolves its tenant from
  `TenantContext.getCurrentTenant()` rather than from `workflowRun.getSimulation().getTenant()`,
  but both background-job callers of `manageWorkflowEnd` now set `TenantContext` (and open a
  matching `TenantScopedTransaction`) to the run's own tenant before reaching this path:
  `WorkflowTimeoutJob` per expired workflow, `QueueChainingJob` per delay-queue item (`executeNew`,
  since the job's outer transaction already spans the atomic multi-tenant pop). This closes the
  gap originally flagged below for `TIMEOUT`/`NO_MORE_PROGRESS` on non-default tenants;
  `CANCELED` was already safe via `TenantInterceptor` on the HTTP thread.

### Negative / trade-offs

- The `DELETED` cause is asymmetric with the other three: it does not go through the shared
  cleanup branch at all, and is only reachable today via `WorkflowService.resetSimulationDeleteWorkflow`
  clearing `workflow_states` — not via `manageWorkflowEnd`'s own `switch`. A reader must know this
  to avoid assuming `DELETED` gets the same treatment as the other causes.
- The shared branch's per-step "is this relevant for this cause" logic (e.g. `stopSimulationByEndWorkflow`'s
  early return on `CANCELED`) lives inside each called method rather than in the `switch` itself,
  so understanding the full behavior of one cause requires reading several methods, not just the
  table.

### Neutral

- No change to the public API surface (`WorkflowController` / DTOs) or to the four
  `WORKFLOW_END_CAUSE` enum values.
- Historical data healed once by migration `V6_20260908142053000__Heal_legacy_ended_workflow_steps_and_states`
  (steps forced to `END`, orphaned `workflow_states` deleted for pre-existing `END` workflows) —
  not part of the ongoing contract, a one-time backfill.
