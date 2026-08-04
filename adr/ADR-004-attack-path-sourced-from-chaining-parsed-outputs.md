# ADR-004: Source the attack path from the chaining parsed outputs

## 1. Context

The attack-path map visualizes what a chained simulation did: which executions ran, what
they produced, and how one action fed the next. Today the snapshot backing this view is
populated from `Finding` entities: on every execution event,
`AttackPathFindingIngestionService.copyFindings` reads the inject's findings and copies them
into `attackpath_finding` / `attackpath_execution_finding`.

The chaining engine, however, does not orchestrate on findings. `WorkflowStateService`
advances steps from the parsed outputs (`IngestionResult.parsedByType()`), persisted in
`WorkflowState`, and never consults `Finding`. Findings are a side effect of the outputs,
produced for the product's finding list.

A capability lets an output element be marked `contract_output_element_is_finding = false`:
it still participates in the chaining (it can trigger events consumed by later steps) but is
intentionally not persisted as a `Finding`, to avoid polluting the finding list (gated by
`InjectorExecutionProcessingHandler.shouldDispatch`). Because the attack path reads findings,
these output-only values never appear on the map, even though they drive the chain — so a
causal edge can reference data that is never rendered.

## 2. Decision drivers

- Fidelity to the chaining model (the map must reflect what the engine actually did).
- No regression on existing findings (values, delta/versioning, masking, tenant isolation).
- Effort and risk (avoid re-implementing output parsing/extraction).
- Single source of truth (avoid two divergent extraction paths for the same outputs).

## 3. Considered options

### Option A: Hybrid — keep findings, add a second pass for `is_finding=false`

Leave the finding-based copy untouched and add a parallel extraction of the output-only
elements, writing them with an `is_finding=false` flag.

**Pros**: smallest diff; the working finding path is untouched.
**Cons**: the map stays a derived projection of a side effect; two code paths for the same
data; the model still does not match the engine.

### Option B: Output-based snapshot — feed the attack path from the parsed outputs

Replace the finding read in the copy with consumption of the parsed outputs (the same data
the engine uses), attributed to the frozen `AttackPathExecution` rows, with an `is_finding`
flag distinguishing a real finding from an output-only node. All parsed output values are
snapshotted; the flag drives the node's type/UI, not its visibility.

**Pros**: the snapshot reflects the engine's source of truth; output-only values appear
natively; a single extraction path; no re-parsing (the parsed output, its type mapping and
its field key are already available at the copy call site).
**Cons**: rewrites a currently working path; endpoint attribution must move from
`finding.getAssets()` to the execution rows.

### Option C: Full re-parse of the raw structured output

Re-implement parsing, value extraction and type mapping inside the attack-path ingestion.

Dropped: high effort and high risk of divergence from the engine, for no benefit over
Option B which reuses the already-parsed outputs.

## 4. Decision

We chose a **hybrid of Option A and Option B**: the existing finding-based copy is kept
unchanged for real findings (`is_finding=true`), and a new output-based pass adds the
output-only values (`is_finding=false`). Pure Option B (sourcing *every* row from the parsed
outputs) was rejected during implementation because a real finding carries precise per-value
asset attribution (`finding.getAssets()`) that the parsed outputs cannot reproduce — moving
findings onto execution-row attribution would regress multi-endpoint finding placement. The
hybrid reaches the goal (all outputs on the map, correctly flagged) with zero regression on
findings.

Concretely:
- The finding-based copy (`AttackPathFindingIngestionService.copyFindings`) is unchanged and
  keeps writing `is_finding=true` rows with their asset-precise attribution.
- A new `copyOutputs` pass consumes the run's parsed outputs — the execution traces available
  in `InjectExecutionStep`'s status-update method (`extractDataFromParsed` +
  `collectOutputElements`), passed into the ingestion — for the contract output elements
  flagged `contract_output_element_is_finding = false`, and writes `is_finding=false` rows.
  The persisted `WorkflowState` is NOT used by this feature; it only proves the parsed
  outputs exist durably. As a prerequisite, `buildTypeMappingsFromInject` now uses the
  non-filtered accessor (`InjectorContractContentUtils.getAllContractOutputs(outputParsers,
  false)`) so non-finding outputs are type-mapped and propagate in the chaining state.
- Each parsed output maps to the `attackpath_finding` shape `(type, field, value,
  endpointKey)`: `field` is the output element key (the same key `FindingUtils` writes for a
  real finding), `type` is the contract output type label, `value` is the value. One row is
  written per value.
- Output-only values are attributed to endpoints via the frozen `AttackPathExecution` rows
  (`targetKey` / `targetAssetId`), using the single-endpoint fallback (skip when the step
  targets several endpoints), since an output value carries no per-value asset.
- Each `attackpath_finding` row carries an `is_finding` flag taken from the contract output
  element's `contract_output_element_is_finding`. The flag drives the node's type and UI (an
  "Output only" node, finding drawer in degraded mode), not whether it is shown — all parsed
  output values are rendered.
- The node + link writes (`attackpath_finding` + `attackpath_execution_finding`), the
  deterministic id `AttackPathIds.findingRow(...)`, the `bump` / `publishChanged` versioning
  and the secret masking are preserved unchanged.

### Implementation notes

- **`is_finding` origin**: read from the contract output element's
  `contract_output_element_is_finding` flag (not inferred from the presence of a `Finding`),
  so it is independent of finding-persistence timing.
- **Endpoint attribution**: reuse today's `resolveTargets` fallback — attach to the step's
  endpoint only when the step has a single endpoint, otherwise skip (no per-value signal to
  pick one target among several).
- **Discovered (non-asset) targets**: attribute the value to `endpointKey = targetKey`
  (the raw target), as raw findings already do.
- **No conflicting `is_finding` on the same id**: the id
  `AttackPathIds.findingRow(simulationId, type, field, value, endpointKey)` does NOT include
  `is_finding`. There is still no ambiguous upsert because `is_finding` is a property of the
  contract output element and `field` is that element's key (`FindingUtils.createFinding`
  sets `finding.field = element.key()`), so `is_finding` is a function of `field`. Since
  `field` is part of the id, any two rows sharing an id share the same `field` and therefore
  the same `is_finding`. As a defensive measure for the unlikely cross-contract case (the same
  key declared with a different flag in another contract), the `ON CONFLICT` sets a
  deterministic value (`is_finding = true` wins, never downgrading a real finding).
- **No type reconciliation shortcut**: `is_finding` is orthogonal to type — an output-only
  value can be of any type, including composite ones. The existing type reconciliation /
  sub-field extraction (`AttackPathKeyMatcher`) MUST therefore be applied to BOTH
  `is_finding=true` and `is_finding=false` rows.
- **Secret masking**: applied to all rows regardless of `is_finding` (keyed by the linked
  credential), preserving the current behavior — including composite types.
- **Source & signature**: outputs come from the run's execution traces in
  `InjectExecutionStep`; `copyFindings` gains the parsed output as an argument. Only
  type-mapped outputs are snapshotted (a `NOT_CHAINABLE` / untyped output cannot form a
  `(type, field)` row and is skipped). The output-copy currently snapshots **primitive**
  parsed values only: a composite/tuple value (a JSON object, e.g. a `PORTSCAN` pair) has no
  single display value and is skipped by `recordAttackPathOutputs` rather than serialized.
- **Bounded id (conditional value hashing)**: ADR-004 lets arbitrarily long parsed outputs reach
  `attackpath_finding` (unlike short `Finding` values), and a raw long value made the id overflow
  its `varchar(255)` primary key (`PSQLException: value too long for type character varying(255)`).
  `AttackPathIds.findingRow(...)` keeps the legacy raw encoding whenever it fits the column — so
  every row copied before this change still resolves to its exact pre-upgrade id and a re-copy
  upserts onto it instead of duplicating it — and only when the raw encoding would overflow does it
  switch to a variant that hashes the whole `value` with SHA-256 (`CryptoHelper.hashWithSHA256`,
  never truncated), under the distinct `FINDING_ROW_H` kind prefix so the raw and hashed namespaces
  can never collide (no pre-existing row can carry an overflowing raw id). The same value always
  yields the same id, preserving the idempotent upsert; the real value is kept untouched in
  `attackpath_finding_value` (`text`) for display.
- **Uniqueness on the primary key (no separate natural-key index)**: because the id is a
  deterministic, injective encoding of the full natural key (`simulationId, type, field, value,
  endpointKey`, hashing the value only on overflow as above), the same finding always resolves to
  the same id, so the **primary key alone enforces natural-key uniqueness**. The copy therefore
  upserts via `ON CONFLICT (attackpath_finding_id)`, and the previous partial unique index
  `uq_ap_find_natural_key` is dropped. This also removes a latent failure: that index keyed on the
  raw `value` (`text`), which for a long output could exceed the Postgres btree tuple limit (~2704
  bytes) and fail the INSERT on the index. No value hash is stored or recomputed in SQL; the
  `value` column stays `text` and un-indexed for display.

## 5. Consequences

### Positive

- The map reflects the chaining engine's actual source of truth.
- Output-only values (`is_finding=false`) appear on the map without special-casing.
- One extraction path; the product finding list and the attack path cannot silently diverge.

### Negative / trade-offs

- A currently working path is rewritten; needs careful non-regression coverage on
  `is_finding=true` findings.
- Endpoint attribution logic moves to the execution rows and must be validated for the
  single-endpoint fallback and discovered (non-asset) targets.
- Long parsed outputs forced one schema-safety change vs short findings: the row id hashes the
  value (only when the raw encoding would overflow) so the `varchar(255)` PK stays bounded while
  pre-existing rows keep their legacy id. Since the id already encodes the full natural key, the
  redundant natural-key unique index (which keyed on the raw `value`, itself a btree-overflow
  risk) was dropped and the copy upserts on the PK. The stored `value` stays `text` for display and
  is never indexed.

### Neutral

- Read contracts, counters and delta/versioning semantics are unchanged.
- Forward-only: past simulations are not backfilled.





