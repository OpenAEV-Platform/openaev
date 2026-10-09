# ADR-010: WorkflowState storage refactor — normalized rows replacing the JSONB blob

|  |                                                                               |
| --- |-------------------------------------------------------------------------------|
| Status | Accepted                                                                      |
| Related | [GitHub issue #6287](https://github.com/OpenAEV-Platform/openaev/issues/6287) |

## 1. Context

The chaining engine persists its per-run execution state (`WorkflowState.entries`) as a single JSONB blob per row: one row for the global state of a workflow run, one row per step-template local state. This blob holds three logical collections — primitive `inputs` (values discovered per key type), `correlated` tuples (multi-field objects like `{ip, port}` produced together by a single output), and `hashExecution` (the anti-replay set of already-fired input combinations).

Every write to this state — a newly discovered IP, a newly produced correlated tuple, a newly committed execution hash — follows the same pattern: `SELECT` the row, `gson.fromJson` the entire blob into a `WorkflowStateEntries` object, mutate it in memory, `gson.toJson` it back, `UPDATE` the whole row. Verified in `WorkflowStateService.syncState()` (global state), `propagateValuesToStep()` (local states), `ConditionService.commitHashes()` and `WorkflowStateService.clearExecutionHashes()` — all follow this read-modify-write cycle on the full blob, regardless of how small the actual change is.

The engine is designed against the following targets: **500 steps × 10 concurrent simulations** with **100 endpoints** in the allow-listed scope (commitment), and **2,000 steps × 50 concurrent simulations** with **10,000 endpoints** (stress). Against these targets, the current storage model has the following consequences.

> ⚠️ **All figures below are estimates**, extrapolated from these targets and from the verified code pattern. None of them comes from profiling or measurement on a real deployment; they size the order of magnitude of the problem, not its exact cost.

- **Write cost grows with history, not with event size.** PostgreSQL's MVCC engine never updates a row in place — every `UPDATE` writes a brand-new row version. Once the JSONB value exceeds ~2 kB it is TOASTed, and Postgres re-stores the *whole* TOASTed value whenever it changes (there is no incremental diff), so each new entry rewrites the entire document and its WAL. Cumulative write volume per run is therefore O(N²) in the number of produced entries. *Estimated*: a final global document of ~1 MB (commitment) to ~4 MB (stress), i.e. ~250 MB to ~4 GB rewritten per simulation, ~2.5 GB to ~200 GB across the parallel simulations.
- **Double write.** Correlated outputs are written to the global document first, then propagated into every matching local document, multiplying the cost above by the number of impacted local states. A port scan over thousands of endpoints can add hundreds of kB in a single sync.
- **Table bloat.** Each rewrite leaves a dead row version behind (*estimated* ~5,000 dead tuples on `workflow_states` at commitment scale, ~100,000 at stress scale), faster than autovacuum can reclaim them.
- **Silent lost updates.** `WorkflowState` has no `@Version` and no lock is taken: two concurrent syncs on the same global document both read it, both mutate it, and the last writer silently drops the other's data. The race window widens with the document size.
- **Read cost grows with history too.** Each step evaluation deserializes the whole global and local documents (`ConditionService.fetchWorkflowContext()`) before doing in-memory lookups, so its cost is proportional to the entire accumulated state — inputs, tuples and hashes — rather than to the data the evaluation actually needs.

These costs are architectural: they are inherent to storing structured, growing state as a single value in a single row, whatever library or SQL function (`jsonb_set`, `||`) is used to touch it. This ADR addresses this storage problem only. The cost of the cartesian combination logic (candidate combinations computed over all accumulated values) is a logic-level problem, explicitly out of scope and documented as residual debt; the normalized model gives it a better foundation for later. Other chaining engine topics (parsing, workflow scope rules, attack path resolution) are out of scope as well.

## 2. Decision drivers

1. **Write cost independent of accumulated history** — adding the Nth entry must not cost more than adding the 1st.
2. **Read cost bounded by what is read** — the anti-replay check and the correlated-tuple lookup must not require deserializing unrelated data.
3. **Concurrency safety** — no read-modify-write race window; duplicate detection must be guaranteed at the database level, not by application code that a race can outrun.
4. **Operational safety of the migration** — no simulation actively producing chaining state may be disrupted mid-flight; no state may be silently lost for a run that is actively relying on it (anti-replay is safety-critical: a lost hash can mean replaying a real exploit).
5. **No dead code/schema left behind** — once the transition window closes, every legacy artifact (column, code path, tests) must be removed, not just bypassed.

## 3. Considered options

### Option A: Keep JSONB, replace application-level merge with native `jsonb_set`/`||` SQL operators

Instead of `SELECT` → Gson deserialize → mutate → Gson serialize → `UPDATE`, do the merge in a single SQL statement (`UPDATE ... SET entries = entries || jsonb_build_object(...)`).

**Pros**: fixes driver 3 (atomicity) — the row lock taken by the `UPDATE` makes the merge atomic. No schema change, minimal code change.
**Cons**: does not address drivers 1 and 2. MVCC still creates a new row version on every update, and TOAST still re-stores the whole out-of-line value since Postgres does no incremental diff on TOASTed content: the O(N²) write volume and the bloat are unchanged. Reads still deserialize the whole document. Rejected as insufficient — it only closes the concurrency gap, not the cost problem this ADR exists to fix.

### Option B: Normalize state into rows, one row per entry, tuples reconstructed at read time (chosen)

Split `WorkflowStateEntries` into individual rows in a single table (`workflow_state_entries`): one row per input value, one row per correlated-tuple field (all fields of a tuple sharing the tuple's `correlation_hash`), one row per execution hash. Correlated tuples are reconstructed at read time by grouping rows on `correlation_hash`.

**Pros**: addresses all decision drivers — a new entry is a single-row `INSERT` whose cost does not depend on history size; reads target only the needed entry type and keys through indexes; deduplication and anti-replay are enforced by `UNIQUE` indexes with `ON CONFLICT DO NOTHING` (not application code); cascade deletion is native (`ON DELETE CASCADE`).
**Cons**: requires a routing layer during the coexistence window (see Decision); reading a correlated tuple means reading several rows instead of one JSON object (see §4.2 — validated by a dedicated load test).

### Option B': Option B plus a synthesis table tracking per-tuple key presence and completeness

Add a `workflow_state_correlation_progress` table recording, per tuple, which keys are present (`keys_present`) and whether the tuple is complete (`is_complete`), so that "ready" tuples could be read without grouping rows.

**Pros**: a single indexed read for "complete" tuples.
**Cons**: solves a problem the engine does not have, and would introduce a regression:
- Tuples are atomic: a tuple is written once, from a single output object, only if it has at least two fields, and is never extended afterwards (`WorkflowStateService.saveCorrelatedObject()`; a tuple with an invalid field is rejected as a whole). Key presence is fully known at insert time and never changes, so the table would only duplicate the `CORRELATED` rows — a second source of truth with an extra write per tuple.
- Completeness is not a property of a tuple: candidate selection keeps every tuple sharing *at least one* key required by the step, then completes the missing keys from the LOCAL/GLOBAL pools (`WorkflowStateEntries.findCandidateCorrelated()`, `ConditionService.buildExecutionBatches()`). Filtering on `is_complete` would drop these valid combinations — covered today by `ConditionServiceTest.given_localAndGlobalSubsetCorrelation_should_completeUncoveredKeyFromMappedPool`.

Rejected.

### Option C: Introduce an in-memory store (e.g. Redis) alongside Postgres for hot execution state

Considered as a thought exercise, not a real candidate for this project (no such component exists in the current stack).

**Pros**: removes MVCC/TOAST/index-maintenance overhead entirely for hot state (`SADD`/`SISMEMBER` map directly onto anti-replay checks).
**Cons**: the write problems stem from the monolithic document model (full read-modify-write, propagation), not from Postgres itself — the same blob in Redis would keep them. Weaker durability guarantees by default for a safety-critical anti-replay set (crash = replay risk), a second source of truth during execution, which is read-your-write, and a new infrastructure component (HA, backup, monitoring) — out of scope for a storage-layer refactor. Rejected; to be reconsidered only if a normalized Postgres store proves unable to meet the targets.

## 4. Decision

We chose **Option B**: it is the only option that satisfies drivers 1 and 2 while also strengthening driver 3 (native DB-level uniqueness and cascade), without the duplication and regression risk of Option B'.

### 4.1 Schema

```sql
CREATE TABLE workflow_state_entries (
    id                 BIGSERIAL PRIMARY KEY,
    workflow_state_id  VARCHAR(255) NOT NULL REFERENCES workflow_states(workflow_state_id) ON DELETE CASCADE,
    entry_type         VARCHAR(20) NOT NULL,   -- INPUT | CORRELATED | HASH_EXECUTION
    entry_key          VARCHAR(255) NOT NULL,  -- primitive type name, or correlated field key
    entry_value        TEXT NOT NULL,
    correlation_hash   VARCHAR(64),            -- CORRELATED only: MurmurHash3-128 (hex) of the tuple
    correlation_type   VARCHAR(255),           -- CORRELATED only: business type of the tuple
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_wse_entry_type CHECK (entry_type IN ('INPUT', 'CORRELATED', 'HASH_EXECUTION'))
);

CREATE INDEX idx_wse_lookup ON workflow_state_entries(workflow_state_id, entry_type, entry_key);

CREATE UNIQUE INDEX uq_wse_input ON workflow_state_entries(workflow_state_id, entry_key, md5(entry_value))
    WHERE entry_type = 'INPUT';
CREATE UNIQUE INDEX uq_wse_hash ON workflow_state_entries(workflow_state_id, entry_value)
    WHERE entry_type = 'HASH_EXECUTION';
CREATE UNIQUE INDEX uq_wse_correlated ON workflow_state_entries(workflow_state_id, correlation_hash, entry_key, md5(entry_value))
    WHERE entry_type = 'CORRELATED';

ALTER TABLE workflows ADD COLUMN storage_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY_JSONB';
COMMENT ON COLUMN workflows.storage_mode IS
   'TEMPORARY — dual-run routing flag for ADR-010 (LEGACY_JSONB / NORMALIZED). Set once at run
   creation in WorkflowService.copyWorkflowTemplateToRun(), never mutated afterwards. To be
   dropped entirely, along with all LEGACY_JSONB code paths, once the coexistence window closes
   (see ADR-010 §4.4 cleanup ticket).';
```

Notes justified by driver trade-offs:
- **One partial unique index per entry type**, because each type has its own identity: an input value is unique per key; an execution hash is unique per state; a correlated field is unique *within its tuple*. A single `UNIQUE (workflow_state_id, entry_type, entry_key, entry_value)` would be wrong for `CORRELATED` rows: `{IPv4=10.0.0.1, Port=80}` and `{IPv4=10.0.0.1, Port=443}` share the row `IPv4=10.0.0.1`, and `ON CONFLICT DO NOTHING` would silently drop it from the second tuple.
- **`md5(entry_value)` in the input and correlated unique indexes**: values are free text of unbounded length, and a raw value larger than the B-tree entry size limit (~2.7 kB) would make the insert fail. Execution hashes have a fixed length and are indexed raw.
- **`correlation_hash`**: MurmurHash3, 128-bit (`Hashing.murmur3_128()`, Guava), hex-encoded — the exact output format of the existing `WorkflowStateEntries.hashCombo()`, so no new hashing convention is introduced and execution hashes and correlation hashes share one representation. Non-cryptographic collision risk is accepted as debt (values come from internal engine outputs, not adversary-controlled input).
- **`correlation_type`** carries `Correlated.type` (`ContractOutputType.name()`), which the JSONB model stores per tuple.
- `idx_wse_lookup` serves key-based reads of every entry type and, through its leading `workflow_state_id` column, the foreign key (cascade deletes).
- **Every tuple lookup is scoped by `workflow_state_id`**, and there is deliberately no index on `correlation_hash` alone. The hash identifies the tuple *content*: the same tuple, hence the same hash, exists in the global state, in every local state it was propagated to, and in other runs (including other tenants' simulations). A lookup by hash alone would mix the rows of all these states; per-state lookups are served by the `uq_wse_correlated` prefix.
- `executionKeys` (currently a `@NotNull @NotEmpty Set<String>` field on `WorkflowStateEntries`) is **dropped entirely** — the anti-replay mechanism is carried by `hashExecution` and no production usage of `executionKeys` exists. No equivalent column or `entry_type` is introduced for it.
- Table names are plural, consistent with the rest of the schema (`workflow_states`, `workflows`), even though `WorkflowStateEntries` (the Java class) is not itself renamed by this ADR.

### 4.2 Read and write patterns (`NORMALIZED` runs)

- **Writes** are single-row `INSERT ... ON CONFLICT DO NOTHING` statements, issued as native queries: JPA cannot express `ON CONFLICT`, and the rows are neither indexed, audited nor streamed, so bypassing the persistence context loses no side effect. Propagating a value or a tuple from the global state to a local state is an insert into that local state, with the same O(1) cost.
- **Correlated tuples** are read in two indexed steps: the hashes of the candidate tuples (`entry_type = 'CORRELATED' AND entry_key = ANY(:requiredKeys)`, served by `idx_wse_lookup`), then the rows of these tuples (served by `uq_wse_correlated`), grouped by `correlation_hash` in the application. No completeness check is needed in SQL: candidate selection and completion from the pools stay in the engine, unchanged.
- **Execution hashes** are read once per step evaluation (one indexed query on the step's local state, as `ConditionService.getCommittedHashes()` does today) and checked in memory. They are never queried once per candidate combination, which would turn one read into hundreds of round trips.
- **Anti-replay** relies on the `uq_wse_hash` unique index, and is effective only if creating a READY step depends on the insert result: hashes are committed with `INSERT ... ON CONFLICT DO NOTHING RETURNING entry_value`, and READY steps are kept only for the hashes actually returned, in the same transaction. Otherwise two concurrent evaluations of the same step would both create the step.
- **Re-arming a step** (`WorkflowStateService.clearExecutionHashes()`, used when an already-executed step is edited in place so that it re-fires) deletes the `HASH_EXECUTION` rows of the step's local state. The table is therefore append-mostly rather than strictly append-only.

### 4.3 Dual-run coexistence (migration strategy)

No historical data migration is performed. Instead, both storage paths coexist for a transition window of **one week to one month**:

- `storage_mode` is set **once**, at run creation, in `WorkflowService.copyWorkflowTemplateToRun()` — the single private method that actually builds a `Workflow` with `status(WorkflowStatus.RUN)`, called only from `launchWorkflowSimulation()` — and never modified afterwards. `creationWorkflow()` and `startWorkflowBySimulationId()` are deliberately *not* used as the anchor point: they only build/manage `Workflow` entities in `TEMPLATE` status, with no associated `WorkflowState`.
- The mode given to new runs is read from a configuration property (`LEGACY_JSONB` or `NORMALIZED`). Switching it back to `LEGACY_JSONB` stops creating `NORMALIZED` runs without a redeployment, should the normalized path misbehave; runs keep the mode they were created with.
- Workflows existing at deployment time get `LEGACY_JSONB` (the column's default).
- `WorkflowStateService` becomes a router: every method branches on `workflowRun.getStorageMode()` to either the existing Gson/JSONB path or the new repository-backed path against `workflow_state_entries`.
- Rolling the application back to a version predating this refactor makes the state of in-flight `NORMALIZED` runs invisible to the old code; such a rollback must only happen when no `NORMALIZED` run is in progress.

This avoids any migration of in-flight execution state (driver 4) at the cost of maintaining two code paths for the duration of the coexistence window — an explicit, scoped, temporary complexity, not a permanent one.

**Terminal-state cleanup needs no new code.** Every path that ends or removes a run already deletes its `workflow_states` rows, and `ON DELETE CASCADE` extends the deletion to `workflow_state_entries`: `WorkflowEndService.manageWorkflowEnd()` → `endActiveWorkflow()` → `deleteWorkflowStatesBySimulationId()` for the `TIMEOUT`, `CANCELED`, `NO_MORE_PROGRESS` and `CANCELED_BY_SIMULATION_DELETION` end causes, and the simulation reset/deletion flows.

### 4.4 Post-coexistence cleanup (separate ticket, executed after the window closes)

Once the coexistence window has elapsed **and** the guard query below returns zero, a separate ticket (tracked, not executed as part of this refactor) removes, in a single pass:

```sql
SELECT COUNT(*) FROM workflows WHERE storage_mode = 'LEGACY_JSONB' AND status IN ('RUN', 'STOP');
-- must be 0 before proceeding (TEMPLATE workflows also carry the column default and END runs no longer have state)
```

- The JSONB column `workflow_state_entries` (mapped as `entries` in the Java entity) on `workflow_states`.
- The `storage_mode` column on `workflows` and the configuration property selecting it (dropped immediately with the rest — not kept as a historical trace).
- All `LEGACY_JSONB` code paths in `WorkflowStateService` (the Gson serialize/deserialize branch).
- The `entries` field on the `WorkflowState` entity.
- The unreachable `WorkflowStateRepositoryCustom` / `WorkflowStateRepositoryCustomImpl` (`jsonb_set` prototype never wired into `WorkflowStateRepository`).
- All tests exercising the legacy JSONB path specifically.

## 5. Consequences

### Positive

- Write cost per new entry (input value, correlated field, execution hash) becomes O(1), independent of the run's accumulated history — no full-document rewrite, no TOAST re-store, one small dead tuple at most per deleted hash.
- Anti-replay lookups read only the step's execution hashes through an index instead of deserializing the whole state.
- Correlated tuples are read through indexes, restricted to the tuples sharing a required key.
- Deduplication, anti-replay and cascade deletion are enforced natively by Postgres, removing the lost-update race of the application-level JSONB merge.
- No migration of historical data required; no risk to state of runs actively executing at deployment time.

### Negative / trade-offs

- Two storage code paths (`LEGACY_JSONB` / `NORMALIZED`) coexist in `WorkflowStateService` for 1 week to 1 month — real, temporary complexity, requiring discipline to keep both paths correct until cleanup.
- A correlated tuple is read as several rows and regrouped in the application, instead of one JSON object.
- `executionKeys` is removed from the model with no replacement column — any code currently relying on it must be adapted or confirmed unaffected.
- The figures in §1 are estimates derived from the design targets and the verified code pattern, not measurements: the actual cost of the JSONB approach in production, and therefore the actual gain of this refactor, have not been measured.
- The `EXPLAIN ANALYZE`-verified performance of the two-step correlated-tuple read (§4.2) at stress volume (10,000 endpoints) remains to be validated by a dedicated load test.
- The cartesian combination cost is unchanged by this refactor (out of scope, see §1).

### Neutral

- No impact on scenario import/export — verified: the exported/imported scenario format does not carry `WorkflowState` content.
- No change to the chaining engine's external behavior (same anti-replay guarantees, same correlated-tuple selection and completion semantics) — only the storage layer changes.

## References

- PostgreSQL official docs — Routine Vacuuming (row versioning under MVCC): https://www.postgresql.org/docs/current/routine-vacuuming.html
- PostgreSQL official docs — TOAST: https://www.postgresql.org/docs/current/storage-toast.html
- PostgreSQL official docs — `INSERT ... ON CONFLICT`: https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
- PostgreSQL official docs — Partial indexes: https://www.postgresql.org/docs/current/indexes-partial.html
- Crunchy Data — "Postgres TOAST: The Greatest Thing Since Sliced Bread" (row updates re-toast entire values): https://www.crunchydata.com/blog/postgres-toast-the-greatest-thing-since-sliced-bread
