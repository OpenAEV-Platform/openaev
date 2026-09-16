# ADR-006: Snapshot the workflow scope rules at simulation launch

|         |                                                        |
|---------|--------------------------------------------------------|
| Status  | Accepted                                               |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/5508 |

## 1. Context

A workflow scope (allowlist / denylist) is stored as a set of `WorkflowScopeRule` rows. Each
rule keeps a **reference**, not a label: `ruleValue` is an id (asset / asset group) plus a
`valueType`, or a raw value for `MANUAL` / `CSV` rules. When the scope is displayed, the label
is **resolved on the fly** from the referenced entity — on the backend nothing is resolved
(`WorkflowConfigurationMapper.toScopeRuleOutput` only copies `ruleValue`), and the frontend
(`ScopeRules.resolveLabel`) looks the id up in the current `endpointsMap` / `assetGroupsMap`.

This is correct for an editable template, but wrong for a **launched simulation**. A launched
simulation is a historical record: its scope should reflect what was targeted **at launch
time**. Today, if the referenced asset or group is later **renamed**, the scope shows the new
name; if it is **deleted**, the label cannot be resolved at all (the UI falls back to
`Loading...`). The "scope snapshot" therefore does not work: the launched simulation's scope
silently tracks the current state of the platform instead of the execution-time state.

We want a launched simulation to display an **immutable, execution-time view** of its scope,
and to signal to the user when the referenced targets have **changed** or **disappeared** since
launch.

The timing of that change matters and must be distinguished. A target modified **during** the
execution window (between launch and end) may have **altered the run's results**; the same
target modified **after** the run has ended is purely a **display divergence** with **no impact**
on what was measured. Collapsing both into a single "modified" signal is misleading — the two
carry a different meaning for the analyst. We therefore need **two frozen reference points**
(launch and end), not just launch, so the change can be attributed to the right window.

A second, related concern is snapshotting the **detection/prevention security platforms**
connected to the tenant at launch: these are the collectors that would update the simulation's
prevention/detection results, and they influence the attack-path outcome just as much as the
targeted assets. We now bring this into scope of the **same table** (`workflow_scope_rules`),
because a security platform is conceptually part of the *execution scope* ("who observes"
alongside "what is targeted"), it shares the exact same lifecycle (frozen at RUN, live in
draft, excluded from export, no backfill), and reusing the table lets us later express a
per-action allow/deny of security platforms with the existing `selectedMode` — without a new
model.

## 2. Decision drivers

- **Historical fidelity**: a launched simulation must show what it targeted at launch, immune
  to later renames/deletions.
- **Attribution of change to the right window**: a change **during** execution (may have
  altered results) must be told apart from a change **after** execution (cosmetic only).
- **Correctness of the change signal**: distinguishing "renamed" from "not loaded yet" must be
  reliable — the frontend map is not an authoritative source of existence.
- **Minimal queries / no N+1**: reuse the scope resolution already performed at launch and
  batch the current-state resolution at read time.
- **Smallest blast radius**: extend the existing `WorkflowScopeRule` / mapper path rather than
  introduce a parallel model.
- **Separation of stored vs computed**: never persist data that goes stale.

## 3. Considered options

### Option A: Store nothing, resolve labels live (status quo)

Keep resolving labels from the current entities on every display.

**Pros**: no schema change; zero write cost.
**Cons**: does not solve the problem — the scope of a launched simulation keeps tracking the
current platform state; deleted targets are unrenderable.

### Option B: Store a frozen label only

At launch, freeze just the display label (`snapshot_label`) per rule.

**Pros**: simple; immune to rename/delete for display.
**Cons**: cannot describe *what* changed (an asset group's composition, agent presence); no
basis for a rich change signal.

### Option C: Store a structured composition snapshot, compute the diff at read time

At launch, freeze a **structured JSON snapshot** per rule (label + resolved composition for a
group: its assets, each with agent **presence** — count + distinct executors, no agent ids).
The snapshot is **immutable**. The change **status** (`RESOLVED` / `MODIFIED` / `DELETED`) is
**never stored**; it is recomputed **at read time** by comparing the frozen snapshot against an
authoritative current resolution, and returned in the output DTO as an enum + structured detail.

**Pros**: historical fidelity + a reliable, rich change signal; the diff can never go stale
because it is recomputed on each read; reuses the launch-time scope resolution and the existing
`scopeService`.
**Cons**: a JSON column + a mapper that must become a bean to batch-resolve current state; the
launch path must freeze only at the RUN stage.

## 4. Decision

We chose **Option C**.

The scope snapshot is a pair of **stored, immutable photos** taken at launch **and at the
simulation's end**; the change status is a **computed, transient field recomputed at every
read** from those two frozen points and the current state. Persisting the status was rejected on
principle: it depends on the current platform state, which mutates continuously, so a stored
`MODIFIED` / `RESOLVED` would silently become wrong the moment a target is renamed back or
deleted, with no code path to refresh it.

Concretely:

### Storage (write, twice: at launch and at end)

We freeze **two immutable reference points** so a change can be attributed to the right window.
They live in **two separate columns** (not one `jsonb` holding `{ launch, end }`) so each is an
**independent one-time write**: the launch photo is written once and **never touched again**
(structural immutability), while the end photo is a later isolated write with clear `null`
semantics until the run ends. A single shared column would force a read-modify-write at end
time, rewriting — and risking clobbering — the immutable launch photo.

- **Launch snapshot** — `workflow_scope_rule_snapshot`, a new **nullable JSON column** on
  `workflow_scope_rules`. Written **only when the RUN workflow is created**, i.e. inside the
  `copyWorkflowTemplateToRun` path (`WorkflowService`). `copyScopeRules` is shared by three
  copies (`toRun`, `toScenario`, `toSimulation`) and `launchWorkflowScenario` copies in cascade
  (scenario TEMPLATE → simulation TEMPLATE → RUN); freezing must happen **only at the RUN
  step**, never at a TEMPLATE copy. `copyScopeRules` is therefore parametrized (e.g. a
  `freezeSnapshot` flag) so only the RUN copy resolves and stores the snapshot.
- **End snapshot** — `workflow_scope_rule_snapshot_end`, a second nullable JSON column with the
  **same shape**. Written **once**, from the **chaining side** when the workflow RUN reaches
  `END`/`STOP` (which is how a chaining simulation reaches `FINISHED` / `CANCELED`), by
  re-running the **same** launch-time resolution (`scopeService`) in `WorkflowService` where the
  run and its scope rules are already in hand. The simulation-status transition is deliberately
  **not** used as the trigger, to avoid missing engine-driven terminations. Until the run ends
  the column stays `null` (simulation still `RUNNING`).
- The composition is obtained by **reusing the existing resolution services** (`AssetService` /
  `AssetGroupService`, the same services `ScopeService` / `getValidAssets` / execution targeting
  are built on), orchestrated by `ScopeSnapshotService` - never a new ad-hoc resolver - so
  neither snapshot can diverge from what the simulation actually targeted.
- Snapshot shape (per rule), identical for both reference points:
  ```json
  {
    "label": "Prod servers",
    "assets": [
      { "name": "asset_A", "agentsCount": 2, "executors": ["openbas", "caldera"] },
      { "name": "asset_B", "agentsCount": 0, "executors": [] }
    ]
  }
  ```
  For an `ASSET` rule the `assets` array holds the single endpoint; for `MANUAL` / `CSV` rules
  the value **is** the label, so `label` is the raw value and there is no composition.
- The column is **schemaless `jsonb`**: its shape is **polymorphic by `ruleSource`** and the
  mapper (de)serializes it accordingly. A `SECURITY_PLATFORM` rule stores the label plus a nested
  `security_platform` block (no `assets` composition):
  ```json
  { "label": "CrowdStrike Falcon", "security_platform": { "id": "b1e...-uuid", "type": "EDR", "updatedAt": "2026-08-05T10:00:00Z" } }
  ```
  Two things matter beyond the label:
  - the frozen `id` lets an **uninstall/reinstall** be detected as a new install (new id → old id
    resolves to `DELETED_*`) instead of a misleading `RESOLVED`;
  - the frozen `updatedAt` lets a **reconfiguration** (same id and label, but the collector's
    config changed — which changes what it detects/prevents, impacting results) be detected as
    `MODIFIED_*`; a later `updatedAt` therefore drives the status even when the name is unchanged.
  This divergence is intentional and not blocking — a single `jsonb` column holds every rule
  kind; a per-shape round-trip test guards (de)serialization.

### Diff (read, every display)

- **Read source.** A launched simulation's configuration is read from its **RUN** workflow (which
  carries the frozen snapshots and the `SECURITY_PLATFORM` rows); a **draft** simulation and a
  **scenario** are read from the **TEMPLATE** (no snapshot → live resolution, and the connected
  security platforms are resolved live by the frontend). `getWorkflowConfiguration` branches on the
  owning simulation's status (`SCHEDULED` → template, else → run). A launched simulation's scope is
  not editable anyway, so sourcing the run is consistent.

- `WorkflowConfigurationMapper` becomes a **Spring bean** injecting `ScopeSnapshotService`, so it
  can resolve the **authoritative current state** at read time. The implemented resolution is
  **per rule** (one lookup per rule through `AssetService` / `AssetGroupService`): per-workflow
  rule cardinality is small (a handful of user-defined entries), so the simpler per-rule path was
  kept; folding the lookups into one `findAllById` per kind remains an accepted follow-up
  optimization if configuration reads ever show up in profiling.
- The status is computed from **three points**: the frozen **launch** snapshot, the frozen
  **end** snapshot (or, while the simulation is still `RUNNING`, the live current state as the
  moving end reference), and the **current** live state. Two windows are distinguished:
  - **During execution** (`launch` ↔ `end`): the scope changed **while the run was active**, so
    it **may have altered the results**.
  - **After execution** (`end` ↔ `current`): the scope changed **after the run ended**, so it is
    a **display divergence with no impact** on what was measured.
- For each rule it computes a **status enum** `ScopeRuleSnapshotStatus` (during-execution takes
  precedence over after-execution, which takes precedence over resolved):
  - `MODIFIED_DURING_EXECUTION` / `DELETED_DURING_EXECUTION` — differs between launch and end
    (impacted the run; while `RUNNING`, computed against the live state as the in-progress end).
  - `MODIFIED_AFTER_EXECUTION` / `DELETED_AFTER_EXECUTION` — identical launch↔end but differs
    from the current state (cosmetic only).
  - `RESOLVED` — identical across all three points.
- The output carries, **per rule**, a single computed `status` (rule-level, composition- and
  agent-aware) plus the frozen composition at **launch** and at **end** (each: label + assets with
  agent count / executors) so the frontend can render the exact during- vs after-run delta — an
  asset / agent added or removed **during** (start↔end) vs **after** (end↔current) the run. The
  stored `jsonb` model is **never** exposed — dedicated output DTOs are used. Connected security
  platforms are returned in a **separate** top-level `workflow_security_platforms` list (they are
  not allow/deny rules). Because their change granularity is the whole platform (the `status`
  already tells during vs after), each entry is a **single minimal current-effective photo** — the
  **end** snapshot once the run is over, the **launch** snapshot while still running — not a
  start/end pair:
  ```json
  {
    "workflow_scope_rules": [
      {
        "workflow_scope_rule_status": "MODIFIED_DURING_EXECUTION",
        "workflow_scope_rule_snapshot_start_label": "Prod servers",
        "workflow_scope_rule_snapshot_start_assets": [
          { "asset_snapshot_id": "a1", "asset_snapshot_name": "asset_A",
            "asset_snapshot_agents_count": 2, "asset_snapshot_executors": ["openbas"] }
        ],
        "workflow_scope_rule_snapshot_end_assets": [
          { "asset_snapshot_id": "a1", "asset_snapshot_name": "asset_A",
            "asset_snapshot_agents_count": 1, "asset_snapshot_executors": ["openbas"] }
        ]
      }
    ],
    "workflow_security_platforms": [
      { "security_platform_snapshot_id": "sp1", "security_platform_snapshot_name": "CrowdStrike",
        "security_platform_snapshot_type": "EDR",
        "security_platform_snapshot_updated_at": "2026-08-05T10:00:00Z",
        "security_platform_snapshot_status": "RESOLVED" }
    ]
  }
  ```

### Indexing (no new index)

- The read path is `findAllByWorkflowId`, already served by the existing
  `idx_workflow_scope_rules_workflow_id` (migration `V4_80`). The read-time current-state
  resolution uses `findAllById` on assets / groups (**primary keys, already indexed**).
- **Asset-group composition needs no new index.** Static membership is resolved through the
  `asset_groups_assets` join table, which already carries `idx_asset_groups_assets_asset_group`
  and `idx_asset_groups_assets_asset` (since `V2_68`). Dynamic membership is **filter-based**
  (`computeFilterGroupJpa`), not a key join, so it is not addressable by a column index — it
  reuses the exact same (potentially expensive) resolution already run at launch / targeting,
  never a new one. The composition is resolved for **display only**, not to compute the status
  (name-level), so it is not on the hot status path.
- **No index on the `jsonb` snapshot columns**: their content is never queried (`WHERE` /
  filtering), only read and serialized, so a `GIN` index would add write cost for no read gain.
- The `SECURITY_PLATFORM` isolation filter is applied **in memory** after loading a workflow's
  rules (small per-workflow cardinality), so no composite `(workflow_id, source)` index is
  warranted. Net DB change is therefore **two nullable columns only**, no new index.

### Translation (frontend only)

- The backend never returns translated text (codebase convention: outputs carry stable
  machine-readable codes; there is no `MessageSource` on the API). It returns the `status` enum
  and structured detail (numbers, names) only.
- The frontend translates the enum and composes the detail messages with `t()` and
  interpolation. All new strings are added to the **9 language catalogs**
  (`en, fr, es, it, de, ru, ko, ja, zh`).

### Fallback (workaround, explicit)

- Simulations launched before this change have no snapshot. Their rules fall back to the
  existing **live resolution / id** display. This path is marked explicitly as a workaround in
  the code, because the execution-time state of a past run is unrecoverable (no backfill).

### Security platforms (reuse the same table)

The connected detection/prevention security platforms are stored **in the same
`workflow_scope_rules` table**, at the **workflow (RUN) level** — one row per platform, sibling
to the asset rules under the same `workflow_id`, **never duplicated per asset rule**.

- **New `ScopeRuleSource.SECURITY_PLATFORM`** value: `ruleValue` holds the security-platform id,
  and the same `workflow_scope_rule_snapshot` JSON column freezes `{ id, label, type, updatedAt }`
  at launch. The frozen `id` detects an uninstall/reinstall (new install → `DELETED_*`) and the
  frozen `updatedAt` detects a **reconfiguration** (config change at same id/label → `MODIFIED_*`),
  both instead of a spurious `RESOLVED`.
- **`selectedMode` is `null` for now** (a security platform is informative, not an
  allow/deny target). It is deliberately kept nullable so a future per-action allow/deny of
  security platforms can reuse the existing `selectedMode` without a schema change — the main
  reason for reusing this table rather than a dedicated model.
- **Draft = live, RUN = frozen** (symmetric with the asset snapshot): while the simulation is in
  draft nothing is written and the frontend resolves the **current tenant** security platforms
  live; the rows are written **only at the RUN copy** (`copyScopeRules(..., freezeSnapshot)`).
- **Composition source = tenant.** At launch we snapshot **all connected security platforms of
  the tenant**. The finer **payload-filtered** set (only the platform *types* the simulation's
  payloads actually expect) is a documented future refinement: it is blocked for chaining
  because steps/injects are generated at runtime, so the full payload set — and therefore the
  expected platform types — is **not known at launch**. Tenant-level is chosen for correctness
  and determinism at launch.
- **Isolation from execution targeting (critical).** `SECURITY_PLATFORM` rows use a distinct
  `ruleSource` (and a dedicated `valueType`), so `ScopeService` / `getValidAssets` / execution
  targeting — which filter by asset `valueType` and `selectedMode` — **never** pick them up.
  This is guarded by a test. **Note:** `SecurityPlatform extends Asset`, so a security platform
  *is* an `Asset` subtype; the distinct `ruleSource` / `valueType` is what keeps it out of the
  asset targeting path — the isolation test is therefore mandatory, not optional.

### Backend implementation (reuse existing services, no new resolver)

- **Asset / asset-group resolution** — reuse **`ScopeService`** (which already injects
  `AssetService` + `AssetGroupService`), the same resolution as `getValidAssets`; both the
  launch/end freeze and the read-time current state go through it.
- **Freeze at launch / end** — reuse **`WorkflowService.copyScopeRules`** (add a `freezeSnapshot`
  flag) and the `END`/`STOP` path in `WorkflowService`; no new launch/termination hook is created.
- **Read-time diff** — **`WorkflowConfigurationMapper`** is promoted to a Spring bean injecting
  `ScopeService` + repositories; the status is computed there, nowhere else.
- **Tenant security platforms** — reuse **`SecurityPlatformRepository`** (already tenant-scoped by
  the tenant filter); no new listing service.

## 5. Consequences

### Positive

- A launched simulation shows an **immutable, execution-time** scope, immune to later
  rename/delete of the referenced targets.
- The change signal distinguishes **when** the change happened: `MODIFIED_DURING_EXECUTION` /
  `DELETED_DURING_EXECUTION` (may have altered the results) vs `MODIFIED_AFTER_EXECUTION` /
  `DELETED_AFTER_EXECUTION` (cosmetic, no impact) vs `RESOLVED`. It is computed against an
  authoritative backend resolution, not the frontend's partial store, and can **never go stale**
  since it is recomputed on every read from the two frozen points and the current state.
- Reuses the launch-time scope resolution and batches the read-time resolution: no N+1, minimal
  extra queries.

### Negative / trade-offs

- Schema change (**two** nullable JSON columns: launch + end snapshot) and a mapper that must
  become a bean with repository access.
- A **second write path at simulation end** (`FINISHED` / `CANCELED`, on workflow `END`/`STOP`)
  freezes the end snapshot; it must fire exactly once and reuse the same resolution as launch.
- The launch path gains a branch: `copyScopeRules` must freeze **only** at the RUN copy, not at
  the intermediate TEMPLATE copies — a subtlety that must be covered by tests.
- The status is **rule-level** but **composition- and agent-aware**: the diff signature folds the
  rule label together with its frozen asset set (each asset's id, name, agent count and executor
  set), so an asset added / removed / renamed *or* an agent added / removed inside the scope flips
  the rule status — never a misleading whole-rule `RESOLVED`. The frozen launch **and** end
  compositions are still returned on the output DTO for display, but the single `status` is
  computed there (never stored on the `jsonb` snapshot).

### Neutral

- **Not exported**: the snapshot is execution-specific and is **excluded** from workflow
  export/import (a template has no snapshot); a test guards that the snapshot column is never
  serialized into an export.
- **Forward-only**: past simulations are not backfilled; they use the live-resolution fallback.
- **Detection/prevention security platforms snapshot** is handled by the **same table** (see
  §4): tenant-connected platforms are frozen at the RUN copy as `SECURITY_PLATFORM` rows, live
  in draft, excluded from export. The **payload-filtered** variant (restricting to the platform
  types the simulation's payloads expect) remains a future refinement, blocked for chaining by
  the runtime generation of steps/injects.
- **Remediations applied on the collector are out of scope (future feature).** OAEV can
  *generate* remediations, but has **no reliable way to know whether they were actually applied**
  on the vendor collector. The frozen `updatedAt` therefore reflects only the config state as
  **seen by OAEV**, not the effective remediation state enforced on the platform. Detecting that
  a remediation was applied (and correlating it to a simulation's results) is a separate,
  genuinely out-of-scope concern noted here for a potential future feature.

### Tests to add

- Launch with an **empty allowlist**: a simulation cannot be launched with an empty allowlist,
  so nothing is stored — guard this case.
- Launch when **all referenced assets are deleted**: verify whether the simulation can be
  launched at all, and that surviving rules snapshot / degrade correctly.
- **Non-export**: the snapshot column is not present in an exported workflow.
- Snapshot is frozen **only at the RUN copy**, not at the intermediate TEMPLATE copies.
- **End snapshot frozen exactly once** when the simulation reaches `FINISHED` / `CANCELED`
  (workflow `END`/`STOP`), reusing the launch-time resolution; stays `null` while `RUNNING`.
- **Timing attribution** (the three cases):
  - *Case 1* — asset modified **during** execution (launch↔end differ) → `MODIFIED_DURING_EXECUTION`.
  - *Case 2* — asset unchanged during the run but modified **after** end (end↔current differ) →
    `MODIFIED_AFTER_EXECUTION`.
  - *Case 3* — no modification → `RESOLVED`.
  - Same three cases for deletion (`DELETED_DURING_EXECUTION` / `DELETED_AFTER_EXECUTION`).
  - *Running* simulation: change since launch surfaces as `*_DURING_EXECUTION` (live state used
    as the in-progress end reference).
  - *Precedence*: a during-execution change dominates a later after-execution change on the same
    rule.
- Status computation (name-level): `RESOLVED` (unchanged) and the `MANUAL` / `CSV`
  always-`RESOLVED` case.

#### Security platforms

- **Execution isolation (critical)**: `SECURITY_PLATFORM` rows are **never** returned by
  `ScopeService` / `getValidAssets` nor included in execution targeting.
- **Draft = live, RUN = frozen**: nothing is written while the simulation is draft; the
  `SECURITY_PLATFORM` rows are written **only at the RUN copy**, not at the intermediate TEMPLATE
  copies.
- **Zero connected platforms at launch**: nothing is stored; the frontend shows an empty set (no
  crash, no error).
- **Snapshot status**: a security platform renamed / deleted **during** vs **after** the run
  resolves to `*_DURING_EXECUTION` vs `*_AFTER_EXECUTION` like an asset rule (same two frozen
  reference points).
- **Reinstall vs reconfiguration**: a platform **uninstalled/reinstalled** (new id) resolves to
  `DELETED_*`; a platform **reconfigured** (same id/label, later `updatedAt`) resolves to
  `MODIFIED_*` — during vs after the run per the two frozen points.
- **Non-export**: `SECURITY_PLATFORM` rows are excluded from an exported workflow.
- **Polymorphic `jsonb` round-trip**: an asset-shape snapshot (`{ label, assets[] }`) and a
  security-platform-shape snapshot (`{ label, security_platform: { id, type, updatedAt } }`) both
  (de)serialize correctly from the single `jsonb` column, dispatched by `ruleSource`.

## 6. Open question — RUN accumulation across reset/relaunch

A simulation reuses the same `exercise_id` across launch → reset → relaunch cycles, and the reset
flow does **not** delete the RUN workflow rows (it only clears `workflow_states` and injects — see
`ExerciseService.changeStatus` reset branch and `WorkflowStateService.deleteAllBySimulationId`).
Consequently a single simulation can own **several** RUN rows over time.

For this reason the scope configuration of a launched simulation is read from the **latest** RUN
(`WorkflowRepository.findFirstBySimulation_IdAndStatusOrderByWorkflowCreatedAtDesc`, ordered by
`workflow_created_at`), never from an arbitrary `findFirst()` on an unordered list. The workflow
**template id** already uniquely identifies the simulation (`Workflow.simulation` is `@OneToOne`),
so no API/frontend change is needed — only the deterministic latest-run resolution.

This leaves a product/technical decision to make later (out of scope for this ADR):

- **Option A — clean up on reset**: delete (or archive) the previous RUN rows when a simulation is
  reset, so a simulation keeps at most one RUN. Simpler read model, but changes reset behaviour and
  drops execution history.
- **Option B — expose history**: keep all RUN rows and let the UI browse previous execution
  snapshots (a version selector on the scope / overview page). Richer audit trail, more UI work.

Until this is decided, reading the latest RUN is the safe, side-effect-free behaviour.
