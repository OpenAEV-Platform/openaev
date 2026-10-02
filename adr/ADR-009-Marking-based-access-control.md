# ADR-007: Marking-based access control

|  |  |
| --- | --- |
| Status | Proposed |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7510 |
| Epic | https://github.com/OpenAEV-Platform/openaev/issues/7510 |
| Design docs | [`brainstorming/marking/`](../brainstorming/marking/) |
| Builds on | [ADR-002 — Multi-tenant data isolation strategy](./ADR-002-Multi-tenant-data-isolation-strategy.md) |

## 1. Context

OpenAEV today answers *"what may this user do?"* (capabilities, roles, groups) and *"which resources may
this user target?"* (grants). It has no answer to *"which resources is this user cleared to **see**?"* —
sensitivity is not modelled at all. Customers running a shared platform across teams of different
clearance need a red-team endpoint, a production credential or a sensitive asset group to be invisible to
users who are otherwise entitled to the feature.

The industry answer, and the one the Filigran suite already speaks elsewhere, is STIX **marking
definitions** (TLP, PAP): a row carries zero, one or many markings; a user holds a clearance; a user sees
a row only if their clearance covers every marking on it.

This lands on top of a live migration. ADR-002 introduced tenant isolation **v2**: a Hibernate
`StatementInspector` that rewrites every statement to add a `can_access_tenant(...)` predicate, driven by
a per-transaction Postgres GUC. That mechanism exists, is tested, and is being rolled out table by table.
Marking is a second sensitivity dimension over the same rows — so the first question is not *how to build
a filter* but *whether to reuse that enforcement point or build beside it*.

Scope of the current epic is deliberately narrow: **Asset Group, Endpoint, Security Platform, Credential**.
Scenario, Simulation and Atomic Testing are a following epic.

## 2. Decision drivers

In priority order:

1. **Enforcement must not be forgettable.** A missed marking check is a disclosure of exactly the data the
   feature exists to protect. An option where a new query silently returns over-clearance rows is
   disqualified on this driver alone.
2. **Cost per onboarded table must stay flat.** Four entity types this epic, three more next, and the
   long tail after. An option that is cheap for four and linear thereafter loses.
3. **Consistency with ADR-002.** Two different isolation philosophies in one codebase is a maintenance and
   incident-response cost, not just an aesthetic one.
4. **Blast radius of getting it wrong.** Marking sits below the whole read surface. Fail-closed must mean
   "see less", never "see nothing", and never "see more".
5. **Time to market for the current epic.**

Driver 1 outranks driver 5, and that is the trade-off this ADR makes explicit.

## 3. Considered options

### Option A: Service-layer marking filter

A shared `MarkingAccessService` applied in service methods of the four marked types.

**Pros**: fastest to deliver, explicit, easy to test, no migration risk.
**Cons**: enforcement happens where a developer *remembers* to call it. Every new read path is a new
opportunity to leak, silently.

### Option B: Repository-level filtering, explicitly wired per query

**B1** joins marking and group membership in each Specification/query. **B2** adds a precomputed clearance
so the read-time query stays cheap.

**Pros**: harder to forget than A once written; filtering in SQL rather than in memory scales better.
**Cons**: same structural flaw as A — it is opt-in per query. Higher complexity, and B2 adds cache
invalidation risk on top.

> A/B can be pushed a long way toward "centralised": a `Markable` interface, one shared specification, a
> hook in the pagination choke point, an AOP guard for non-paginated paths, and an ArchUnit rule that
> fails the build on an unguarded read. That last item is the only thing that makes A/B safe at scale, and
> it converts a silent leak into a build failure rather than eliminating it. Detail in
> [`tech-design.md`](../brainstorming/marking/tech-design.md).

### Option C: Transparent statement rewrite, reusing the tenant v2 mechanism

Generalise the ADR-002 enforcement point into a **scope dimension** abstraction, and add marking as a
second dimension alongside tenant: `ScopeStatementInspector` asks each active dimension for a predicate
and ANDs them. Clearance travels in its own GUC (`app.current_markings`) read by a new SQL function.

**Pros**: forgetting is *structurally impossible* — enforcement sits below the code a developer writes.
Fail-closed by construction. One enforcement point. Onboarding a table is one property entry plus one
migration.
**Cons**: highest upfront complexity. It means modifying the sole enforcement point of multi-tenancy v2,
so tenant isolation carries regression risk. Two properties of marking make it *not* a drop-in reuse:
clearance is **ordinal** where tenant scope is set-membership, and marking is **many-to-many** where
`tenant_id` is a local column. Does not cover Elasticsearch-served reads.

### Option D: Do nothing

Rejected: the epic exists because the capability layer cannot express sensitivity. Deferring does not make
the problem cheaper — it makes the eventual retrofit larger, because every table added meanwhile is
another table to onboard.

## 4. Decision

We choose **Option C**, because driver 1 admits no other answer: it is the only option where a developer
who has never heard of markings cannot write a leaking query.

**This decision is being validated by a PoC before it is committed to.** The status of this ADR is
`Proposed` and stays there until the PoC's definition of done is met. Options A and B remain the
fallbacks of record — the `Markable` contract and the shared-specification skeleton are the shape we
would retreat to.

Concretely, Option C is composed of:

- **`ScopeDimension`** — the abstraction extracted from the tenant inspector so a dimension can be added
  without forking the rewrite skeleton. Tenant and marking are two implementations; the inspector ANDs
  whichever are active on a table. A table may be on **tenant v1 `@Filter` and marking v2 at the same
  time** — verified empirically, which is what lets marking activate on `assets` without waiting for the
  tenant v2 rollout to reach it.
- **`marking_ids text[]`** — a column on the marked table, not a per-entity join table. This reverses an
  earlier choice, argued in the design doc: the epic's real deliverable is a repeatable *activation
  procedure*, and a procedure built on join tables is discarded the first time a relationship needs
  marking (63 tables have composite primary keys). Join tables are retained as the fallback of record.
- **A negative SQL predicate.** Visibility is *"there is no marking on this row that I do not hold"* — a
  `NOT EXISTS` anti-join over the array. Naming the function for the missing half keeps the generated SQL
  free of a double negative, and makes unmarked rows visible for free with no `allow_unmarked` flag. The
  positive formulation leaks and is pinned against by test.
- **`MarkingCtx`** — the clearance badge, mirroring ADR-002's `TxCtx` but with a different empty state.
  `TxCtx.Missing` sees nothing; `MarkingCtx.None` still sees unmarked rows. Holding no clearance is a
  normal, safe state. **Fail-closed for marking means "see less", not "see nothing".**
- **Ordinality resolved in Java, not SQL.** The resolver takes the granted marking ids plus the tenant's
  scale, computes the maximum order *per type*, and expands back to every id at or below it. The result is
  a flat set, so the SQL stays plain containment. A type with no grant contributes nothing — not even its
  lowest level.
- **A clearance cache** read over raw JDBC, for the same reason `TenantMembershipCacheManager` is: it runs
  on the pre-transaction argument-resolver path, where a JPA query would pin a pool connection for the
  whole request.
- **Per-table activation** via `openaev.marking.active-tables`, and the activation procedure captured as a
  repeatable skill — the same shape as ADR-002's runbook.

Governance decisions (multi-marking is **AND**; unmarked is visible to all; `BYPASS` overrides; background
jobs see all markings; over-clearance direct `GET` returns **404** not 403, because a 403 leaks existence;
a user may not assign a marking they do not hold) are recorded with their reasoning in
[`tech-design-option-c.md` §6](../brainstorming/marking/tech-design-option-c.md).

## 5. Consequences

### Positive

- Marking enforcement cannot be bypassed by forgetting it. New repositories, new native queries and joins
  written by developers unaware of marking are all covered.
- Onboarding a table is a property entry plus a migration, so the cost stays flat as the next epic adds
  Scenario, Simulation and Atomic Testing.
- One enforcement point, one incident-response story, one mental model shared with tenant isolation.
- Extracting `ScopeDimension` leaves the tenant mechanism better factored than it found it.

### Negative / trade-offs

- **We are modifying the sole enforcement point of multi-tenancy.** A marking bug can become a tenant bug.
  The existing tenant inspector regression suite must stay green and unmodified — that is the gate on
  every step, not a final check.
- **Slower to first delivery than Option A.** Accepted, on driver 1.
- **The fail-closed blast radius is paid at activation.** Activating marking on a table pulls every
  statement touching it into parse-and-rewrite; an unsupported SQL shape is refused rather than run.
  `assets` is deliberately activated first because it is the most-joined, and therefore the honest test.
- **Marking ends up stricter than tenant v1 on a shared table.** v1 `@Filter` covers neither bulk HQL nor
  native queries; the inspector covers both. The table is then marking-isolated on paths where it is not
  tenant-isolated.
- **Clearance caching fails open.** The SQL function is pure set containment and never consults
  `marking_definitions`, so a stale *larger* cached clearance grants access that nothing downstream can
  detect. Every reduction of a clearance must evict. This is a correctness requirement, not an
  optimisation detail, and it is a gate on activating the first table.
- **Elasticsearch-served reads are not covered** by this mechanism and need their own answer.
- **Derived and denormalised data is out of scope for this epic** and is a known consequence: expectation
  rows and native aggregate queries can surface information about assets the viewer cannot see.

### Neutral

- No REST contract changes: the clearance is derived in the aspect, so endpoint signatures are untouched.
- Background work deliberately sees all markings — jobs must execute against every asset, including marked
  ones. Visibility and execution are separate knobs by design.
- The group-marking grant table is a clearance *grant*, never itself a marked table.
- Marking is additive to RBAC: capability, then grant, then clearance. A deny at any step denies.

## 6. Further reading

The working design documents live in [`brainstorming/marking/`](../brainstorming/marking/):

| Document | Contents |
| --- | --- |
| [`user-stories.md`](../brainstorming/marking/user-stories.md) | Epic scope, user stories and acceptance criteria |
| [`tech-design.md`](../brainstorming/marking/tech-design.md) | Existing RBAC analysis, and the full A / B / C option comparison summarised in §3 |
| [`tech-design-option-c.md`](../brainstorming/marking/tech-design-option-c.md) | The chosen design in detail: data model and the schema-shape argument, runtime architecture, risks, and the decision log |
| [`implementation-plan-option-c.md`](../brainstorming/marking/implementation-plan-option-c.md) | Delivery plan, per-step status, and the PoC definition of done |

> These are **working documents**, not a specification. They record the reasoning, including reversed
> decisions and open questions. This ADR is the stable summary; when the two disagree, the ADR is the one
> that was reviewed. Once the mechanism ships, the contributor-facing "how to work with it" page belongs in
> `docs/docs/development/`, next to
> [`tenant-isolation.md`](../docs/docs/development/tenant-isolation.md).
