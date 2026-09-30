# ADR-010: Workflow pause/resume lifecycle

|         |                   |
|---------|-------------------|
| Status  | Accepted          |
| Related | issue private 178 |

## 1. Context

We need a deterministic pause/resume behavior for a workflow run.

When a run is paused, we must stop producing and consuming new READY/DELAY work, while allowing already-running work to finish and propagate its normal side effects (step updates, inject updates, attack path updates).

The model also needs explicit pause timing persistence to correctly shift time-based goals when resuming.

## 2. Decision drivers

- **Execution safety**: pausing must prevent new consumption, but must not corrupt in-flight executions.
- **Predictability**: stop/resume must produce stable and auditable state transitions.
- **Time correctness**: resumed delays/time goals must account for total paused duration.
- **Minimal behavior change**: preserve current in-flight lifecycle behavior (run/update/end) as much as possible.

## 3. Considered options

### Option A: Hard freeze everything at pause time

Block READY/DELAY consumption and also force-stop RUN/inject/agent updates immediately.

**Pros**: simple mental model, strict pause.
**Cons**: breaks in-flight lifecycle semantics, increases interruption side effects, high risk on partial updates.

### Option B (chosen): Soft pause with blocked consumption only

Set workflow to STOP and block new consumption from READY and DELAY queues, while letting in-flight RUN logic complete naturally and emit normal updates.

**Pros**: safe for in-flight work, consistent with existing lifecycle behavior, low blast radius.
**Cons**: outputs produced by in-flight RUN steps during STOP are not translated into new READY steps immediately and must be processed on resume.

### Option C: Queue-level pause flag without workflow status change

Keep workflow RUN but toggle consumers off.

**Pros**: avoids status transition changes.
**Cons**: less explicit domain state, harder observability, weaker contract across jobs/services.

## 4. Decision

We chose **Option B**.

### On STOP (pause)

1. Set `workflow_status = STOP`.
2. Set `workflow_pause_at = now()`.
3. Block:
   - consumption/execution pickup of READY steps,
   - consumption of DELAY steps,
   - creation of new READY steps through workflow progress evaluation.
4. Do **not** block:
   - RUN step transition/update until their own END,
   - inject updates,
   - attack path updates.
5. For delay rows in that run, set `goal = null` when needed (defensive normalization).

### On RESUME

1. Compute `pause_delta_seconds = now() - workflow_pause_at`.
2. Increment `workflow_pause_second += pause_delta_seconds`.
3. Set `workflow_pause_at = null`.
4. Recalculate `goal` for each delay step in the current run execution context.
5. Re-enqueue existing READY steps.
6. Re-evaluate workflow progress to produce new READY steps from outputs accumulated during STOP.
7. Unblock READY/DELAY consumption so execution can continue.
8. After restoring RUN status and recalculating delay goals, workflow re-evaluation is the
   mechanism that creates new READY steps from outputs accumulated during STOP.

### Resume transaction boundaries

Steps 1–4 are committed on their own (`WorkflowResumeService.resumeWorkflowRunIsolated`,
`REQUIRES_NEW`, tenant scope carried by a `TxCtx`) **before** step 5 publishes anything.
READY events go to RabbitMQ immediately, not on commit: published inside the resume transaction,
an event consumed before RUN commits reads STOP and is dropped by the ready guard. The step then
stays READY forever, because its batch hash was committed with it and re-evaluation skips
already-committed hashes. Once the isolated commit is done, the caller reloads its copy of the run
and runs steps 5–6 in the enclosing transaction (`ExerciseService.changeExerciseStatus`), so the
"workflow ended at resume" result stays synchronous.

Pause and resume only act on the **current execution** of the simulation (its latest run, by
`workflow_created_at`) and only when it is in the expected status (RUN to pause, STOP to resume).
Reset deletes executions (ADR-009), but a database reset before that may still hold older runs.

### Time impact rule

Paused time contributes to workflow effective timing through `workflow_pause_second`, so timeout/delay calculations use active runtime + accumulated pause duration.

## 5. Consequences

### Positive

- Pausing is safe for already-running work.
- Consumption is cleanly gated during STOP.
- Resume behavior is deterministic with explicit paused-duration accounting.
- Operational visibility improves with `workflow_pause_at` and `workflow_pause_second`.

### Negative / trade-offs

- STOP does not mean full immobility: in-flight RUN updates still happen, but they do not create new READY work until resume.
- Resume needs recalculation logic for existing delay goals.
- Defensive updates may be needed for existing delay rows.
- Resume is split across two commits: if the enclosing transaction fails after the isolated one,
  the run is RUN while the simulation is still PAUSED.
- READY steps created by the resume re-evaluation are still published before the enclosing
  commit, like on the regular update path (update → re-evaluation). This publish-before-commit
  pattern is engine-wide and is not addressed here.

### Neutral

- Existing RUN step lifecycle semantics remain unchanged.
- Inject and attack-path update flows remain unchanged.
- This ADR defines behavior; API/UI wording and migration details are implementation concerns.
