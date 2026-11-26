# Hide the full attack path (inherited marking from a restricted asset) - brainstorm

> Branch: `issue/8555_hide_full_attack_path` - Follows #8555 block G ("Attack paths: removed from that PR, to
> be redone differently", see [issue-8555-chained-and-autonomous-clearance.md](issue-8555-chained-and-autonomous-clearance.md))
> Size target: M (probably M to L, see section 8)
>
> Legend: **Verified** = read in the code on this branch. **To verify** = deduced or not read yet, check before
> coding. **Decision** = taken, with the reason.

## 1. The idea

A RED asset discovers a value (a finding or an output). A later step uses that value on a GREEN asset. For a
GREEN reader, the whole branch from the RED asset onward is hidden in the attack path, **including the green
nodes**, because they only exist through RED data.

```
blue inputs -> C (RED) -> green -> blue -> B (RED) -> green      full path
                |_______________ hidden for a GREEN reader _____|
```

Two pieces: (1) a marking on the attack path rows, (2) the engine carrying a marking along the data flow
(marking on the chaining inputs and outputs).

**Scope of this PR: the attack path only.** The Findings tab, the `Finding` entity and the search index are out
of scope (section 7).

## 2. What the code does today

| Fact | Status |
|---|---|
| `attackpath_execution`, `attackpath_finding` have **no** `marking_ids`; the per-simulation queries in `AttackPathExecutionRepository` / `AttackPathFindingRepository` have **no join to `assets`**; nothing under `service/attackpath` references markings. A GREEN reader sees the whole attack path of RED assets | Verified |
| Rows are frozen snapshots, no foreign key to `assets`; the only cleanup is `deleteAllBySimulationId` (simulation reset / delete) | Verified for the services under `service/attackpath`; asset deletion code **to verify** |
| A RED asset is written to the attack path only when the **launcher** is cleared for it (`getAgentsAndAgentlessAssetsByInject` filters by clearance; block E reads the launcher for that) | Verified|
| `executionDetail` starts with `findByIdAndSimulationId` and returns `null` (404) when the row is not visible. The collector alerts (jsonb), the remediation snapshot, `command`, `terminal_output` and the rebuilt injector command line (live `Inject.content` JSON + traces) are all reached **only after that lookup** | Verified (`AttackPathGraphService.executionDetail`) |
| Finding nodes are read under an asset node by endpoint, often with only `EXISTS (... AttackPathExecutionFinding ...)`, **not** through the execution row | Verified (`AttackPathFindingRepository`) |
| `WorkflowStateEntry` = `entry_key`, `entry_value`, hashes. No provenance, no marking | Verified |
| `syncState(outputData, typeMappings, workflowRun)` is called from `InjectExecutionStep.processOutputAndStateSync`; it appends entries to the global state, then `propagateToLocalStates` copies the matching ones to the local state of the dependent steps | Verified |
| A chained step runs one inject per scoped asset (`expandTargetBatches`, block F) | Verified in the doc |
| Markings: a row is visible when **all** of its `marking_ids` are in the reader's expanded set; null or empty = unmarked = visible. The reader's set is the highest order per type, expanded downward | Verified (`Asset.markingIds` javadoc, task2 design) |

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| H1 | **Snapshot semantics (option A).** The inherited marking is computed and stored when the step runs, and never recomputed | Same choice as the frozen scope photos in #8555. Recomputing needs a live check (Java post-filter breaks the paged graph reads) or a re-stamp with downstream re-propagation and an index on the asset id columns |
| H2 | **The inherited marking of a step is the union of marking ids**: markings of the entries it consumed + markings of the asset it targets. It only grows along the path | A union of ids is read as "the highest" by the subset rule: a reader must hold every id. Hop-by-hop is better than "highest marking of the first asset": on GREEN -> RED -> GREEN, the first-asset rule would leave the last stage visible |
| H3 | **`marking_ids text[]` on `attackpath_execution` and `attackpath_finding`**, registered as marked tables | `attackpath_finding` cannot be skipped: its nodes are read without the execution row |
| H4 | **`marking_ids` on `workflow_state_entries`, but this table is NOT registered as a marked table** | Entries are engine-only (no read endpoint). Registering it would make the engine's own reads drop entries under a restricted scope |
| H5 | **The step's inherited marking is carried in the `step_data` JSON** (like `inject_id`), read by `onRun` and the finding copy from the `Step` they already receive | No new column on `steps` |
| H6 | **MARKING flag off: compute nothing, store nothing** | Same rule as D9 of #8555 |
| H7 | **No marking on the collector, remediation and `step_data` JSON** | They are reached only through the execution row (section 2) |

Rejected: computing the inherited marking at read time from the graph's kill-chain matching (`applyKillChain`,
`applyEventDependencies`). It matches by type, uses an `isBefore` time check and "mirrors the front", and it
cannot tell "no producer" from "producer hidden". A visibility rule must not rest on a heuristic.

## 4. Design

1. **Schema** (one migration, nullable columns, no default, no backfill): `marking_ids text[]` on
   `attackpath_execution`, `attackpath_finding`, `workflow_state_entries`. Rows written before this PR stay
   unmarked, so they stay visible (limit, section 6).
2. **Pure function** `union(entryMarkings..., assetMarkings...)`: unit-tested, no I/O. Optional compaction to
   the highest order per type.
3. **Engine**
   - `syncState`: stamp the producing step's inherited marking on the entries it appends; `propagateToLocalStates` copies
     the marking with the entry.
   - Step creation (`StepService`, where the input combination is chosen): inherited marking = union of the consumed
     entries + the target asset's markings, written into `step_data`.
4. **Writers**: `AttackPathExecutionIngestionService.onRun` stamps the step's inherited marking on its rows;
   `AttackPathFindingIngestionService.copy` and `copyOutputs` stamp it on the finding rows.
5. **Read side**: registering the two tables as marked tables is enough; the inspector filters every query.
   `executionDetail` returns 404 for a hidden row, which closes every JSON piece of section 2.

## 5. To verify before coding

- [ ] **Which entries a step consumed.** Where the input combination is built in `StepService`, and whether the
  consumed entries can be listed. If not: fallback = union over the whole local state of the step template (over-restrictive:
  any RED entry in the pool is inherited by every consumer). Say it in the PR if used.
- [ ] **`AttackPathFindingWriter.insertFindings`** is an upsert on a deterministic id
  (`AttackPathIds.findingRow(simulationId, type, field, value, endpointKey)`). The same finding reached by a RED
  and a GREEN path hits the conflict branch: it must **union** the marking, never overwrite it.
- [ ] **Writers under a restricted scope.** `onRun`, the verdict updates, the terminal view, the collector upsert
  and `deleteAllBySimulationId` run in `tenantTx.executeNew(TxCtx.forTenant(...))`. Check they run at system
  clearance, or a restricted user's reset leaves rows with an inherited marking behind (an update or delete that skips rows
  the writer cannot see leaves them stale). Test: reset by a GREEN user removes the rows that inherited a RED marking.
- [ ] **The primary-key lookup is filtered** (`findByIdAndSimulationId`): rely on the multi-tenancy doc, but test it.
- [ ] **Other readers** of the collector and remediation repositories (my grep hit its time limit).
- [ ] **Marking definition delete**: do the new columns follow the scrub of `assets.marking_ids`
  (`AllTablesWithMarkingIds`)? An unscrubbed dead id hides the row from everyone.
- [ ] **Asset deletion code**: does anything touch `attackpath_*` rows?
- [ ] Migration timestamp later than every migration on `main` (see `migration.instructions.md`).

## 6. Events and known limits

| Event | Result |
|---|---|
| Asset deleted | The path stays hidden: the marking is on the row |
| Asset marking lowered (RED -> GREEN) | Stays hidden (fail-closed, may surprise) |
| Asset marking **raised** after the run | **Stays visible** (hostname, IP, history). Accepted by H1 |
| Reader's clearance / group changed, reader deleted | Immediate: the reader's set is computed per request |
| Recipient (PERSON row) deleted | The row stays with its frozen email label; persons carry no marking |
| Launcher deleted | Rows unaffected; later steps run with zero clearance (#8555 doc, fail-closed) |
| Rows written before this PR | Unmarked, so visible |

Known limits, to state in the PR description (with a follow-up issue each):
- The inject on the green asset stays readable through the inject API and the Execution page (`inject_content`
  JSON, execution traces, expectations). "Action details" opens it.
- The Findings tab shows the findings discovered through a RED value (out of scope).
- `consumedFindingKeys` values come from the step template's conditions: authored, not discovered, so no inherited marking.

## 7. Out of scope

- Findings tab, `Finding` entity, `findings_assets`, `FindingRecordingService`, finding dedup across paths.
- Search index and dashboards (S4).
- Live re-check or re-stamp when an asset's marking changes (options B and C, follow-up).
- Admin / bypass (another PR, D10 of #8555).

## 8. Tests and slicing

Integration tests in the style of `ChainedRunDispatchParityTest` (real DB, real GREEN and RED users):

| Case | Expected |
|---|---|
| RED C -> value -> GREEN asset, GREEN reader | No row from C onward in the graph feed; `executionDetail` = 404; no finding node under the green asset |
| Same, RED reader (control) | Everything, including alerts, remediation, command |
| GREEN -> RED -> GREEN | The last stage is hidden (H2) |
| Same finding reached by a RED and a GREEN path | The marking is the union after the upsert |
| RED asset deleted, then GREEN reader | Still hidden |
| Reset by a GREEN user | Rows that inherited a RED marking are removed |
| MARKING off | Identical to today, no marking stored |
| Unit: union function | Order per type, empty, null |

Slices (each reviewable alone): (1) migration + columns + marked-table registration;
(2) union function; (3) engine propagation (entries, local states, `step_data`); (4) writers stamp the inherited marking;
(5) the integration tests above.

**Size**: the schema, the writers and the read side are small. The engine part (which entries a step consumed,
copy to local states, dedup and anti-replay hashes that must not merge entries with different inherited markings) is the
heavy one: M, close to L.

## 9. Open questions

1. Plain union, or compaction to the highest order per type?
2. If the consumed entries cannot be listed (section 5), is the over-restrictive fallback acceptable for this PR?
3. Rows written before the PR stay visible: acceptable, or does a backfill (inherited marking = markings of the live
   target asset) belong in the PR?
