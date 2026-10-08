# ADR-011: Marking side effects on related entities — fine granularity and derived tables

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8171 |
| Design docs | [`brainstorming/marking/task4/tech-design.md`](../brainstorming/marking/task4/tech-design.md) |
| Builds on | [ADR-009 — Marking-based access control](./ADR-009-Marking-based-access-control.md) |

## 1. Context

ADR-009 made a marking on an asset a **read filter**: the statement inspector rewrites every query on
`assets` so a user only sees the rows their clearance covers. It left derived data explicitly out of
scope: *"expectation rows and native aggregate queries can surface information about assets the viewer
cannot see."*

That gap is what this ADR closes. An asset is rarely read on its own: it is referenced by asset groups,
scenarios, simulations and atomic testings, and every run on it produces agents, execution traces,
expectations and findings. Two questions follow:

1. **What does a user see of, and do with, a parent** (asset group, scenario, simulation, atomic
   testing) that holds an asset they have no clearance for — including launching it?
2. **How are the rows derived from a marked asset filtered**, when they do not carry the marking
   themselves? Manual testing showed the gap is real: an admin's run on a `TLP:RED` asset left its
   findings, scores and agent output readable by a `TLP:GREEN` user, and a `TLP:GREEN` user editing an
   inject's targets silently dropped the `TLP:RED` target they could not see.

## 2. Decision drivers

In priority order:

1. **No leak through derived objects.** A restricted asset must not surface through its id, its
   existence, a finding, a score, an expectation or an agent's output.
2. **No privilege escalation through execution.** A user must never cause an effect on an asset they
   cannot see.
3. **Visibility must scale with markings.** More marked entities are coming (scenarios, secret
   references…); each one must hide what it restricts, not everything that reaches it.
4. **Correctness must not depend on application write paths.** A marking copied by application code is
   a marking someone will forget to copy.
5. **Stay on ADR-009's enforcement point, and keep it readable.** What gets filtered, and how, must be
   visible in code review.

## 3. Considered options

### Question 1 — parents holding a restricted asset

#### Option 1: Fine granularity

The parent stays visible to everyone who could see it before. Inside it, each restricted asset is
filtered out wherever it appears (targets, results, scores, findings). A launch runs in **partial
mode**: only the targets the launcher is cleared for are executed.

**Pros**: lower-clearance users keep working with shared scenarios, groups and results; each newly
marked entity hides only itself.
**Cons**: runs can be partial and must be explained to higher-clearance viewers; stored aggregates
need extra work; one missed read or dispatch path leaks inside a visible parent.

Within Option 1, running **every** target and only filtering the results was rejected: it is a
confused-deputy escalation (driver 2). Blocking the whole launch as soon as one target is restricted
was rejected too: no extra safety over a partial launch, and far more disruptive.

#### Option 2: Hide parents

Any parent holding at least one restricted asset is completely hidden from the user (lists, counts,
pickers, direct URL → 404), and so is everything its runs produced. Implemented either with a derived
marking set stored on every parent, or with read-time SQL functions walking down to the assets.

**Pros**: simple to reason about; runs are never partial; aggregates are correct as they are.
**Cons**: one restricted asset hides the whole parent, including the author's own work and past
simulations, transitively through broad dynamic groups; it gets worse with every new marked type
(driver 3). The stored variant needs propagation along the whole chain; the SQL-function variant does
not see dynamic asset groups.

### Question 2 — filtering rows derived from a marked asset

#### Variant A: Copy the marking onto each derived table

Give each derived table its own `marking_ids` column holding a copy of its asset's markings.

**Pros**: no engine change; a local, indexable test.
**Cons**: every write path must keep the copy in sync; a missed path leaves a row unmarked, i.e.
visible to everyone (driver 4); write amplification and backfill on re-marking.

#### Variant B: Filter derived tables through the asset

No column on the derived table: its predicate asks whether the asset row it comes from is visible.

**Pros**: the asset is the single source of truth; nothing to propagate, backfill or forget; re-marking
an asset is O(1).
**Cons**: correlated sub-selects at read time; the inspector carries more logic.

#### How derived tables are declared

- **A new `ScopeDimension`**: rejected — it would duplicate the active-table set, chain validation and
  recursion of `MarkingDimension`, to AND two predicates testing the same clearance.
- **A property** (`openaev.marking.linked-tables=findings_assets.asset_id>assets.asset_id,…`): built
  first, rejected — it makes a data-model fact a deployment setting, needs its own syntax and parser,
  and two properties that must agree.
- **Derived from foreign keys**: rejected — ambiguous (`injects_expectations` has three candidate
  columns), impossible for `findings` (no foreign key to `assets`), blind to tables without a
  constraint, and "follow every foreign key to `assets`" would also hide `injects` and `asset_groups`
  (Option 2).
- **An explicit registry in code**: chosen, below.

## 4. Decision

We choose **Option 1 (fine granularity) with Variant B (derived tables filtered through the asset)**,
because it is the only combination that hides exactly what is restricted (drivers 1 and 3), never
executes on a restricted asset (driver 2), and keeps the asset as the only place a marking is stored
(driver 4).

Concretely:

- **Partial launch, gated by the launcher's live clearance.** New columns `Exercise.launched_by`,
  `Scenario.scheduled_by`, `Inject.launched_by` / `Inject.scheduled_by` (atomic testing) capture the
  actor explicitly at launch, relaunch or recurrence configuration — never inferred from a "last
  edited" field. At dispatch, the clearance is recomputed from that actor's current groups through
  `MarkingClearanceCacheManager.findClearance(...)`, applied at all three asset-resolution paths (agent
  routing, expectation computation, external-push payload). A missing or deleted actor resolves to zero
  clearance: marked targets are skipped, never run.
- **No `marking_ids` on derived tables.** Only tables that carry markings have the column (`assets`).
- **Derived tables are a shape of `MarkedTable`, inside `MarkingDimension`.** A marked table is marked
  in exactly one way:

  | Shape | Example | Predicate |
  | --- | --- | --- |
  | Own column | `assets` | `is_marking_set_allowed(t.marking_ids)` |
  | Parent link (`linkedTo`) | `injects_expectations.asset_id → assets` | `(t.fk IS NULL OR EXISTS (SELECT 1 FROM parent p WHERE p.key = t.fk AND <parent predicate>))` |
  | Link rows (`throughLinkRows`) | `findings ← findings_assets` | `(NOT EXISTS (<links>) OR EXISTS (<links> AND <link predicate>))` |

  The predicate is built recursively, so a chain (trace → agent → asset) resolves hop by hop down to
  the one column that holds the marking.
- **Derived tables and their relationships are declared explicitly, in code**, in
  `MarkingDerivedTables`. Each entry names the columns of one hop, because the predicate is generated
  SQL:

  ```java
  // Links from a parent to an asset: editing them must keep the hidden ones
  LINKS_TO_ASSETS = List.of(
      linkedTo("injects_assets", "asset_id", "assets", "asset_id"),
      linkedTo("asset_groups_assets", "asset_id", "assets", "asset_id"));

  // What a run produces on an asset
  FROM_ASSETS = List.of(
      linkedTo("findings_assets", "asset_id", "assets", "asset_id"),
      throughLinkRows("findings", "finding_id", "findings_assets", "finding_id"),
      linkedTo("injects_expectations", "asset_id", "assets", "asset_id"),
      linkedTo("agents", "agent_asset", "assets", "asset_id"),
      linkedTo("execution_traces", "execution_agent_id", "agents", "agent_id"));
  ```

- **Activation follows the marked table.** `openaev.marking.active-tables` lists only tables with a
  `marking_ids` column. Activating `assets` filters every table derived from it; listing a derived table
  there fails the startup. The registry is checked against the schema by a test.
- **Findings use a permissive rule**: visible when attached to no asset or to at least one visible
  asset. The strict rule (hidden as soon as one asset is hidden) is a one-branch change, pending PO
  confirmation.

## 5. Consequences

### Positive

- Findings, expectations (and therefore read-time scores), agents and execution traces of a restricted
  asset no longer exist for a lower-clearance user — through the UI, the API and raw entity responses.
- A lower-clearance user editing an inject's targets or a static group's members keeps the restricted
  one they cannot see: Hibernate's `DELETE … WHERE inject_id = ?` collection rewrite is narrowed to
  visible links.
- Re-marking an asset changes, instantly, everything derived from it. Nothing is copied, so nothing can
  go stale.
- Adding a derived table is one reviewed line of code, not a migration or a deployment setting.

### Negative / trade-offs

- **Read cost.** One correlated sub-select per derived table and per row (two for findings and for
  execution traces). All join columns are indexed, but the unmarked fast path is lost: the sub-selects
  run even when nothing is marked, and for bypass or system clearance. Not yet measured on realistic
  volumes; the hot path is the eager load of an inject status's traces.
- **Partial runs must be explained** to higher-clearance viewers (who launched it, which targets were
  skipped). No UI yet.
- **Stored aggregates are not filtered.** The asset-group expectation row is rolled up from every
  member, restricted ones included, so its verdict (and the global score using it) can reflect a
  restricted asset. Needs a product decision.
- **Known gaps:** agentless execution traces naming an asset only through `execution_context_identifiers`
  (no foreign key); asset groups in the external-push payload (pending US1); the real-time stream (SSE)
  and Elasticsearch-served reads, which no SQL rewrite reaches.
- **Wider fail-closed surface.** Every statement on the derived tables is now parsed and rewritten; an
  unsupported SQL shape is refused. A transaction without a `TxCtx` has an empty clearance and no
  longer sees agents of marked assets, as it already did not see the assets.

### Neutral

- No REST contract change: endpoints are untouched, the filtering is below them.
- Agents always see themselves (`AGENT_RUNTIME_ACCESS` is a read bypass); background jobs and external
  executors run with system clearance.
- INSERTs are not rewritten: what may be written stays a service-layer guard, as in ADR-009.
- Option 2's read-time SQL-function mechanism remains the path if a container (scenario, simulation)
  must one day be hidden as a whole.

## 6. Further reading

- [`brainstorming/marking/task4/tech-design.md`](../brainstorming/marking/task4/tech-design.md) —
  the full reasoning: both options, launch variants, the three dispatch paths, Variant A / B, worked
  examples with the rewritten SQL, and performance.
- Code: `MarkingDerivedTables`, `MarkedTable`, `MarkedTables.withDerived`, `MarkingDimension`
  (`io.openaev.config`).
- Tests: `MarkingLinkedTablesTest`, `MarkingLinkedTablesRowsTest`, `InjectAssetsMarkingUpdateTest`.
