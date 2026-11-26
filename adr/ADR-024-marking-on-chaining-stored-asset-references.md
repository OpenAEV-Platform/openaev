# ADR-024: Marking on chaining's stored asset references

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/8555 |
| Design docs | [`brainstorming/marking/task4/issue-8555-chained-and-autonomous-clearance.md`](../brainstorming/marking/task4/issue-8555-chained-and-autonomous-clearance.md) |
| Builds on | [ADR-006 — Workflow scope rule execution snapshot](./ADR-006-workflow-scope-rule-execution-snapshot.md), [ADR-009 — Marking-based access control](./ADR-009-marking-based-access-control.md), [ADR-016 — Marking side effects on related entities](./ADR-016-marking-side-effects-on-related-entities.md) |

## 1. Context

ADR-009 hides the rows of a marked asset with the statement inspector, and ADR-016 extends that to the
tables linked to `assets` by a foreign key. Both leave out data that merely *copies* an asset reference.
Chaining keeps three such copies, which the inspector does not filter:

- `workflow_scope_rules`: `rule_value` holds an asset id, a group id, an IP or another value, depending
  on `rule_source`;
- the launch and end photos of those rules (JSON, ADR-006), which hold asset ids and names;
- the `inject_assets` ids in the step templates' JSON.

Without a decision, a `TLP:GREEN` user sees the name of a `TLP:RED` asset in a scope, and the UI shows
that asset as "deleted". If they save what they were shown, the `TLP:RED` rule is deleted for everyone.

A second question comes with it. The photos are frozen by whoever starts or ends a run (the launcher, the
timeout job, the queue consumer, the user who stops it). Under each caller's own clearance the launch and
end photos differ, and every group reads `MODIFIED_DURING_EXECUTION`. So code that serves an HTTP request
must sometimes work at system clearance. Two primitives already do that, and the rule for choosing one
was never written down.

## 2. Decision drivers

In priority order:

1. **No leak of a hidden asset** (id, name, photo).
2. **A restricted writer never removes or rewrites what they cannot see.**
3. **"Hidden" and "deleted" stay distinct**: a deleted asset still reads as deleted.
4. **Same behaviour as a classic simulation**: a hidden asset is absent, with no placeholder.
5. **No schema change for now.**

## 3. Considered options

### Question 1 — hiding the rules and their JSON copies

#### Option A: filter in Java when reading, protect when writing

`ScopeSnapshotService.hiddenAssetIds` resolves the visible ids at the user's clearance, then looks up only
the leftovers at system clearance: an asset that exists but is not visible is hidden, one that does not
exist is deleted. The mappers drop hidden rules and ids; `applyScopeRules`, the template realignment and
the reset leave hidden rules alone.

**Pros**: no migration; tells hidden from deleted.
**Cons**: a second mechanism next to the inspector; every new read or write of these tables must remember
to use it.

#### Option B: make `workflow_scope_rules` a derived table

A new nullable column `workflow_scope_rule_asset_id`, a foreign key to `assets` with `ON DELETE SET NULL`,
filled for `ASSET` rules only, so the rules are filtered like `injects_assets`.

**Pros**: the rules are filtered and protected by SQL, with no Java to remember.
**Cons**: needs a migration and a backfill; the JSON photos and the template ids still need Option A.
Pointing `linkedTo` at `rule_value` directly is not an option: every non-asset rule, and every rule on a
deleted asset, would disappear for everyone.

#### Option C: copy the marking onto the rule

Rejected: ADR-016 (Variant A) already rejected it, because the copy goes stale.

#### Option D: a placeholder (`RESTRICTED` status or chip)

Rejected: a hidden asset is absent from a classic simulation (driver 4).

#### Option E: do nothing

Rejected: the asset leaks and gets deleted by mistake (drivers 1 and 2).

### Question 2 — reaching system clearance during an HTTP request

#### `TenantScopedTransaction.executeNew`

A new transaction, with its own persistence context.

**Pros**: the caller's transaction and entities are untouched, nothing to restore.
**Cons**: it does not see the caller's uncommitted writes, and it borrows a second connection.

#### `TenantScopedTransaction.withSystemClearance` (through `FaithfulCopyTransaction`)

The request's own transaction, with the clearance widened for the work and given back afterwards.

**Pros**: it sees the request's uncommitted writes.
**Cons**: the persistence context is flushed and cleared on both sides, so the caller's entities are
detached and must be reloaded; callers are an allow-list (`FaithfulCopyCallersArchTest`).

Using only one of them was considered and dropped: `executeNew` cannot see what a copy has just written,
and `withSystemClearance` is a heavier tool than reading committed photos needs.

## 4. Decision

**Question 1: Option A now, Option B as the planned follow-up for the rules.** A meets drivers 1 to 5. B
would remove the special handling for the rules, but not for the JSON photos and the template ids, so it
adds to A and does not replace it.

**Question 2: keep both primitives, each with its own rule.**

- Use `executeNew` to resolve ids or photos from data that is already committed: the launch and end
  photos, `hiddenAssetIds`, `assetIdsInScope` (`ScopeSnapshotService.atSystemClearance`).
- Use `withSystemClearance`, through `FaithfulCopyTransaction`, when the work must see what the request
  has just written: the faithful copies, and the realignment of step templates after a scope save
  (`readIds`).
- In both cases only ids or photos come out. The response is always built afterwards, at the user's
  clearance. A third way into system clearance during a request is a new decision, not an extension of
  this one.

The engine itself still expands the scope at system clearance, and the dispatch gate of ADR-016 decides
what runs on each target.

## 5. Consequences

### Positive

- A `TLP:GREEN` user never sees a `TLP:RED` asset in a scope or in the step templates, and a restricted
  writer cannot delete or rewrite a rule they cannot see.
- Photos are frozen the same way for every caller, so there is no false `MODIFIED_DURING_EXECUTION`.
- The choice between the two primitives has a rule, and the Javadoc of both says which.

### Negative / trade-offs

- Two ways to hide an asset: SQL for the tables of ADR-016, Java for chaining's copies.
- Two ways into system clearance during a request.
- Up to one extra database connection per call to `hiddenAssetIds` (only when some asset is not visible
  to the reader), and one per photo freeze.

### Neutral

- No REST contract change and no new feature flag: the `MARKING` preview feature covers it, and with the
  flag off nothing is hidden.
- The admin / bypass case is left to another change.
