# Marking — Assign to Groups (Task 2) Implementation Plan

**Design doc**: [`tech-design.md`](./tech-design.md) — the data model, clearance resolution and write path

**Depends on**: [Task 1 — Marking definitions](../task1/marking-definitions-create-manage-plan.md) (merged, PR #7651)

**Mechanism this task feeds**: [Task 3 — assign markings to assets](../task3/tech-design.md), which enforces
the clearance this task computes via the generic SQL rewrite documented in
[`../task3/tech-design-option-c.md`](../task3/tech-design-option-c.md)

**Status**: 2.1–2.4 delivered on this branch (`issue-7510/poc-task2`) — **backend/API only**, demoed via
[`../task3/demo/group-markings.sh`](../task3/demo/group-markings.sh) (plain `curl`, no UI); step 2.5
(`ASSIGN_MARKING` capability chain) newly added, not yet started; step 2.6 (group detail page UI) newly
added, not yet started

> Extracted from [`../task3/implementation-plan-option-c.md`](../task3/implementation-plan-option-c.md)
> steps 2.1–2.4 ("Step 2 — Scope channel"), which is where this work was originally planned and executed
> alongside the shared inspector mechanism (step 1). Step numbering below is kept for traceability back to
> that plan.

## 1) Goal

Deliver group marking clearance — view/assign/remove markings on a group, plus default clearances for the
built-in groups — as the input Task 3 needs to enforce visibility on assets, and finish the `ASSIGN_MARKING`
capability chain Task 1 left stubbed so assignment has its own RBAC gate.

> **This PoC is API-only.** Steps 2.1–2.4 deliver the backend (schema, resolver, cache, write endpoint) and
> prove it end-to-end with `curl` (`../task3/demo/group-markings.sh`) and integration tests — there is
> **no group detail page UI yet**. US1/US2/US3's acceptance criteria are written against a UI ("Given I am
> on the group detail page…"), so those user stories are only **partially** satisfied today: the capability
> exists as an API, not as something an admin can click through. Building that UI is **§4's Step 2.6**,
> explicitly not done.

## 2) Confirmed decisions and constraints

- A user never holds a marking directly; clearance is always derived from group membership (§2, `tech-design.md`).
- `groups_markings` stays a join table with real FKs — it is authorization data, read only by Java, never by
  the SQL predicate on the hot path.
- `groups_markings` itself is intentionally **not** marking-filtered, to avoid a circular clearance
  dependency (`tech-design.md` §2.2).
- Escalation is checked against the caller's **resolved** clearance, not their raw grants.
- Cache eviction on every clearance-reducing event is a **correctness** requirement, not an optimisation.

## 3) Delivery steps

### Step 2.1 — Skimmed Task 1 prerequisite ✅ done

`marking_definitions` CRUD (superseded on this branch by PR #7651) and the empty `groups_markings` join
table. The assign/unassign endpoint was deliberately **not** built in this step — it lands in 2.4, where a
test can prove it does something.

### Step 2.2 — `MarkingCtx` + `MarkingScopeResolver` + `MarkingClearanceCacheManager` ✅ done

Pure Java, no SQL: groups → grants → highest order per type → expanded id set.

| Artefact | Note |
|---|---|
| `MarkingCtx` (`openaev-model`) | sealed `None` / `Restricted` / `All`, shaped like `TxCtx` |
| `MarkingScopeResolver` | mirrors `TenantScopeResolver`; highest-order-per-type then expand downward |
| `MarkingClearanceCacheManager` | `@AllowRawJdbc`; `findClearance` + `evict` / `evictForUser` / `evictForUsers` / `evictAll` |
| eviction wiring | `TenantGroupService` + `PlatformGroupService` (`updateGroupUsers`, `delete`), `MarkingDefinitionService` (`update`, `delete`), and the assign endpoint from 2.4 |

**DoD**: unit tests for highest-wins across groups, per-type independence, empty clearance, admin/BYPASS;
cache eviction test per `tech-design.md` §3.3.

**Corrections found while building, not while assuming**:

- `evict(userId, tenantId)` was the wrong API for membership changes — a *platform* group's `Group` entity
  is `DualScopeBase`-capable and can span tenants, so `evictForUser(userId)` walks every tenant the user
  belongs to defensively, evicting both bypass variants, even though (see `tech-design.md` §2.3) **assigning
  a marking to a platform group is itself out of scope** — this is generic membership-change eviction
  hygiene, not a platform-group marking feature. `PlatformGroupService` is wired only because group
  membership changes (not marking grants) can happen through it.
- Asset marking updates need **no** eviction: only the *clearance* is cached, the asset's own marking set is
  read fresh every time.
- `MarkingDefinitionService.update` calls `evictAll()`, not a targeted evict, because `order`/`type` are
  resolver inputs shared across every clearance of that type.

### Step 2.3 — marking scope written on both transaction paths ✅ done

`app.current_markings`-equivalent scope is set next to the tenant scope on both the HTTP path (aspect
derives `MarkingCtx` from the principal, no new controller parameter) and the background path
(`TenantScopedTransaction` defaults to all markings of the tenant(s) in scope). This step only *writes* the
scope — nothing reads it until Task 3 activates a table.

### Step 2.4 — `groups_markings` write path ✅ done (API only, no UI)

The assign/unassign **endpoint**, deferred from 2.1 and 2.2 until a test could prove it did something.
Nothing in this step is UI: the endpoint is exercised by integration tests and by a shell script that issues
raw `PUT` requests — an admin cannot do any of this from the product today (see Step 2.6).

| Artefact | Note |
|---|---|
| `PUT /api/tenants/{tenant}/groups/{group}/markings` | replace-the-whole-set; empty list revokes |
| `Group.markings` `@ManyToMany` | write path only — read path stays raw JDBC |
| `MarkingEscalationValidator` | write guard, `tech-design.md` §4.1 |
| `TenantGroupMarkingsApiTest` (6) | the three manual flows end-to-end; eviction mutation-checked |
| `MarkingEscalationValidatorTest` (6) | incl. "higher implies lower is allowed" |
| [`../task3/demo/group-markings.sh`](../task3/demo/group-markings.sh) | **plain `curl`** against the endpoint above — set what a group grants, the clearance side of the end-to-end demo. Stands in for the UI described in Step 2.6, which does not exist yet |

**Why this became a hard prerequisite rather than a nicety**: until it existed, nothing wrote
`groups_markings`, so every user resolved to `MarkingCtx.none()` and the clearance path was only provable
against a stubbed `JdbcTemplate` — Task 3's own definition of done ("user cleared `TLP:GREEN` cannot read a
`TLP:RED` asset") is undemonstrable with an empty grant table.

### Step 2.5 — finish the `ASSIGN_MARKING` capability chain (US0) 🔴 next

Task 1 registered a `MARKING` capability group with two chains (`tech-design.md` §5) but wired only the
Definitions one. Step 2.4's endpoint currently authorizes on the group's own `WRITE` capability alone — this
step adds the missing, independent gate.

**Build**:

- Wire `ACCESS_MARKING_ASSIGNMENT -> ASSIGN_MARKING -> DELETE_MARKING_ASSIGNMENT` on
  `PUT /api/tenants/{tenant}/groups/{group}/markings`, checked **in addition to** the existing group-scoped
  `WRITE` control, not instead of it.
- Expose the Assignment sub-group in the capability tree API next to Task 1's Definitions sub-group (both
  already share the `MARKING` parent).
- Frontend: parse the two capability strings; render the Assignment sub-group in the role editor.

**DoD**:

- A user with group `WRITE` but without `ASSIGN_MARKING` gets `403` on the assign endpoint.
- A user with `ASSIGN_MARKING` but without group `WRITE` also gets `403` — the two checks are independent,
  neither one alone is sufficient.
- Capability cascade test: `ASSIGN_MARKING` parent auto-enable behaves like every other chain in the tree.
- BYPASS still grants effective access, unchanged.
- Existing `TenantGroupMarkingsApiTest` flows still pass with the new check added — this step must not
  regress 2.4's tests, only tighten the gate in front of them.

### Step 2.6 — Group detail page: Markings UI (US1, US2, US3) 🔴 TODO — not started

This is the missing piece that turns the API from 2.4 into something an admin can actually use. Everything
below is **net-new frontend work**; nothing from this PoC covers it.

**Build**:

- **Feature-flagged, like Task 1**: the whole section is rendered only when `isFeatureEnabled('MARKING')`
  (mirroring `SecurityMenu.tsx` / `settings.config.tsx`'s `isFeatureEnabled('MARKING') && ability.can(...)`
  pattern) — with the flag off, the group detail page must look exactly as it does today, no empty
  "Markings" section, no stub.
- **US1 — view**: a "Markings" section on the group detail page listing the group's currently assigned
  markings (name + colour), with an empty state when none are assigned.
- **US2 — assign**: a selector on that section to add a marking; markings already assigned must not
  reappear as selectable options; saving calls the 2.4 `PUT` endpoint and refreshes the list on success.
- **US3 — remove**: a remove action per assigned marking, behind a confirmation dialog, calling the same
  `PUT` endpoint with that marking excluded from the set.
- Read side needs `marking_definitions` names/colours resolved for display — reuse Task 1's
  `marking_definitions` list-fetch pattern rather than re-inventing it.
- Respect the capability gate from **2.5**: the "add"/"remove" affordances should be disabled (not merely
  fail server-side) for a user who lacks `ASSIGN_MARKING`/`DELETE_MARKING_ASSIGNMENT`, consistent with how
  Task 1's frontend masks actions behind its own capability checks.

**DoD**:

- Component tests for the list, empty state, selector (already-assigned markings excluded), remove
  confirmation.
- Component test confirming the section renders nothing when `MARKING` is disabled (flag-off regression
  guard), matching how Task 1's `SecurityMenu`/`settings.config` tests already cover the flag-off case.
- An end-to-end manual/automated pass reproducing what `demo/group-markings.sh` currently does via `curl`,
  but through the UI.
- `yarn check-ts` / `yarn lint` pass on the new components.
- Acceptance criteria AC1–AC4 of US1/US2 and AC1–AC3 of US3 (`../user-stories.md`) are met literally, not
  just their underlying API.

**Explicitly not blocked on 2.5**: the UI can be built in parallel with the capability-chain work — it
should render disabled affordances for the pre-2.5 state (server-side check is still only group `WRITE`)
and simply gain the extra gate once 2.5 lands; it should not be sequenced strictly after it.

## 4) Deliberately deferred (not this task)

- The platform-group equivalent of the assign endpoint (cross-tenant question, answered deliberately, not by
  accident).
- Marking-filtering `groups_markings` itself.
- Default marking assignment for built-in groups (US4) beyond the seed data described in `tech-design.md`
  §6 — role-editor UX for reassigning defaults is out of scope for the PoC.

## 5) Validation matrix

- `MarkingScopeResolver`, `MarkingClearanceCacheManagerTest` — unit, green.
- `TenantGroupMarkingsApiTest`, `MarkingEscalationValidatorTest` — integration, green.
- `ASSIGN_MARKING` / `DELETE_MARKING_ASSIGNMENT` cascade + independence tests (step 2.5).
- Tenant isolation suite unaffected (this task adds no new statement-inspector dimension).
- `yarn check-ts` / `yarn lint` for the group-markings assignment UI — **not applicable yet**: no such UI
  exists on this branch (step 2.6, not started); applies once 2.6 lands.

## 6) Traceability to user stories

- US0 (finish `ASSIGN_MARKING` capability chain) — step 2.5, not started.
- US1 (view group markings) — **API done** (step 2.4, `GET` side of the endpoint); **UI not done** (step
  2.6). The user story's own acceptance criteria are written against the group detail page, so US1 is not
  complete until 2.6 lands.
- US2 (assign marking to group) — **API done** (step 2.4, `PUT` endpoint + `MarkingEscalationValidator`,
  gated by `ASSIGN_MARKING` once 2.5 lands); **UI not done** (step 2.6, the selector).
- US3 (remove marking from group) — **API done** (step 2.4, empty-list revoke path, gated by
  `DELETE_MARKING_ASSIGNMENT` once 2.5 lands); **UI not done** (step 2.6, the remove action + confirmation).
- US4 (default group assignment) — `tech-design.md` §5, seed data alongside Task 1's default TLP
  definitions; done as seed data, no UI for reassigning defaults (out of scope, §4).
