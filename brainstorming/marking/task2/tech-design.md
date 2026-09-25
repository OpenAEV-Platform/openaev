# Task 2 — Assign Markings to Groups: Technical Design

**Notion**: Task 2 (591) — *Assign markings to users*, delivered as **group clearance**: users never hold a
marking directly, they inherit it through group membership, the same rule already used for roles and grants.

**User stories**: [`../user-stories.md`](../user-stories.md) — US0–US4.

**Depends on**: [Task 1 — Marking definitions](../task1/marking-definitions-create-manage-plan.md) (already
merged, PR #7651).

**Consumed by**: [Task 3 — Assign markings to assets](../task3/tech-design.md) — the clearance this task
produces is the input the asset-visibility check reads; this task defines *what a user is cleared to see*,
never *what a specific asset shows*.

**Mechanism reference**: [`../task3/tech-design-option-c.md`](../task3/tech-design-option-c.md) — the
generic SQL-rewrite (`ScopeDimension` / `ScopeStatementInspector`) that Task 3 uses to enforce the clearance
this task computes. Task 2 itself does not touch that mechanism: clearance resolution is pure Java, no SQL
rewrite involved (see §3 below).

---

## 1. Goal

Let an administrator **view, assign and remove** marking clearances on a group, so that every member of
that group inherits the corresponding visibility once Task 3 enforces it on assets. Task 2 also seeds
sensible defaults so the feature is useful out of the box for the platform's built-in groups, and finishes
the RBAC work Task 1 explicitly left stubbed.

**Feature flag**: like Task 1, the whole feature ships behind `MARKING` (default off). Task 2 does not
introduce a new flag — it reuses Task 1's, so the group-markings UI (`implementation-plan.md` step 2.6)
must gate on `isFeatureEnabled('MARKING')` exactly the way Task 1's `SecurityMenu`/`settings.config` already
do, and stay invisible while the flag is off.

Scope, from `user-stories.md`:

- **US0** — Finish the `ASSIGN_MARKING` capability chain descoped from Task 1 (see §5): assignment must be
  gated by its own RBAC subject, independent of a group's `WRITE` control.
- **US1** — View a group's currently assigned markings.
- **US2** — Assign a marking to a group.
- **US3** — Remove a marking from a group.
- **US4** — Default marking assignment for built-in groups: Administrators → `TLP:RED`, Managers →
  `TLP:AMBER`, Observers → `TLP:GREEN`.

Explicitly **not** in scope for Task 2 (see §7): the platform-group equivalent of the assignment endpoint,
marking-filtering `groups_markings` itself, and anything about *how* an asset's marking is checked against
the clearance produced here — that is Task 3.

## 2. Data model

### 2.1 `groups_markings` — a clearance grant, not a marking attachment

```sql
groups_markings(
  group_id   varchar REFERENCES groups(group_id)                ON DELETE CASCADE,
  marking_id varchar REFERENCES marking_definitions(marking_id)  ON DELETE CASCADE,
  PRIMARY KEY (group_id, marking_id)
)
```

| Relation | Question it answers | Role |
|---|---|---|
| `groups_markings(group_id, marking_id)` | *What can members of this group see?* | clearance **grant** — an input to authorization (this task) |
| an asset's `marking_ids` (Task 3) | *Who is allowed to see this asset?* | marking **attachment** — an output of authorization |

`groups_markings` stays a plain join table with real FKs and `ON DELETE CASCADE`: it is read only by the
Java resolver below (never by a SQL predicate on the hot path), so as authorization data it wants referential
integrity more than it wants to avoid a join.

### 2.2 Why `groups_markings` is not itself marking-filtered

It is the table a clearance is *derived from*. Filtering it on marking would make the resolver's own read
depend on a clearance that does not exist yet — a circular dependency that fails closed to "no clearance",
i.e. every marked row invisible to everyone. Leaving it unfiltered (behind ordinary tenant isolation only) is
a deliberate decision, revisited only if `groups_markings` itself ever needs to be marking-scoped (§5).

### 2.3 Platform groups are out of scope for now

`Group` implements `DualScopeBase`: a group is either **tenant-scoped** (`tenant_id` set — an ordinary group
that exists inside one tenant) or **platform-scoped** (`tenant_id IS NULL` — one group definition shared
across every tenant its members are attached to, the same "platform vs tenant" split already used by
`Role`). A platform group's *membership* is a single list of users, but those users can each belong to
different, unrelated tenants.

**Decision: marking assignment applies only to tenant-scoped groups.** Assigning a clearance to a platform
group would have no coherent meaning yet, for two independent reasons:

- **The thing being protected is tenant-scoped.** Every marked entity so far (assets in Task 3) lives inside
  exactly one tenant, and so does its `marking_ids` predicate. A clearance granted to a platform group would
  need to mean *the same clearance, in every tenant that group's members happen to be in* — a cross-tenant
  semantic nobody has asked for, and one this design does not need to invent to ship Task 2/3.
- **It would multiply the eviction problem for no benefit.** Because a platform group's members span
  tenants, granting or changing its clearance would require invalidating the resolved-clearance cache for
  every one of those users, **in every tenant they belong to** — not just the tenant the admin was looking
  at when they made the change. Building and testing that correctly buys nothing today, since nothing reads
  a platform group's clearance.

**What this means in practice**: the assign endpoint (§4) targets a group inside a specific tenant path
(`/api/tenants/{tenant}/groups/{group}/markings`); a platform group is simply not a valid target for it —
this is the same restriction already listed as out of scope in §7. `MarkingScopeResolver` and
`MarkingClearanceCacheManager` are written against **tenant-scoped group membership only**; cache eviction
(§3.3) only ever needs to consider the one tenant a write happened in, not "every tenant this user belongs
to" — the cross-tenant fan-out described in earlier drafts of this document does not apply while platform
groups stay out of scope, and should not be built ahead of an actual need.

## 3. Clearance resolution

### 3.1 Ordinality is collapsed in Java, not in SQL

A group's grants are **ordinal** (`TLP:RED` implies `TLP:AMBER` and below), but the read predicate Task 3
enforces is a flat **set containment** test (`row.marking_ids <@ my_clearance`). Collapsing the ordering is
this task's job, not the SQL layer's: `MarkingScopeResolver` takes, **per marking type**, the **highest
order held across all of a user's groups**, then expands it back into every marking id of that type at or
below it. What reaches the transaction scope is a flat set of ids — the SQL predicate downstream never has
to know about `order` or `type`.

> **This expansion happens in memory, once per resolution, never in `groups_markings`.** Assigning `TLP:RED`
> to a group (§4) writes exactly **one** row — `(group_id, TLP:RED)` — nothing about the lower levels is
> ever written. `MarkingScopeResolver` reads that one grant, sees its `order` is the group's highest for
> `TLP`, and only *then* expands `order 5` into the id list `{TLP:CLEAR, TLP:GREEN, TLP:AMBER,
> TLP:AMBER+STRICT, TLP:RED}` as a transient value held in the cache/scope channel. `groups_markings` stays
> exactly as small as the admin's explicit choices; the expansion is recomputed (or served from cache) on
> every resolution, not persisted.

- **Per type, independently.** Holding `TLP:RED` says nothing about `PAP`; a type the user was granted
  nothing on contributes nothing.
- **Resolved per (user, tenant, bypass) and cached** by `MarkingClearanceCacheManager`, 5-minute TTL —
  recomputing on every request would make every read pay for a join across groups → grants → definitions.
- **BYPASS resolves to the whole tenant's marking scale**, expanded into an explicit id list, so no wildcard
  ever enters the transaction-scope channel.

### 3.2 Components

| Artefact | Role |
|---|---|
| `MarkingCtx` (`openaev-model`) | sealed `None` / `Restricted` / `All`, shaped like `TxCtx`; `all()` throws on `toGuc()` — an unresolved intention must never reach the transaction scope |
| `MarkingScopeResolver` | pure function: groups → grants → highest order per type → expanded id set; mirrors `TenantScopeResolver` |
| `MarkingClearanceCacheManager` | `findClearance` + `evict` / `evictForUser` / `evictForUsers` / `evictAll` |

### 3.3 Cache invalidation is a correctness requirement

The downstream check is pure set containment against whatever is in scope — it never re-reads
`marking_definitions` or `groups_markings`. A **stale, larger** cached clearance therefore grants access
that current data no longer justifies. Every **reduction** of a clearance must evict:

- user removed from a group, group deleted → `evictForUser` / `evictForUsers`
- marking unassigned from a group (this task's own write path, §4) → targeted evict
- a marking definition's `order` is lowered, or the definition is archived/deleted → `evictAll()` (order and
  type are resolver *inputs*, not labels — raising or lowering one changes which grants cover which rows for
  **everyone** holding a grant of that type, not just one user)

Two corrections worth keeping in mind, found by checking rather than assuming:

- **Descoped, not solved**: `Group` implements `DualScopeBase`, so in principle a *platform* group
  (`tenant_id IS NULL`) could grant markings into every tenant its members belong to, which would make a
  single-tenant eviction leave the others stale — fail-open, the exact case eviction exists to prevent.
  Per §2.3, platform-group marking assignment is **out of scope for Task 1/2/3**, so eviction only ever needs
  to cover the one tenant a write happened in. `evictForUser(userId)` still evicts **both** bypass variants
  for that tenant so a stale larger entry cannot survive under the other key — that part is unrelated to
  the platform/tenant distinction and stays regardless.
- Asset marking updates (Task 3) need **no** eviction here: the asset's own marking set is read fresh on
  every query, only the *clearance* is cached. Evicting on an asset save would be a no-op that looks like
  protection.

## 4. Write path

`PUT /api/tenants/{tenant}/groups/{group}/markings` — replaces the whole set for a group; an empty list
revokes every clearance. The read side stays raw JDBC (no OSIV/Hikari constraint there); the write side goes
through `Group.markings` (`@ManyToMany`), which has no such constraint.

### 4.1 `MarkingEscalationValidator`

Guards against a user granting a group a clearance **higher** than their own, checked against the caller's
**resolved** clearance, not their raw grants: a user holding `TLP:AMBER` *may* grant `TLP:GREEN` to a group,
because they can already read every `GREEN` row themselves — granting it discloses nothing they could not
disclose another way. Forbidding it would be annoying rather than safer ("higher implies lower is allowed").

### 4.2 Tenant isolation is not the only guard on a cross-tenant assignment

The tenant-v2 statement inspector rewrites *queries* — it cannot filter a read that is never issued, and an
entity already in the persistence context is served from Hibernate's first-level cache. The independent
guarantee here is the escalation guard itself: a clearance is resolved per tenant, so nobody holds another
tenant's marking to begin with.

## 5. RBAC — finishing the `ASSIGN_MARKING` capability chain (US0)

Task 1 defined a `MARKING` capability group with **two independent chains** but only wired the first one:

```
Definitions: ACCESS_MARKING_DEFINITION -> MANAGE_MARKING_DEFINITION -> DELETE_MARKING_DEFINITION   (Task 1, wired)
Assignment:  ACCESS_MARKING_ASSIGNMENT -> ASSIGN_MARKING          -> DELETE_MARKING_ASSIGNMENT      (Task 1, defined but stubbed)
```

The assignment chain exists in the capability catalog but nothing checks it: the group-markings endpoint
(§4) currently authorizes on the group's own `WRITE` capability, which conflates "may edit this group at
all" with "may change what this group is cleared to see" — the latter is a more sensitive action and
deserves its own gate, independent of general group editing.

### 5.1 What changes

- `PUT /api/tenants/{tenant}/groups/{group}/markings` requires `ASSIGN_MARKING` (assign/remove a single
  marking) in addition to — not instead of — the existing group-scoped `WRITE` check: a caller must still be
  allowed to edit *this* group, and separately be allowed to touch *markings* at all.
- `DELETE_MARKING_ASSIGNMENT` gates the empty-list revoke path distinctly from a same-request re-assignment,
  mirroring the Definitions chain's own split between edit and delete.
- Parent-hierarchy cascade and BYPASS behavior follow the existing capability tree conventions (auto-enable
  cascades upward, BYPASS grants effective access regardless of the leaf capability) — no new mechanism, just
  wiring the chain Task 1 already registered.
- Frontend: parse the two new capability strings, render the Assignment sub-group in the role editor next to
  Task 1's Definitions sub-group (they already share the `MARKING` parent group in the tree API).

### 5.2 Why this could not stay stubbed

`MarkingEscalationValidator` (§4.1) is a **data** guard — it stops a caller from granting a clearance higher
than their own. It says nothing about **who may attempt an assignment in the first place**. Without
`ASSIGN_MARKING`, any user with group `WRITE` — a much more common capability, since it also covers renaming
a group or changing its role — can reshape what an entire group is cleared to see. That is a materially
different blast radius than the rest of group editing, which is exactly why Task 1 modeled it as an
independent chain instead of folding it into the Definitions one.

## 6. Default group assignment (US4)

Built-in groups are seeded with a default clearance so the feature is useful without manual setup:

| Built-in group | Default clearance |
|---|---|
| Administrators | `TLP:RED` |
| Managers | `TLP:AMBER` |
| Observers | `TLP:GREEN` |

Seeded once per tenant, alongside the Task 1 default TLP marking definitions, so a fresh tenant has a
working, sensible clearance hierarchy from creation.

## 7. Explicitly out of scope for Task 2

Deferred to go-live hardening, tracked in [`../task3/implementation-plan-option-c.md`](../task3/implementation-plan-option-c.md) (step 5):

- The platform-group equivalent of the assignment endpoint (see §2.3) — marking assignment only targets
  tenant-scoped groups for now; a platform group granting markings across every tenant its members belong to
  is a cross-tenant question this task deliberately does not answer, since nothing marked today needs it.
- Marking-filtering `groups_markings` itself (see §2.2).
- Anything about enforcing the clearance on a specific resource type (assets, credentials) — that is
  [Task 3](../task3/tech-design.md).

## 8. Testing

- `MarkingScopeResolver` — unit tests: highest-wins across groups, per-type independence, empty clearance,
  admin/BYPASS.
- `MarkingClearanceCacheManager` — eviction tests per §3.3.
- `TenantGroupMarkingsApiTest` — the assign/view/remove flows end-to-end, eviction mutation-checked.
- `MarkingEscalationValidatorTest` — incl. "higher implies lower is allowed".
- `ASSIGN_MARKING` / `DELETE_MARKING_ASSIGNMENT` — capability cascade and independence tests (mirroring
  Task 1's `MANAGE_MARKING_DEFINITION` chain tests), plus a 403 test proving group `WRITE` alone is no
  longer sufficient once the chain is wired.
- Isolation-test note: the clearance read is raw JDBC and joins the test transaction, but Hibernate has not
  flushed — a test must `entityManager.flush()` before asserting, or it measures the flush rather than the
  feature.
