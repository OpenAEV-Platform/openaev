# ADR-007: WorkflowState storage refactor — normalized append-only tables replacing the JSONB blob

|  |                                                                               |
| --- |-------------------------------------------------------------------------------|
| Status | Accepted                                                                      |
| Related | [GitHub issue #6287](https://github.com/OpenAEV-Platform/openaev/issues/6287) |

## 1. Context

The chaining engine persists its per-run execution state (`WorkflowState.entries`) as a single JSONB blob per row: one row for the global state of a workflow run, one row per step-template local state. This blob holds three logical collections — primitive `inputs` (values discovered per key type), `correlated` tuples (multi-field objects like `{ip, port}` waiting to be completed), and `hashExecution` (the anti-replay set of already-fired input combinations).

Every write to this state — a newly discovered IP, a newly completed correlated tuple, a newly committed execution hash — follows the same pattern in `WorkflowStateService`: `SELECT` the row, `gson.fromJson` the entire blob into a `WorkflowStateEntries` object, mutate it in memory, `gson.toJson` it back, `UPDATE` the whole row. Verified in `WorkflowStateService.syncState()`, `propagateValuesToStep()`, and `clearExecutionHashes()` — all three follow this exact read-modify-write cycle on the full blob, regardless of how small the actual change is.

This has two consequences we want to fix:

- **Write cost grows with history, not with event size.** PostgreSQL's MVCC engine never updates a row in place — every `UPDATE` writes a brand-new row version. When the updated column is indexed (a JSONB column needs a GIN index to be searchable at all), the row also loses eligibility for the HOT (Heap-Only Tuple) optimization that could otherwise skip the index rewrite (see `postgresql.org/docs/current/storage-hot.html`). Concretely: adding the 500th execution hash to a run's history costs as much I/O as rewriting the 499 already stored — a full-row, full-index rewrite, every single time.
- **Read cost has the same shape.** The anti-replay check (`hashExecution.contains(hash)`) and the correlated-tuple lookup (`findCandidateCorrelated()`) both require deserializing the entire blob before doing an in-memory lookup, even though the actual operation needed is a single-key check. This check runs once per candidate combination evaluated by the engine — potentially hundreds of times within one run — so the re-deserialization cost is paid repeatedly, and grows with the run's accumulated history.

Both costs are architectural, not a code style issue: they are inherent to storing structured, growing state as a single value in a single row, whatever library or SQL function (`jsonb_set`, `||`) is used to touch it. This ADR addresses this storage problem only. Other issues previously identified around the chaining engine (parsing, workflow scope rules, attack path resolution) are explicitly out of scope.

## 2. Decision drivers

1. **Write cost independent of accumulated history** — adding the Nth entry must not cost more than adding the 1st.
2. **Read cost independent of accumulated history** — the anti-replay check and the "which correlated tuples are ready" query must not require deserializing unrelated data.
3. **Concurrency safety** — no read-modify-write race window; duplicate detection must be guaranteed at the database level, not by application code that a race can outrun.
4. **Operational safety of the migration** — no simulation actively producing chaining state may be disrupted mid-flight; no state may be silently lost for a run that is actively relying on it (anti-replay is safety-critical: a lost hash can mean replaying a real exploit).
5. **No dead code/schema left behind** — once the transition window closes, every legacy artifact (column, code path, tests) must be removed, not just bypassed.

## 3. Considered options

### Option A: Keep JSONB, replace application-level merge with native `jsonb_set`/`||` SQL operators

Instead of `SELECT` → Gson deserialize → mutate → Gson serialize → `UPDATE`, do the merge in a single SQL statement (`UPDATE ... SET entries = entries || jsonb_build_object(...)`).

**Pros**: fixes driver 3 (atomicity) — Postgres's row-level MVCC lock makes the read-modify-write atomic. No schema change, minimal code change.
**Cons**: does not address drivers 1 and 2 at all. MVCC still creates a new row version containing the *entire* JSONB value on every update, regardless of which SQL construct produced it. A GIN-indexed JSONB column still loses HOT eligibility on every write; TOAST still re-stores the whole out-of-line value since Postgres does no incremental diff on TOASTed content. Rejected as insufficient — it only closes the concurrency gap, not the actual cost problem this ADR exists to fix.

### Option B: Normalize state into append-only tables, one row per entry (chosen)

Split `WorkflowStateEntries` into a table of individual, append-only rows (`workflow_state_entries`), plus a dedicated synthesis table (`workflow_state_correlation_progress`) that tracks, per correlation tuple, which keys are already present and whether the tuple is complete.

**Pros**: addresses all four decision drivers directly — write cost and read cost become independent of history size, uniqueness/anti-replay is enforced by a `UNIQUE` DB constraint with `ON CONFLICT DO NOTHING` (not application code), cascade deletion is native (`ON DELETE CASCADE`) instead of a full-blob rewrite.
**Cons**: larger schema surface (two new tables instead of one JSONB column), requires a routing layer during the coexistence window (see Decision), and introduces a second table (`workflow_state_correlation_progress`) whose `keys_present` is a controlled denormalization of data also derivable from `workflow_state_entries` — an explicit trade-off, justified below.

### Option B-read-only-variant: Normalize `workflow_state_entries` only, recompute tuple completeness at read time via SQL aggregation (`GROUP BY correlation_hash HAVING array_agg(...) @> required_keys`)

**Pros**: same write-side benefits as Option B, no extra table, no denormalization.
**Cons**: the single most frequent read the engine performs — "which correlated tuples are ready to launch a step" — stays O(n) on the number of partial tuples stored for that run, recomputed on every call. Under the assumption (confirmed for this project) that the volume of partial tuples can be very large, this read remains the dominant cost center even after the write-side fix. Rejected in favor of full Option B specifically because of this assumption; kept here as the documented alternative should the volume assumption later prove wrong.

### Option C: Introduce an in-memory store (e.g. Redis) alongside Postgres for hot execution state

Considered as a thought exercise, not a real candidate for this project (no such component exists in the current stack).

**Pros**: removes MVCC/TOAST/index-maintenance overhead entirely for hot state (`SADD`/`SISMEMBER`, `HSET`/`SINTERSTORE` map directly onto anti-replay and tuple-completeness checks).
**Cons**: weaker durability guarantees by default for a safety-critical anti-replay set (crash = replay risk), introduces a second source of truth to keep consistent with Postgres, and a new infrastructure component (HA, backup, monitoring) — entirely out of scope for a storage-layer refactor. Rejected without further analysis: no Redis in the stack, no case made for the operational cost.

## 4. Decision

We chose **Option B** because it is the only option that satisfies decision drivers 1 and 2 (write and read cost independent of accumulated history) while also strengthening driver 3 (native DB-level uniqueness and cascade) — under the explicit assumption, confirmed for this project, that the volume of partial correlated tuples per run can be large enough that recomputing completeness at read time (Option B-read-only-variant) would remain a bottleneck.

### 4.1 Schema

```sql
CREATE TABLE workflow_state_entries (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_state_id  VARCHAR NOT NULL REFERENCES workflow_states(workflow_state_id) ON DELETE CASCADE,
    entry_type         VARCHAR(20) NOT NULL,   -- INPUT | CORRELATED | HASH_EXECUTION
    entry_key          VARCHAR NOT NULL,       -- primitive type name, or correlated field key
    entry_value        TEXT NOT NULL,
    correlation_hash    BYTEA,                  -- non-null only for CORRELATED rows; MurmurHash3-128 of the tuple
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workflow_state_id, entry_type, entry_key, entry_value)
);

CREATE INDEX idx_wse_lookup ON workflow_state_entries(workflow_state_id, entry_type, entry_key);
CREATE INDEX idx_wse_correlation_hash ON workflow_state_entries(correlation_hash) WHERE correlation_hash IS NOT NULL;

CREATE TABLE workflow_state_correlation_progress (
    correlation_hash    BYTEA PRIMARY KEY,
    workflow_state_id   VARCHAR NOT NULL REFERENCES workflow_states(workflow_state_id) ON DELETE CASCADE,
    keys_present         TEXT[] NOT NULL DEFAULT '{}',
    is_complete           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_wscp_ready ON workflow_state_correlation_progress(workflow_state_id) WHERE is_complete = TRUE;

ALTER TABLE workflows ADD COLUMN storage_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY_JSONB';
COMMENT ON COLUMN workflows.storage_mode IS
   'TEMPORARY — dual-run routing flag for ADR-007 (LEGACY_JSONB / NORMALIZED). Set once at run
   creation in WorkflowService.copyWorkflowTemplateToRun(), never mutated afterwards. To be
   dropped entirely, along with all LEGACY_JSONB code paths, once the coexistence window closes
   (see cleanup ticket referenced in ADR-007). Do not build new logic depending on this
   field outside WorkflowStateService routing.';
```

Notes justified by driver trade-offs:
- `correlation_hash` (not `id`) is the semantic name for the tuple-identifying hash, to avoid confusion with a surrogate primary key — it *is* the primary key of `workflow_state_correlation_progress`, but a business-meaningful one.
- Hash algorithm: MurmurHash3, 128-bit (`Hashing.murmur3_128()`, Guava), reusing the exact pattern already implemented in `WorkflowStateEntries.hashCombo()` — no new hashing convention introduced. Non-cryptographic collision risk is accepted as debt, in the same family as previously accepted risks (see `hashCombo` usage today).
- `ON CONFLICT DO NOTHING` on the `UNIQUE` constraint is the anti-replay mechanism — enforced by Postgres itself, not by an application-level containment check.
- `executionKeys` (currently a `@NotNull @NotEmpty Set<String>` field on `WorkflowStateEntries`) is **dropped entirely** — no equivalent column or dedicated `entry_type` is introduced for it. It is not carried into the normalized model.
- Table names are plural, consistent with the rest of the schema (`workflow_states`, `workflows`), even though `WorkflowStateEntries` (the Java class) is not itself renamed by this ADR.

### 4.2 Dual-run coexistence (migration strategy)

No historical data migration is performed. Instead, both storage paths coexist for a transition window of **one week to one month**:

- `storage_mode` is set **once**, at run creation (`WorkflowService.creationWorkflow()` / `startWorkflowBySimulationId()`), and never modified afterwards.
- `copyWorkflowTemplateToRun()` — the single private method that actually builds a `Workflow` with `status(WorkflowStatus.RUN)`, called from both `launchWorkflowSimulation()` and `launchWorkflowScenario()`), and never modified afterwards. `creationWorkflow()` and `startWorkflowBySimulationId()` only build/manage `Workflow` entities in `TEMPLATE` status, with no associated `WorkflowState` — they are not the right anchor point for this flag.
- Workflows already in `RUN` status at deployment time are backfilled to `LEGACY_JSONB` (the column's default).
- Every workflow run created after deployment receives `NORMALIZED`.
- `WorkflowStateService` becomes a router: every method branches on `workflowRun.getStorageMode()` to either the existing Gson/JSONB path or the new repository-backed path against `workflow_state_entries` / `workflow_state_correlation_progress`.

This avoids any migration of in-flight execution state (driver 4) at the cost of maintaining two code paths for the duration of the coexistence window — an explicit, scoped, temporary complexity, not a permanent one.

### 4.3 Terminal-state cleanup

For `NORMALIZED` runs only, any terminal state reached by the workflow triggers deletion of the entire associated `WorkflowState` row (cascading to both new tables via `ON DELETE CASCADE`):

- `FINISHED` via normal completion (`WorkflowEndService.stopSimulationByEndWorkflow()`, reached through `markWorkflowEnded()` with cause `NO_MORE_PROGRESS`)
- `FINISHED` via timeout (`WorkflowEndService.forceCompleteWorkflowByTimeout()`)
- `CANCELED` (reached via `ExerciseService.changeExerciseStatus()` → `WorkflowService.cancelSimulationEndWorkflowRun()`)

These three methods currently have no common call site (verified in `WorkflowEndService.java`) — introducing a centralized hook, `WorkflowStateService.onWorkflowTerminated(Workflow run)`, called from all three, avoids future triplication and the risk that a later fourth termination path forgets to clean up. The deletion is a single `DELETE FROM workflow_states WHERE id = ?`, synchronous, in the same transaction as the terminal-state transition — no deferred job for this iteration.

### 4.4 Post-coexistence cleanup (separate ticket, executed after the window closes)

Once the coexistence window has elapsed **and** the guard query below returns zero, a separate ticket (tracked, not executed as part of this refactor) removes, in a single pass:

```sql
SELECT COUNT(*) FROM workflows WHERE storage_mode = 'LEGACY_JSONB' AND status = 'RUN';
-- must be 0 before proceeding
```

- The JSONB column `workflow_state_entries` (renamed `entries` in the Java entity) on `workflow_states`.
- The `storage_mode` column on `workflows` (dropped immediately with the rest — not kept as a historical trace).
- All `LEGACY_JSONB` code paths in `WorkflowStateService` (the Gson serialize/deserialize branch).
- The `entries` field on the `WorkflowState` entity.
- All tests exercising the legacy JSONB path specifically.

## 5. Consequences

### Positive

- Write cost per new entry (input value, correlated field, execution hash) becomes O(1), independent of the run's accumulated history — verified architecturally via HOT/TOAST/GIN behavior (see references below), not yet benchmarked in production.
- Anti-replay lookup becomes an indexed point lookup (`idx_wse_lookup`) instead of a full blob deserialization.
- "Which correlated tuples are ready" becomes a single indexed read on `workflow_state_correlation_progress` (`idx_wscp_ready`), independent of the number of partial tuples stored — the read the engine performs most frequently.
- Uniqueness (anti-replay) and cascade deletion are enforced natively by Postgres, removing an entire class of race-condition and dead-data bugs that were possible with the application-level JSONB merge.
- No migration of historical data required; no risk to state of runs actively executing at deployment time.

### Negative / trade-offs

- Two storage code paths (`LEGACY_JSONB` / `NORMALIZED`) coexist in `WorkflowStateService` for 1 week to 1 month — real, temporary complexity, requiring discipline to keep both paths correct until cleanup.
- `workflow_state_correlation_progress.keys_present` is a controlled denormalization of data already present in `workflow_state_entries` — an additional source of truth to keep consistent on every `CORRELATED` insert (incremental upsert via `ON CONFLICT ... DO UPDATE`).
- `executionKeys` is removed from the model with no replacement column — any code currently relying on it must be adapted or confirmed unaffected.
- The exact production cost of the current JSONB approach (deserialization time, TOAST overhead) has not been measured; the case for this refactor rests on documented PostgreSQL architecture (MVCC/HOT/TOAST/GIN behavior — see references) applied to the verified code pattern, not on profiling data.
- The `EXPLAIN ANALYZE`-verified performance of the two-step correlated-tuple reconstruction query, and of the `workflow_state_correlation_progress` upsert under heavy partial-tuple volume, remains to be validated by a dedicated load test.

### Neutral

- No impact on scenario import/export — verified: the exported/imported scenario format does not carry `WorkflowState` content.
- No change to the public API contract of the chaining engine's external behavior (same anti-replay guarantees, same correlated-tuple completion semantics) — only the storage layer changes.

## References

- PostgreSQL official docs — HOT (Heap-Only Tuples): https://www.postgresql.org/docs/current/storage-hot.html
- PostgreSQL official docs — Routine Vacuuming (row versioning under MVCC): https://www.postgresql.org/docs/current/routine-vacuuming.html
- PostgreSQL official docs — TOAST: https://www.postgresql.org/docs/current/storage-toast.html
- PostgreSQL official docs — GIN Indexes: https://www.postgresql.org/docs/current/gin.html
- Crunchy Data — "Postgres TOAST: The Greatest Thing Since Sliced Bread" (row updates re-toast entire values): https://www.crunchydata.com/blog/postgres-toast-the-greatest-thing-since-sliced-bread
- pganalyze — GIN index maintenance cost: https://pganalyze.com/blog/gin-index