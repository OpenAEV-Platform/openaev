# ADR-011: WorkflowState storage refactor — normalized rows replacing the JSONB blob

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
5. **No dead code/schema left behind** — every legacy artifact (code path, mapping, tests) must be removed, not just bypassed; the legacy column only survives one release as a safety net.

## 3. Considered options

### Option A: Keep JSONB, replace application-level merge with native `jsonb_set`/`||` SQL operators

Instead of `SELECT` → Gson deserialize → mutate → Gson serialize → `UPDATE`, do the merge in a single SQL statement (`UPDATE ... SET entries = entries || jsonb_build_object(...)`).

**Pros**: fixes driver 3 (atomicity) — the row lock taken by the `UPDATE` makes the merge atomic. No schema change, minimal code change.
**Cons**: does not address drivers 1 and 2. MVCC still creates a new row version on every update, and TOAST still re-stores the whole out-of-line value since Postgres does no incremental diff on TOASTed content: the O(N²) write volume and the bloat are unchanged. Reads still deserialize the whole document. Rejected as insufficient — it only closes the concurrency gap, not the cost problem this ADR exists to fix.

### Option B: Normalize state into rows, one row per entry, tuples reconstructed at read time (chosen)

Split `WorkflowStateEntries` into individual rows in a single table (`workflow_state_entries`): one row per input value, one row per correlated-tuple field (all fields of a tuple sharing the tuple's `correlation_hash`), one row per execution hash. Correlated tuples are reconstructed at read time by grouping rows on `correlation_hash`.

**Pros**: addresses all decision drivers — a new entry is a single-row `INSERT` whose cost does not depend on history size; reads target only the needed entry type and keys through indexes; deduplication and anti-replay are enforced by `UNIQUE` indexes with `ON CONFLICT DO NOTHING` (not application code); cascade deletion is native (`ON DELETE CASCADE`).
**Cons**: the state of in-flight runs must be converted at deployment (see §4.3); reading a correlated tuple means reading several rows instead of one JSON object (see §4.2 — validated by a dedicated load test).

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

-- One global state per run (the (execution, step_template) constraint does not apply to NULL step templates).
CREATE UNIQUE INDEX uq_workflow_state_global ON workflow_states(workflow_execution_id)
    WHERE workflow_step_template_id IS NULL;

-- Legacy JSONB column: no longer read nor written, dropped in the next release (§4.4).
ALTER TABLE workflow_states ALTER COLUMN workflow_state_entries DROP NOT NULL;
```

The table was created by `V6_20261005105200000`; the global-state uniqueness, the legacy column change and the conversion of in-flight states (§4.3) are done by `V6_20261005160000000`. The first migration also added a `workflows.storage_mode` dual-run routing flag, from an earlier version of this ADR planning a coexistence window; it was never used by any code and is dropped by the second one.

Notes justified by driver trade-offs:
- **One partial unique index per entry type**, because each type has its own identity: an input value is unique per key; an execution hash is unique per state; a correlated field is unique *within its tuple*. A single `UNIQUE (workflow_state_id, entry_type, entry_key, entry_value)` would be wrong for `CORRELATED` rows: `{IPv4=10.0.0.1, Port=80}` and `{IPv4=10.0.0.1, Port=443}` share the row `IPv4=10.0.0.1`, and `ON CONFLICT DO NOTHING` would silently drop it from the second tuple.
- **`md5(entry_value)` in the input and correlated unique indexes**: values are free text of unbounded length, and a raw value larger than the B-tree entry size limit (~2.7 kB) would make the insert fail. Execution hashes have a fixed length and are indexed raw.
- **`correlation_hash`**: MurmurHash3, 128-bit (`Hashing.murmur3_128()`, Guava), hex-encoded — the output format of the existing execution-hash function, so execution hashes and correlation hashes share one representation. Both live in `ChainingHashUtils`: `hashCombo()` (execution hashes, format unchanged, since committed hashes are compared with freshly computed ones) and `hashTuple()` (tuples: pairs sorted by key then value, so the hash does not depend on iteration order and a tuple with two fields of the same key keeps both; each key and value is length-prefixed rather than joined with separators, since values come from the outputs of targeted machines and a separator inside a value could otherwise make two different tuples share a hash). Non-cryptographic collision risk is accepted as debt (values come from internal engine outputs, not adversary-controlled input).
- **`correlation_type`** carries `Correlated.type` (`ContractOutputType.name()`), which the JSONB model stores per tuple.
- `idx_wse_lookup` serves key-based reads of every entry type and, through its leading `workflow_state_id` column, the foreign key (cascade deletes).
- **Every tuple lookup is scoped by `workflow_state_id`**, and there is deliberately no index on `correlation_hash` alone. The hash identifies the tuple *content*: the same tuple, hence the same hash, exists in the global state, in every local state it was propagated to, and in other runs (including other tenants' simulations). A lookup by hash alone would mix the rows of all these states; per-state lookups are served by the `uq_wse_correlated` prefix.
- `executionKeys` (currently a `@NotNull @NotEmpty Set<String>` field on `WorkflowStateEntries`) is **dropped entirely** — the anti-replay mechanism is carried by `hashExecution` and no production usage of `executionKeys` exists. No equivalent column or `entry_type` is introduced for it.
- **One global state per run** (`uq_workflow_state_global`): two concurrent first writes on a run could previously create two global states. States are now created with an atomic `INSERT ... ON CONFLICT DO NOTHING` followed by a lookup.
- Table names are plural, consistent with the rest of the schema (`workflow_states`, `workflows`). `WorkflowStateEntries` (the Java class) is kept, as the in-memory view of a state (§4.2).

### 4.2 Read and write patterns

`WorkflowStateStore` is the only component aware of the row layout. The services exchange `WorkflowStateEntries` objects with it: *deltas* to append on the write side, *views* restricted to the keys they need on the read side.

- **Writes never read the state.** A sync builds, in memory, the delta of the values and tuples accepted from the output (same validation, subnet expansion and all-or-nothing tuple semantics as before), then appends it with one `INSERT ... SELECT FROM unnest(...) ON CONFLICT DO NOTHING` per entry type, issued as native queries: JPA cannot express `ON CONFLICT`, and the rows are neither indexed, audited nor streamed, so bypassing the persistence context loses no side effect. Propagating values or tuples to a local state is an append to that local state, with the same cost.
- **Reads are restricted to the keys an evaluation reads.** Filter evaluation reads the input values of the keys of its conditions' leaves; mapper evaluation reads the input values of the mappers' source keys and the correlated tuples holding at least one of them. The view holds nothing else, while the combination logic of `ConditionService` (candidate selection, completion from the pools, cartesian fallback) is unchanged.
- **Correlated tuples** are read in two indexed queries: the hashes of the candidate tuples (`entry_type = 'CORRELATED' AND entry_key IN (:keys)`, served by `idx_wse_lookup`), then all the rows of these tuples (`correlation_hash = ANY(:hashes)`, a single array parameter, served by `uq_wse_correlated`), regrouped by `correlation_hash` in the application. A single query with an `IN (subquery)` was measured and rejected: once PostgreSQL switches to a *generic plan* for the prepared statement, it scans every correlated row of the state even when only a few tuples match (see §5).
- **`entry_type` is always filtered with a literal**, never a bind parameter, so that PostgreSQL can match the partial indexes on generic plans too.
- **Execution hashes** are read once per mapper evaluation (one indexed query on the step's local state) and checked in memory. They are never queried once per candidate combination, which would turn one read into hundreds of round trips.
- **Anti-replay** relies on the `uq_wse_hash` unique index, and is effective only because creating a READY step depends on the insert result: `StepService.createReadySteps()` commits the hashes of the batches that pass the rate limit with `INSERT ... ON CONFLICT DO NOTHING RETURNING entry_value`, then creates a READY step only for the hashes actually returned, in the same transaction. A concurrent evaluation of the same step that committed a hash first blocks the second insert on the unique index, which then returns nothing for it.
- **Re-arming a step** (`WorkflowStateService.clearExecutionHashes()`, used when an already-executed step is edited in place so that it re-fires) deletes the `HASH_EXECUTION` rows of the step's local state. The table is therefore append-mostly rather than strictly append-only.

### 4.3 Migration strategy: conversion at deployment

The JSONB state of in-flight runs is converted into rows by `V6_20261005160000000`, at application start, before the engine runs. From then on the engine only reads and writes the normalized store: there is a single implementation, no routing and no coexistence window. The migration, in a single transaction:

1. Deletes the states of runs that are neither `RUN` nor `STOP` (paused, hence resumable). States of ended runs are deleted at end of run (see below); the remaining ones predate that cleanup.
2. Converts every remaining JSONB document: one `INPUT` row per value, one `CORRELATED` row per tuple field (with the tuple's hash and type, computed exactly as at runtime), one `HASH_EXECUTION` row per committed hash. Duplicate global states of a run are merged into the oldest one, then deleted.
3. Creates `uq_workflow_state_global` (superseding the non-unique `idx_wf_state_global_lookup`), makes the JSONB column nullable and drops `workflows.storage_mode`.

A dual-run coexistence (new runs on the normalized store, in-flight runs finishing on JSONB) was considered first: it avoids converting data, but maintains two code paths for weeks and defers the removal of the legacy one to a later ticket. It was rejected: in-flight states are few (ended runs have no state left) and their conversion is mechanical, while two engines' worth of storage code is a lasting source of divergence.

**Rollback.** The JSONB column is kept one release as a *safety net*, to diagnose or repair a conversion bug from the original data — not as a rollback path: from the first sync after deployment, the JSONB of a run is stale. Rolling the application back to a version predating this refactor is therefore only safe when no simulation is running.

**Terminal-state cleanup.** Every path that ends or removes a run already deletes its states, and `ON DELETE CASCADE` extends the deletion to `workflow_state_entries`: `WorkflowEndService.manageWorkflowEnd()` → `endActiveWorkflow()` → `deleteWorkflowStatesBySimulationId()` for the `TIMEOUT`, `CANCELED`, `NO_MORE_PROGRESS` and `CANCELED_BY_SIMULATION_DELETION` end causes, and the `workflows` → `workflow_states` cascade for the simulation reset/deletion flows. That deletion is now a single bulk `DELETE` (previously a derived query loading every state, JSONB included, before deleting them one by one). Pausing a run (`STOP`) keeps its state, since it is needed on resume.

### 4.4 Next release: drop the legacy column

Once this release has been deployed and its conversion verified, a follow-up migration drops `workflow_states.workflow_state_entries`. Nothing else remains: the JSONB mapping, the Gson (de)serialization paths, the unreachable `WorkflowStateRepositoryCustom` / `WorkflowStateRepositoryCustomImpl` (`jsonb_set` prototype never wired into `WorkflowStateRepository`), the dead `WorkflowStateService.newOutput()` and the tests of the JSONB path are all removed by this refactor.

## 5. Consequences

### Positive

- Write cost per new entry (input value, correlated field, execution hash) becomes O(1), independent of the run's accumulated history — no full-document rewrite, no TOAST re-store, one small dead tuple at most per deleted hash.
- Anti-replay lookups read only the step's execution hashes through an index instead of deserializing the whole state.
- Correlated tuples are read through indexes, restricted to the tuples sharing a required key.
- Deduplication, anti-replay and cascade deletion are enforced natively by Postgres, removing the lost-update race of the application-level JSONB merge.
- A single storage implementation from deployment on: no dual code path, no routing flag.
- One global state per run, guaranteed by the database.

### Negative / trade-offs

- The state of in-flight runs is converted at deployment: the migration time grows with the number of in-flight states, and rolling the application back is only safe when no simulation is running (§4.3).
- A correlated tuple is read as several rows and regrouped in the application, instead of one JSON object.
- `executionKeys` is removed from the model with no replacement column — any code currently relying on it must be adapted or confirmed unaffected.
- The figures in §1 are estimates derived from the design targets and the verified code pattern, not measurements: the actual cost of the JSONB approach in production, and therefore the actual gain of this refactor, have not been measured.
- Load test on synthetic data at stress volume (5 to 6.3 million rows; states of 10,000 endpoints, i.e. 50,000 correlated tuples), on a developer laptop — the absolute timings are indicative, the plans and WAL volumes are the meaningful part:
  - **Writes do not depend on history**: appending 100 inputs costs the same WAL (~0.5 MB, ~505 records) into an empty state as into a state of 111,000 rows. The legacy path rewrote a ~5.4 MB document (518 kB stored) for *each* new entry: ~0.9 MB of WAL and ~100 ms per entry, before any Gson work.
  - **Reads go through the indexes, generic plans included** (bind parameters, literal `entry_type`): inputs by key ~2 ms for 11,000 values; execution hashes ~0.5 ms for 500 hashes; anti-replay commit of 100 hashes (50 already committed) ~1 ms.
  - **Candidate tuples**: ~2.4 ms when 20 tuples of 50,000 match, ~190 ms in the worst case where all 50,000 match (100,000 rows returned), versus ~290 ms in both cases for the rejected single-query form.
  - Not covered: production hardware, concurrency at the 50-simulation scale, and the end-to-end engine time (which also includes the out-of-scope cartesian combination cost).
- The cartesian combination cost is unchanged by this refactor (out of scope, see §1).
- Minor behavior changes, without functional effect: an identical tuple produced twice is stored once in the global state (duplicates were already merged when building execution batches); under contention, the rate-limit counter of an evaluation may count a batch whose hash was committed concurrently, delaying another batch by one cycle.

### Neutral

- No impact on scenario import/export — verified: the exported/imported scenario format does not carry `WorkflowState` content.
- No change to the chaining engine's external behavior (same anti-replay guarantees, same correlated-tuple selection and completion semantics) — only the storage layer changes.

## References

- PostgreSQL official docs — Routine Vacuuming (row versioning under MVCC): https://www.postgresql.org/docs/current/routine-vacuuming.html
- PostgreSQL official docs — TOAST: https://www.postgresql.org/docs/current/storage-toast.html
- PostgreSQL official docs — `INSERT ... ON CONFLICT`: https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
- PostgreSQL official docs — Partial indexes: https://www.postgresql.org/docs/current/indexes-partial.html
- Crunchy Data — "Postgres TOAST: The Greatest Thing Since Sliced Bread" (row updates re-toast entire values): https://www.crunchydata.com/blog/postgres-toast-the-greatest-thing-since-sliced-bread
