# Manual testing: assigning markings to a group

Companion to [`tech-design-option-c.md`](tech-design-option-c.md). Everything below runs against a
local dev stack and exercises the write path added for `groups_markings` — the piece the rest of the
marking design was blocked on, since until it existed every user resolved to `MarkingCtx.none()`.

> ⚠️ **What you can and cannot observe today.** No table is marking-active yet
> (`openaev.marking.active-tables` is empty), so assigning a marking changes the caller's *clearance*
> but does not yet change which rows they see. The `app.current_markings` GUC is written on every
> request; nothing reads it. Step 3 (activating `assets`) is what turns the flows below into visible
> row filtering. Until then, verify the clearance rather than the result set — §4.

---

## 1. Setup

```bash
export OPENAEV_URL="http://localhost:8080"
export TOKEN="<your api key from the user profile page>"
export TENANT="<tenant uuid>"

auth=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")
```

Tenant-scoped endpoints take the tenant in the path. The non-prefixed form works too, with the
tenant in an `X-Tenant-Ids` header — both are shown below, since the marking write path resolves its
tenant through `TenantWriteScopeResolver` and refuses a scope that does not pin exactly one tenant.

---

## 2. The endpoint

```http
PUT /api/tenants/{tenantId}/groups/{groupId}/markings
```

```json
{ "group_markings": ["<marking-uuid>", "..."] }
```

**Replace-the-whole-set**, like the sibling `users` and `roles` endpoints: an empty list revokes
every grant. A PATCH-style add/remove would make "what does this group grant?" depend on request
ordering, which is the wrong property for a security boundary.

| Response | Meaning |
|---|---|
| `200` | Assigned. Every member's cached clearance was evicted, so it takes effect on their next request. |
| `403` | You tried to assign a marking you do not hold yourself (design Q7), or you lack `WRITE` on the group. |
| `404` | The group, or one of the markings, does not exist **in your tenant**. Nothing is assigned — never a partial write. |

---

## 3. Walking the flows

### 3.0 Find the ids you need

Every tenant is seeded with nine default markings, so you do not need to create any:

| Type | Levels (by `marking_order`) |
|---|---|
| `TLP` | `CLEAR` 10 · `GREEN` 20 · `AMBER` 30 · `AMBER+STRICT` 40 · `RED` 50 |
| `PAP` | `CLEAR` 10 · `GREEN` 20 · `AMBER` 30 · `RED` 50 |

> The scale says **AMBER**, not ORANGE — the flows below use `TLP:AMBER` wherever you would say
> "orange". Note also that `AMBER+STRICT` sits *between* `AMBER` and `RED`, so granting `AMBER` does
> **not** include it. Types are independent scales: holding `TLP:RED` says nothing about `PAP`.

```bash
curl -s "${auth[@]}" -X POST \
  "${OPENAEV_URL}/api/tenants/${TENANT}/marking-definitions/search" \
  -d '{"page":0,"size":50,"sorts":[{"property":"marking_order","direction":"asc"}]}' \
  | jq -r '.content[] | "\(.marking_id)  \(.marking_type):\(.marking_name)  order=\(.marking_order)"'
```

```bash
export M_GREEN="<id of TLP:GREEN>"
export M_AMBER="<id of TLP:AMBER>"
export M_RED="<id of TLP:RED>"
export GROUP_A="<group uuid>"
export GROUP_B="<group uuid>"
```

Make sure the user you are testing with is a **member** of the group — a clearance is what a group
grants *its members*:

```bash
curl -s "${auth[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_A}/users" \
  -d "{\"group_users\":[\"${USER_ID}\"]}"
```

### 3.1 Group marked AMBER, asset GREEN → the user sees it

```bash
curl -s "${auth[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_A}/markings" \
  -d "{\"group_markings\":[\"${M_AMBER}\"]}" | jq '.group_markings'
```

The response echoes **only** `TLP:AMBER` — that is the *grant*. The *clearance* it resolves to is
wider: `AMBER` implies `GREEN` implies `CLEAR`. That expansion happens in Java
(`MarkingScopeResolver`), once per request, precisely so the SQL predicate can stay a flat
containment test with no notion of order. Check it in §4 — a GREEN asset is inside the clearance.

### 3.2 Group marked AMBER, asset RED → the user does not see it

Same grant as 3.1, nothing to change. `RED` (50) sits *above* `AMBER` (30), so it is not in the
expansion and a RED asset falls outside the clearance. Confirm with §4: neither `TLP:RED` nor
`TLP:AMBER+STRICT` may appear.

### 3.3 Admin raises Group B to RED → the user sees it, without waiting

```bash
curl -s "${auth[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_B}/markings" \
  -d "{\"group_markings\":[\"${M_RED}\"]}"
```

Then immediately re-run §4. `M_RED` is present.

🔴 **This is the flow that must not need a wait.** The cached clearance is pure set containment and
never re-reads `groups_markings`, so the cache is a *correctness* mechanism, not a performance one:
a stale entry that is too **large** keeps granting access that the data no longer justifies — it
fails **open**. Every write to this endpoint evicts every member's clearance, in every tenant
(a group is dual-scope, so a platform group can grant into several). The 5-minute TTL bounds the
damage; it does not prevent it.

### 3.4 The guard: assigning above your own clearance

As a **non-admin** user who holds only `GREEN`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' "${auth[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_A}/markings" \
  -d "{\"group_markings\":[\"${M_RED}\"]}"
# 403 — Cannot assign markings you do not hold: TLP:RED
```

Without this, "may manage groups" would silently mean "may read every marked row": put yourself in a
group, grant it `TLP:RED`, done. Note the guard checks the **resolved** clearance, so a user holding
`AMBER` *may* grant `GREEN` — they can already read every GREEN row, so granting it discloses
nothing they could not disclose by other means.

### 3.5 Revoking

```bash
curl -s "${auth[@]}" -X PUT \
  "${OPENAEV_URL}/api/tenants/${TENANT}/groups/${GROUP_A}/markings" \
  -d '{"group_markings":[]}'
```

The direction that fails open if eviction is ever missed. Verify with §4 that the clearance actually
shrinks.

---

## 4. Verifying the clearance

The clearance is not exposed over the API (it is derived, never supplied — a marking request
parameter would be a forgettable security boundary). Read it from the database instead.

**The grants** — what the groups say:

```bash
cd openaev-dev && docker compose exec -T openaev-dev-pgsql psql -U openaev -d openaev -c "
  select g.group_name, md.marking_type || ':' || md.marking_name as marking
  from groups_markings gm
  join groups g on g.group_id = gm.group_id
  join marking_definitions md on md.marking_id = gm.marking_id
  order by g.group_name, md.marking_order;"
```

**The resolved clearance** — what a given user actually holds, ordinality already expanded. This is
the exact query `MarkingClearanceCacheManager` runs, plus the expansion:

```bash
cd openaev-dev && docker compose exec -T openaev-dev-pgsql psql -U openaev -d openaev -c "
  with granted as (
    select md.marking_type, max(md.marking_order) as highest
    from groups_markings gm
    join users_groups ug on ug.group_id = gm.group_id
    join marking_definitions md on md.marking_id = gm.marking_id
    where ug.user_id = '${USER_ID}' and md.tenant_id = '${TENANT}'
    group by md.marking_type
  )
  select md.marking_type || ':' || md.marking_name as holds
  from marking_definitions md
  join granted on granted.marking_type = md.marking_type
  where md.tenant_id = '${TENANT}' and md.marking_order <= granted.highest
  order by md.marking_type, md.marking_order;"
```

Run this after each step above. It is the ground truth the `app.current_markings` GUC is set from.

> An admin or `BYPASS` holder short-circuits all of this and resolves to the whole tenant scale, so
> **test the flows with a non-admin user** or every one of them will pass for the wrong reason.

---

## 5. What is covered automatically

| Test | Covers |
|---|---|
| `TenantGroupMarkingsApiTest` | §3.1, §3.2, §3.3, §3.5 end-to-end, plus the cross-tenant refusal |
| `MarkingEscalationValidatorTest` | §3.4, including the "higher implies lower" allowance |
| `MarkingClearanceCacheManagerCachingTest` | the eviction triggers, each mutation-tested |
| `MarkingScopeResolverTest` | the ordinality expansion §3.1 relies on, per type |

Two findings worth carrying forward, both surfaced by writing these tests:

- **Tenant isolation is not the only thing stopping a cross-tenant assignment, and cannot be.** The
  statement inspector can only rewrite a query that is actually *issued*; an entity already in the
  persistence context is served from Hibernate's first-level cache and never filtered. The
  independent guarantee is the escalation guard — a clearance is per tenant, so nobody holds another
  tenant's marking. The two are not redundant.
- **Asset marking updates need no cache eviction.** `is_marking_set_allowed(row_marking_ids)` takes
  the row's array as a function *argument*; only the clearance lives in the GUC, and the row is
  re-read on every query. An evict on asset save would be a no-op that *looks* like protection.
