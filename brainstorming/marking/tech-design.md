# Feature: Marking-based Access Control for Assets - Phase 1

**Issue**: #[number] (if applicable)
**Type**: Full Stack
**Estimation**: [S | M | L | XL]

---

## 📋 Context

### Problem to Solve
Capability:
OAEV has a way to grant access to resources using either Group/role/capabilitites. if a user is part of a group with an associated role that contain the capability to READ/WRITE/DELETE a resource, the user can perform those actions.
With the recent implementation of bypass and grant escalation prevention, a user can not grant access to a capability he has not access to.

Grant:
A user can access a scenario/simulation/atomic testing either through a specific grant, or globally if their group has the ASSESSMENT capability — and having the ASSESSMENT capability overrides individual grants (i.e., gives full access regardless of what grants are or aren't set).
How Grants Work in OpenAEV
Grants in OpenAEV are the fine-grained, resource-level permission layer that sits on top of the global RBAC (Roles → Capabilities). While Roles/Capabilities control what a user can do platform-wide (e.g., "can create scenarios"), Grants control access to specific individual resources (e.g., "this one simulation").
Grants are always managed at the group level — you don't grant access to an individual user directly, you grant it to a group, and members inherit it.

**Problem: access does not scale, because it has to be granted one resource at a time.**

Grants are per-resource. To let a team see thirty simulations, someone assigns thirty grants — and
assigns thirty more next month. The only alternative on offer is the `ASSESSMENT` capability, which
overrides grants entirely and gives access to *everything*. So the choice today is between
per-resource bookkeeping that nobody keeps up with, and all-or-nothing.

Neither expresses what people actually mean, which is rarely "this user may see resource #4471". It is
almost always **"this is sensitive, and only people cleared for that level should see it"** — a
property of the resource, not a list of who may open it.

**What we want: sensitivity as a label on the resource, and a clearance on the group.**

A resource carries a marking; a group is granted a clearance. A user sees a resource when their
clearance covers the marking it carries. Nobody maintains a list.

- Resource marked **TLP:RED**, my group is cleared **TLP:RED** → I see it.
- Resource marked **TLP:RED**, my group is cleared only **TLP:AMBER** (one level below) → I do not.

Markings are **ordered** (1 to 10, highest last), and a clearance **expands downward**: cleared for
TLP:RED means cleared for RED and everything beneath it, so the same user also sees TLP:AMBER
resources. Only the resource's own marking is ever compared against the clearance.

Two consequences worth stating up front, because the rest of this document depends on them:

- **Unmarked means visible.** Adding markings changes nothing until something is actually marked, so
  the feature ships without a backfill and is inert until first use.
- **A marking can only ever reduce visibility, never widen it.** Marking is a filter layered *on top
  of* RBAC — it never grants access that capabilities and grants have already denied.

## Design options

### Scope (current epic)
- In scope: **Asset (Endpoint, Security Platform), Credential**.
- Out of scope for this epic: **Asset Group**, and **Scenario, Simulation, Atomic Testing** (next epic).

### Use Cases
1. **Administrator with the right capability** manages group markings and group membership so users inherit effective marking access.
2. **User with the right capability** assigns/removes markings on Endpoint, Security Platform, and Credential.
3. **User with the right capability** assigns/updates/removes markings on assets via bulk edit.
4. **Standard user** can only view assets within their authorized marking scope.

### Integration in Architecture
- Keep **Capabilities/Roles/Groups** as-is for action authorization.
- Add a **Marking level** attribute on markable assets: Endpoint, Security Platform, Credential.
- Add **group-level marking clearance** for each marking domain (for example `TLP`), inherited by users through groups.
- Effective read access for those asset types becomes:
  - User has required capability for the asset type **and**
  - Asset marking level is `<=` user effective clearance for that domain.
- Marking is an additional visibility guard, layered on top of existing RBAC/capability checks.

### Existing design fit (RBAC + Capability/Role/Group)
- **RBAC remains the action layer**: "what user can do".
- **Grant/asset-access rules remain resource-scoping layer**: "which assets user may target".
- **Marking becomes sensitivity layer**: "which marked assets user can see".
- Final decision model:
  1. Capability check (action allowed?)
  2. Grant/global access check (asset in scope?)
  3. Marking clearance check (classification allowed?)
- A deny at any step denies access.

### Domain model (common to every option)

This is the data model the feature needs. It is the same whichever enforcement mechanism is chosen
below — what changes between the options is only **where the visibility check runs**, never what a
marking *is* or how a user comes to hold one.

```mermaid
classDiagram
    class MarkingDefinition {
      +id
      +type
      +definition
      +order
      +color
    }
    class Group {
      +id
      +roles
      +users
    }
    class GroupMarking {
      +groupId
      +markingId
    }
    class Endpoint
    class SecurityPlatform
    class Credential

    Group "1" --> "*" GroupMarking
    GroupMarking "*" --> "1" MarkingDefinition
    Endpoint "*" --> "*" MarkingDefinition : object_marking_refs
    SecurityPlatform "*" --> "*" MarkingDefinition : object_marking_refs
    Credential "*" --> "*" MarkingDefinition : object_marking_refs
```

Two invariants follow from this model alone, and every option below must satisfy them:

- A user's **clearance** is derived from their groups' markings, never assigned directly — the same
  rule already used for roles and grants.
- An entity carries **zero or more** markings (STIX `object_marking_refs`), so the visibility test is
  set containment, not a scalar comparison. See [Marking cardinality](#marking-cardinality).

### Implementation options (with pros/cons)

1. **Option A — Service-layer marking filter with shared `MarkingAccessService`** — *evaluated, not chosen*
   - **How**: Keep repositories mostly unchanged; apply marking visibility in service methods for Endpoint, Security Platform, Credential. Reuse one shared resolver for effective clearance.
   - **Pros**: Fastest delivery for this epic, explicit logic, easy to test, low migration risk.
   - **Cons**: Requires discipline to call filter in every read path; potential duplication if not centralized.

   - **What it looks like**: two shared services, and a per-row check on the way out of the service
     layer.

     ```mermaid
     classDiagram
         class MarkingAccessService {
           +canView(user, entityMarkings) boolean
           +effectiveClearance(user, markingType) int
           +visibleMarkingIds(user) Set~String~
         }
         class MarkingAssignmentService {
           +assignMarkings(entityId, markingIds)
           +bulkAssignMarkings(entityIds, markingIds)
           +removeMarking(entityId, markingId)
         }
         MarkingAccessService --> Group
         MarkingAccessService --> MarkingDefinition
         MarkingAssignmentService --> MarkingDefinition
         MarkingAssignmentService --> Endpoint
         MarkingAssignmentService --> SecurityPlatform
         MarkingAssignmentService --> Credential
     ```

     ```mermaid
     sequenceDiagram
         actor U as Standard user
         participant API as Asset API (search/read)
         participant AOP as AccessControlAspect
         participant SVC as Asset Service
         participant MAS as MarkingAccessService
         participant DB as Repository

         U->>API: Search assets
         API->>AOP: Check action capability (READ/SEARCH)
         alt denied
             AOP-->>API: 403
             API-->>U: 403 Forbidden
         else allowed
             API->>SVC: search(...)
             SVC->>DB: load candidate assets
             loop each asset
                 SVC->>MAS: canView(user, asset.markings)
                 Note right of MAS: AND semantics —<br/>user must hold EVERY marking<br/>(unmarked ⇒ visible)
                 MAS-->>SVC: allow/deny
             end
             SVC-->>API: filtered assets
             API-->>U: 200 OK
         end
     ```

     Note what the loop implies: the repository returns rows the user may not see, and correctness
     depends on the service remembering to filter them. It also breaks pagination — a page of 50
     rows can come back with 30 after filtering. Both are the core objection to this option.

2. **Option B — Repository-level filtering, explicitly wired per query** — *evaluated, not chosen*
   - Both sub-options require explicitly wiring marking clearance into every repository/query path
     that reads a marked asset type (e.g. adding a `findByMarkingClearance(...)`-style method, or
     joining marking + group membership manually in each Specification/query). Nothing enforces
     this automatically — it is on the developer to remember to add it to every new query.

   - **B1 — Explicit repository filtering (Specifications/queries join marking + group membership)**
     - **How**: Push visibility checks into DB queries so only authorized assets are returned (join marking + group membership at query time, computed fresh on every read).
     - **Pros**: Strong consistency when applied (harder to forget than a service-layer check once written), better performance on large result sets than filtering in-memory.
     - **Cons**: Higher implementation complexity, query maintenance cost, more tenant/scoping edge cases, must be duplicated across every repository method.

   - **B2 — Explicit repository filtering + precomputed/cached effective clearance**
     - **How**: Same explicit repository wiring as B1, but user effective marking clearance per domain is precomputed (cache/table) whenever group memberships/markings change, and reused by the repository queries instead of recomputing joins/subqueries every time.
     - **Pros**: Best runtime efficiency for heavy search traffic, simpler read-time query shape than B1.
     - **Cons**: Highest complexity of the explicit options, invalidation/sync risks for the cache, heavier rollout for the current epic.

3. **Option C — Reuse the tenant-filter transparent rewrite mechanism ("à la" `TenantStatementInspector` / `can_access_tenant`)** — ✅ **chosen**
   - **How**: Extend the existing statement-inspector rewrite (used today for `tenant_id` via `app.current_tenants` + `can_access_tenant(...)`) to also inject a marking predicate on marked tables, driven by a per-transaction session variable (`app.current_markings`) derived from the current user's effective group clearance. Because markings are many-to-many, the injected predicate is a correlated anti-join on the entity's markings join table.
   - **Pros**: Fully transparent to developers — no per-repository/query changes needed (unlike B1/B2), consistent with the existing tenant isolation pattern, hard to forget (fail-closed by design like tenant filtering), single enforcement point, onboarding a new table costs one migration + one property entry.
   - **Cons**: Higher upfront complexity (SQL rewrite logic, session variable management), couples marking enforcement to the Hibernate/JDBC layer, harder to reason about/debug than an explicit repository predicate, the M2M anti-join is heavier than tenant's single-column check, and it means modifying the sole enforcement point of multi-tenancy v2 (regression risk), heavier testing surface (matches `TenantStatementInspector`'s own complexity). Does **not** cover Elasticsearch-served reads.

#### Deep dive — generalizing the tenant mechanism for Option C

Today's tenant isolation (v2, statement-inspector based) is built from three cooperating pieces (all in `openaev-api`/`openaev-model`):

| Component | Current responsibility (tenant-only) |
|---|---|
| `TenantFilteringConfig` | Derives `TenantTables` from the DB schema (`tenant_id` column presence + nullability), installs `TenantStatementInspector` as Hibernate's `STATEMENT_INSPECTOR`. |
| `TenantTables` | Static registry of which tables are scoped and how (`STRICT` vs `DUAL`), keyed off a single column name (`tenant_id`). |
| `TenantStatementInspector` | Parses every SQL statement and rewrites FROM/JOIN/UPDATE/DELETE/INSERT so each scoped table gets `can_access_tenant(tenant_id, ...)` in its WHERE — this is what makes it *transparent* (no repository code needed). |
| `TenantScopeTransactionAspect` + `can_access_tenant()` (SQL function) | Writes the per-transaction scope (`app.current_tenants`, sourced from `TxCtx`, i.e. the current user's tenant memberships) into a Postgres session GUC that the SQL function reads. |

To generalize this for marking, each piece would become **scope-kind aware** instead of tenant-specific:

1. **`TenantTables` → `ScopedTables`**: keyed by a `ScopeKind` enum (`TENANT`, `MARKING`, ...), each kind carrying its own metadata — a column name (`tenant_id`) for the tenant kind, and per-table join metadata (PK column, `<table>_markings` join table, FK column) for the marking kind, since markings are many-to-many and are not a column on the filtered row. A table can be registered under multiple kinds at once (e.g. `assets` is both tenant-scoped and marking-scoped) — the inspector would then AND both predicates instead of only one.

2. **`TenantStatementInspector` → generic `ScopeStatementInspector`**: same SQL-rewrite skeleton, but instead of hardcoding `can_access_tenant`, it asks each active scope dimension for a predicate on the table alias and ANDs them, e.g. `can_access_tenant(t.tenant_id) AND <marking predicate>`. Two differences make the marking dimension shaped differently from the tenant one: (a) marking clearance is *ordinal* while tenant scope is *set-membership*, and (b) marking is **many-to-many** (an entity carries zero, one or many markings via a join table), so the predicate cannot read a local column. Both are solvable — see [tech-design-option-c.md](./tech-design-option-c.md), which resolves ordinality in Java (expanding a clearance into a flat set of marking ids, restoring plain set-membership in SQL) and expresses the M2M check as a correlated anti-join.

3. **`can_access_tenant()` → add `is_marking_missing(row_marking_id)`**: a new Postgres function reading a new GUC (`app.current_markings`), fail-closed the same way as the tenant one. It is deliberately **negative** — true means "the caller does not hold this marking" — because markings live in a join table and visibility is decided by a `NOT EXISTS (...)` **anti-join**: "visible if there is no marking on this row that I do not hold". Naming it for the missing half keeps the generated SQL free of a double negative and stops it being mistaken for a row-level visibility test (the positive form leaks — see Option C §4.4). The shape also makes unmarked entities visible for free (Case 2), with no `allow_unmarked` flag needed.

4. **`TenantScopeTransactionAspect` → generalize `TxCtx`**: today `TxCtx` only carries tenant scope. It would need to also carry the current user's effective markings, so the aspect can `set_config('app.current_markings', ..., true)` alongside `app.current_tenants` in the same `@Before` advice — one shared "scope context" object instead of one purely tenant-shaped one.

**Why this is a bigger lift than it first looks**: the tenant mechanism was designed around one invariant (row belongs to exactly one tenant, checked by set membership on a local column). Marking is ordinal, per-type, and many-to-many, so the "generic" mechanism isn't a drop-in reuse — it's a *second, parallel scope dimension* bolted onto the same rewrite skeleton. It is architecturally sound (same fail-closed, transparent philosophy) but is realistically a multi-sprint investment (schema derivation, inspector rewrite logic, new SQL function, GUC/aspect changes, and a full regression pass on `TenantStatementInspector`'s existing test suite so generalizing it doesn't regress tenant isolation). **Detailed design, risks and PoC plan: [tech-design-option-c.md](./tech-design-option-c.md).**

### Making Options A / B generic and reusable as more entities are onboarded

Options A and B are opt-in by construction: enforcement happens where a developer *remembers* to call it.
They can still be made significantly more reusable — the goal is to reduce the per-entity cost from
"write a marking predicate for every query" to "declare the entity as markable", and to make the
remaining human step **impossible to forget silently**.

1. **A single `Markable` contract on the model side.** `interface Markable { Set<MarkingDefinition> getMarkings(); }`
   implemented by `Asset` (covers Endpoint + Security Platform) and `CredentialSecretReference`.
   Everything below keys off this interface instead of enumerating entity types.

2. **One shared predicate, not one per repository.** A single
   `MarkingSpecifications.visibleTo(user)` returning a reusable `Specification<T extends Markable>`.
   Because markings are **many-to-many**, the predicate is a "no marking I don't hold" check — in JPA
   Criteria a correlated `NOT EXISTS` subquery on the join, *not* a naive `join.get("id").in(clearedIds)`
   (which would silently give OR/union semantics and leak). Adding an entity adds **zero** new predicate code.

3. **Hook it into the pagination choke point.** Every paginated search endpoint funnels through
   `PaginationUtils.buildPaginationJPA(...)` / `buildPaginationCriteriaBuilder(...)`, which already
   composes `filterSpecifications.and(searchSpecifications)`. `.and(markingSpecification)` can be added
   there once, applied automatically to any `Markable` entity class. This makes **Option B transparent for
   the search/list paths**, which is the bulk of the read surface — the single highest-leverage change for
   reusability.

4. **A shared read guard for the non-paginated paths (Option A).** Single-entity `findById`, "related
   objects" lookups and export paths do not go through pagination. Cover them with one `@MarkingFiltered`
   AOP advice on service methods returning `Markable` (or `Collection<Markable>` / `Page<Markable>`) that
   post-filters generically via the interface — one aspect, N entities, no per-service code.

5. **A registry + a build-time guardrail.** A `MarkingActiveEntities` registry (the A/B analogue of
   `openaev.marking.active-tables`) plus an **ArchUnit rule** that fails the build when a repository method
   returning a registered `Markable` type is called from a service without a marking specification or
   `@MarkingFiltered`. This mirrors the existing tenant ArchUnit guardrails and is the *only* mechanism that
   makes A/B safe at scale.

6. **One write guard for all of them.** `MarkingEscalationValidator.assertCanAssignMarking(user, markingId)`,
   modeled on `PrivilegeEscalationValidator`, called from every write path regardless of entity type.

**The honest trade-off**: items 1–4 make A/B *centralized*, and item 3 makes the search paths effectively
transparent — but enforcement remains **opt-in per query**. A new native `@Query`, a new custom repository
method, or a new join added by someone unaware of marking will silently return over-clearance rows. Item 5
converts that from a silent leak into a build failure, which is as close to safe as A/B can get. Option C
is the only option where forgetting is *structurally impossible*, because the enforcement point is below
the code a developer writes.

### Direction — Option C

**Decided: Option C.** Recorded in [ADR-007](../../adr/ADR-007-Marking-based-access-control.md);
detailed design, risks and PoC plan in [tech-design-option-c.md](./tech-design-option-c.md).

The deciding argument is not cost, and it is not performance — it is that **A and B are opt-in by
construction**. In both, enforcement happens where a developer remembers to put it, so a new native
`@Query`, a new custom repository method, or a join added by someone who has never heard of markings
returns over-clearance rows and nothing complains. Section
[Making Options A / B generic](#making-options-a--b-generic-and-reusable-as-more-entities-are-onboarded)
sets out how far that can be mitigated: a `Markable` contract, one shared specification, a hook in
the pagination choke point, and an ArchUnit rule that turns a forgotten filter into a build failure.
That is genuinely close to safe — but it is still a list of things that must not be forgotten.

Option C is the only option where forgetting is **structurally impossible**, because the enforcement
point sits below the SQL a developer writes. That is the same reason multi-tenancy v2 works, and
marking is the second dimension on the same mechanism rather than a parallel invention.

What was accepted in exchange, honestly:

- **Slower to first delivery than Option A.** Accepted on the argument above.
- **Higher upfront complexity** — statement rewriting, a new SQL function, GUC and transaction-scope
  management — and it means touching the single enforcement point of multi-tenancy v2, so the
  existing tenant isolation suite has to stay green throughout.
- **Does not cover Elasticsearch-served reads.** Out of PoC scope, tracked as step 5.1.

Options A and B are kept above as the alternatives that were evaluated, not as live proposals. The
`Markable` / shared-specification / ArchUnit material under "Making Options A / B generic" is
retained deliberately: if Option C were ever abandoned, that section is the fallback design, and it
also documents *why* the mitigations were judged insufficient rather than leaving that implied.

