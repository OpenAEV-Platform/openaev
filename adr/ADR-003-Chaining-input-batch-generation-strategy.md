# ADR-003: Chaining input batch generation strategy

|  |  |
| --- | --- |
| Status | Proposed |
| Related | Chaining input preparation flow in `ConditionService.prepareInputsForStepExecution(...)` |

## 1. Context

Chaining step execution needs input batches built from workflow state. Inputs can come from:

- local state (`LOCAL`)
- global state (`GLOBAL`)
- static mapper values (`DEFAULT`)

The previous behavior had two recurring issues:

- strict early failures could drop valid executions when data existed only in correlated tuples;
- fallback-only combinations could lose the original tuple relation, while correlated-only logic could reduce execution coverage.

We needed one deterministic strategy that preserves relation-first behavior, still executes best effort, and avoids duplicates.

## 2. Decision drivers

1. **Correctness of execution inputs**: required keys must be complete before execution
2. **Best-effort execution coverage**: do not miss valid runs when full tuples are unavailable
3. **Deterministic deduplication**: no repeated execution of same input combination
4. **Maintainability**: clear and testable flow in one place

## 3. Considered options

### Option A: fallback cartesian only

Build all batches from flat value pools.

**Pros**: simple implementation.
**Cons**: loses tuple relationship; can create semantically weak combinations.

### Option B: correlated only (strict)

Execute only from correlated tuples and fail when a required key is uncovered.

**Pros**: strong relation fidelity.
**Cons**: low coverage when tuples are partial; misses valid combinations that exist in mapped pools.

### Option C: correlated-first then fallback with dedup (chosen)

1. Build from correlated tuples first.
2. Complete uncovered keys from mapped pools (`LOCAL`/`GLOBAL`).
3. Run fallback cartesian after correlated pass.
4. Deduplicate by hash across persisted and in-call combinations.

**Pros**: relation-first behavior with best-effort coverage and deterministic dedup.
**Cons**: more complex generation flow than single-pass options.

## 4. Decision

We chose **Option C** because it best satisfies correctness and coverage together.

Concretely, `prepareInputsForStepExecution(...)` and helpers apply this sequence:

1. Parse local/global workflow states.
2. Prepare mapper metadata:
   - dynamic key pools,
   - key -> mapping type,
   - static `DEFAULT` values.
3. Correlated-first phase:
   - project tuple onto required keys,
   - resolve uncovered keys from correct mapped source.
4. Fallback phase:
   - generate cartesian from dynamic pools.
5. For each candidate combo:
   - enforce required-key completeness,
   - deduplicate by hash (`hashExecution` + pending hashes),
   - merge static values,
   - emit execution batch.
6. Persist hashes later (`commitHashes(...)`) only for batches that truly proceed.

## 5. Consequences

### Positive

- Better execution coverage without losing correlated-first behavior
- Invalid partial combos are blocked before execution
- Stable duplicate protection across runs and within the same call
- Predictable, testable pipeline

### Negative / trade-offs

- More logic branches (correlated projection, uncovered key completion, fallback pass)
- Requires good tests for edge cases (partial tuples, mixed LOCAL/GLOBAL, already executed hashes)

### Neutral

- No API contract change in execution batch shape
- Hash persistence timing remains caller-controlled (`commitHashes(...)`)
