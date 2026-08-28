# Option C — Marking isolation "à la" tenant v2 

**Marking cardinality: many-to-many.** An entity carries **zero, one or many** markings
(STIX `object_marking_refs` semantics). This is the decision this document is built on, and it drives most
of the design below. *How* that set is stored physically is a separate, argued choice — see section 3.

---

## 1. Goal

Enforce marking-based visibility the **same way tenant isolation v2** is enforced today: **transparently**, at
the SQL level, driven by a per-transaction scope channel — so that onboarding a new table to marking is a
**configuration change plus one migration**, not a rewrite of every repository method or service layer.

Target developer experience:

```properties
# adding a table to marking isolation = 1 line + 1 migration
openaev.marking.active-tables=assets,asset_groups,secret_references
```

---

## 2. Why the tenant v2 mechanism is the right shape to reuse

The v2 tenant stack has exactly the four properties marking needs:
* **Transparent** for the developer: `TenantStatementInspector` rewrites the SQL Hibernate emits => ✅ identical need for marking
* **Fail-closed** when the GUC is unset: `can_access_tenant()` returns false => ✅ identical need for marking
* **Per-request**: `TxCtxArgumentResolver` → `TenantScopeResolver` → `TxCtx` → `set_config('app.current_tenants', …, true)` => ✅ same shape (groups → markings)
* **Incrementally activatable**: `openaev.tenant.active-tables` allowlist, inert until a table is onboarded => ✅ same need

### 2.1 The hard constraint that shapes the whole design

Hibernate accepts **exactly one** `AvailableSettings.STATEMENT_INSPECTOR`
(`TenantFilteringConfig#tenantStatementInspectorCustomizer` installs it with `putIfAbsent`).

>  ⚠️ ⚠️ **Marking cannot be a second, independent inspector.** It must be folded into the existing rewrite as a
> second *scope dimension*, or it will silently displace tenant isolation.

This is the single most important architectural consequence of choosing Option C.

### 2.2 The design trick that keeps it cheap: resolve ordinality in Java, not in SQL

The naive reading of Option C says: tenant is a *set-membership* check
(`row_tenant_id = ANY(app.current_tenants)`) while marking is an *ordinal* check
(`row_marking_order <= my_clearance`), so the generic mechanism must support two comparison styles
(this is the concern raised in the parent doc's deep dive).

> 💡💡 **It does not have to.** Ordinality is resolved **once per request, in Java**, where the user's groups and
clearances are already loaded. In java we have a logic that flatten set of marking ids and resolve => `app.current_markings = "id1,id2,id3"` where for ex: `id1:TPL:clear, id2: TPL:green, id3: CUSTOM1:green`

### 2.3 What many-to-many changes

Tenant isolation compares a **scalar**: `t.tenant_id` against a list. Marking compares a **set** against a
set. That single difference — not the choice of schema — is what drives the rest of the design:

```
tenant  :  row's tenant_id   ∈  my tenants          membership
marking :  row's marking set ⊆  my clearance set    containment
```
## 3. Data model

### 3.1 Choosing the many-to-many shape — two options, one decision

Both options store the same thing (a set of markings per row). They differ only on **where** that set lives.

| | Shape | Read predicate |
|---|---|---|
| **Option 1** | one join table per marked table (`assets_markings`, …) | correlated anti-join |
| **Option 2** | `marking_ids text[]` column on the marked table | local `<@` containment test |

#### Option 1 — join table

```sql
assets_markings(asset_id, marking_id)                        PK (asset_id, marking_id)
asset_groups_markings(asset_group_id, marking_id)            PK (asset_group_id, marking_id)
secret_references_markings(secret_reference_id, marking_id)  PK (secret_reference_id, marking_id)
```

| ✅ Pros | ❌ Cons |
|---|---|
| Real FKs both sides → `ON DELETE CASCADE`, no orphans possible | One migration per marked table |
| Composite PK is exactly the anti-join access path (good plans) | Cannot mark tables whose PK is composite (relationships) |
| Plain JPA `@ManyToMany` + `@JoinTable` | Cross-entity queries need a `UNION` |

#### Option 2 — `marking_ids text[]` column *(chosen for the PoC)*

```sql
ALTER TABLE assets ADD COLUMN marking_ids text[];
CREATE INDEX assets_marking_ids_idx ON assets USING GIN (marking_ids);
```

```sql
-- the whole predicate, no join:
COALESCE(t.marking_ids, '{}') <@ COALESCE(string_to_array(current_setting('app.current_markings', true), ','), '{}')
```

`<@` is "is contained by", which **is** the AND semantics: *every* marking on the row must be in my
clearance. An unmarked row is `'{}'`, and `'{}' <@ anything` is true, so it stays visible for free; with no
clearance the right side is `'{}'` and any marked row is denied.

| ✅ Pros | ❌ Cons |
|---|---|
| No join at all — same cost class as the tenant check | No referential integrity: a garbage id is accepted, a deleted definition leaves a dangling id |
| Works on relationships unchanged (composite PK irrelevant) | `<@` is rarely served by GIN — must be measured, not assumed |
| Onboarding = `ADD COLUMN` + index; markings die with the row | No per-marking audit trail (array mutation rewrites the column) |
| Proven pattern here (`assets.asset_ips` is already `text[]`) | Only viable as the **sole** store; alongside join tables it would need trigger-syncing |

> **Do not "optimise" this into `NOT (marking_ids && :lacked)`.** The lacked set is *all markings minus
> mine*, so a definition created after the scope was resolved is absent from it and its rows become
> **visible** — fail-open. The `<@` held-set form fails closed. Take the correct form; the arrays are tiny.

#### Decision: **Option 2** for the PoC

Chosen because the read predicate is a local column test and it marks relationships unchanged. The price is
the lost FK, paid back with machinery the design already requires:

- **Insert side is free.** The §4.3 write guard already loads each marking definition to answer *"do you hold
  it?"*, so existence is verified as a by-product — a garbage id cannot pass the service layer.
- **Delete side is explicit.** See §3.2.

### 3.2 Deletion without a cascade

Option 2 has no FK, so nothing cascades. Three cases must be distinguished, and only one is a problem:

| What is deleted | What happens to the markings | Needs work? |
|---|---|---|
| A **marked row** (asset, asset group, secret reference) | the array dies with the row | ❌ nothing — simpler than Option 1 |
| A **marking removed from a row** (declassification) | the id is dropped from that row's array | ❌ nothing beyond the write guard — see below |
| A **marking definition** | `groups_markings` grants cascade, but `marking_ids` arrays keep the dead id | ✅ scrub + cache eviction |

**Removing a marking from an asset needs nothing extra**, and the reason is worth stating because it looks
like it should. The predicate is `is_marking_set_allowed(marking_ids)`: the row's array is a function
*argument*, re-read on every query, while only the *clearance* lives in the cached GUC. So
`AssetMarkingsService.updateAssetMarkings` deliberately does **not** evict the clearance cache — evicting on a
row write would be a no-op that *looks* like protection. Eviction belongs only where a **clearance shrinks**
(group membership, grant removal, definition delete, order lowered).

Two consequences fall out for free:
- **Self-lockout is impossible.** The write guard enforces `requested ⊆ your clearance`, and a row is visible
  iff `row_markings ⊆ clearance` — so you can always still read what you just marked.
- **Declassification is the only direction worth auditing.** Adding a marking narrows visibility; removing one
  widens it, so removals are logged (`logDeclassification`).

**Deleting a definition permanently hides data — this is a real bug, not untidiness.** Once the definition
row is gone, its id survives inside `marking_ids` arrays. The read predicate is pure set membership against
the GUC and never consults `marking_definitions`, so it cannot tell *"deleted"* from *"exists but you do not
hold it"* — both simply deny. And the deleted id can never re-enter anyone's clearance, because its grants
cascaded away with it. Result: **every row carrying that id becomes invisible to the entire platform,
permanently, with no error raised** — not even to an admin holding every marking that still exists.

```
clearance = m_green,m_warm
  m_green   (held)              -> allowed
  m_red     (exists, not held)  -> denied
  m_deleted (orphan)            -> denied     <- indistinguishable from m_red
```

#### What the PoC code does today

`MarkingDefinitionService.delete(id)` performs the hard delete and evicts every cached clearance. The
`marking_ids` scrub is **deliberately not there yet**: no table is marking-activated, so no array can hold
the id. The scrub lands with activation (step 3), generated from the same `MarkedTables` registry that drives
the inspector, so it cannot drift out of sync with the allowlist:

```java
for (MarkedTable t : markedTables.all())
  jdbc.update("UPDATE " + t.table() + " SET marking_ids = array_remove(marking_ids, ?)", markingId);
```

**And this is the cost Option 2 pays for losing the FK.** `ON DELETE CASCADE` would have touched only the
rows that actually reference the marking, via an index. The scrub instead issues one `UPDATE` **per marked
table**, and each one is a **full-table scan and rewrite** of every row it touches — the array is a plain
column, so Postgres has no cheap way to find "rows containing this id" unless the GIN index is used, and it
must still rewrite each matching row. Cost therefore grows with the *size of every marked table*, not with
the number of rows carrying the marking. On `assets` — the largest and most-joined of the three — that is a
noticeable write burst inside the delete transaction.

Three mitigations, in order of preference:

1. **Do not hard-delete**: archive the definition instead. This works because the row still exists, so
   the id in `marking_ids` is never dangling — but it only works under one **load-bearing condition**: the
   clearance resolver must keep seeing archived definitions. Clearance is computed from
   `marking_definitions` (the tenant scale) joined with `groups_markings` (the grants), so as long as the
   archived row and its grants survive, whoever held it still holds it and the marked rows stay readable.
   An archive that also strips the grants — or a resolver that filters on `archived = false` — reproduces
   the hard-delete bug exactly: the id becomes unholdable and the rows vanish platform-wide. Archiving
   changes the id from *unholdable* to *still holdable but no longer assignable*; that is the whole trick.
2. **Narrow the scan** with `WHERE marking_ids @> ARRAY[?]` so the GIN index can select candidate rows
   (`@>` is the containment direction GIN serves well), instead of rewriting blindly.
3. **Move it off the request** — run the scrub asynchronously if hard delete is ever shipped for large
   tenants, accepting a short window where the id is dangling.

```mermaid
sequenceDiagram
    actor A as Admin
    participant API as MarkingDefinitionApi
    participant SVC as MarkingDefinitionService
    participant REPO as MarkingDefinitionRepository
    participant PG as PostgreSQL
    participant CACHE as MarkingClearanceCacheManager

    A->>API: DELETE /markings/{markingId}
    API->>SVC: delete(markingId)
    SVC->>REPO: findById + delete
    REPO->>PG: DELETE FROM marking_definitions
    PG-->>REPO: groups_markings grants cascade (FK)
    Note right of PG: no cascade to marking_ids arrays<br/>(no FK under Option 2)
    SVC->>PG: UPDATE {marked table} SET marking_ids = array_remove(marking_ids, id)
    Note right of SVC: not implemented yet — lands with activation (step 3)<br/>one UPDATE per marked table = costly
    SVC->>CACHE: evictAll()
    Note right of CACHE: grants are gone, but derived<br/>clearances are still cached
    SVC-->>API: done
    API-->>A: 204 No Content
```

The archive-rather-delete policy above is what avoids needing the scrub at all.

### 3.3 Concrete schema

```sql
-- Task 1 (prerequisite): marking definitions, tenant-scoped
marking_definitions(
  marking_id          varchar PK,
  marking_type        varchar   NOT NULL,   -- TLP, PAP, custom
  marking_definition  varchar   NOT NULL,   -- TLP:RED
  marking_order       int       NOT NULL,   -- 1..10, 10 = highest
  marking_color       varchar,
  tenant_id           varchar   NOT NULL REFERENCES tenants,
  UNIQUE (marking_definition, tenant_id)    -- composite, per multi-tenancy conventions
)

-- Task 2 (prerequisite): group clearance
groups_markings(
  group_id   varchar REFERENCES groups(group_id)                       ON DELETE CASCADE,
  marking_id varchar REFERENCES marking_definitions(marking_id)        ON DELETE CASCADE,
  PRIMARY KEY (group_id, marking_id)
)

-- Task 3 (this design): one marking-set column per marked entity table
ALTER TABLE assets              ADD COLUMN marking_ids text[];
ALTER TABLE asset_groups        ADD COLUMN marking_ids text[];
ALTER TABLE secret_references   ADD COLUMN marking_ids text[];

CREATE INDEX idx_assets_marking_ids ON assets USING GIN (marking_ids);
-- GIN serves "what is marked X?" (marking_ids @> ARRAY['X']).
-- The read predicate is `<@`, which GIN rarely serves; step 1.4 measures whether the index
-- is worth keeping on the read path or exists purely for admin/impact queries.
```

**Convention (load-bearing)**: the column is named `marking_ids`, type `text[]`, on the marked table itself.
Its *presence* is what lets `MarkingFilteringConfig` derive `MarkedTable` from `information_schema` instead
of a hand-maintained mapping — exactly how tenant tables are derived from `tenant_id`. `MarkedTable` therefore
needs **no PK column, no join table, no FK column**, which is why composite-PK tables and relationships work
unchanged.

**Tenant scoping**: `marking_ids` lives on the marked row, which is already tenant-scoped. There is no second
table to confine.

#### `groups_markings` is a clearance **grant**, not a marking attachment

| Relation | Question it answers | Role |
|---|---|---|
| `groups_markings(group_id, marking_id)` | *What can members of this group see?* | clearance **grant** — an input to authorization (Task 2) |
| `groups.marking_ids` | *Who is allowed to see this group?* | marking **attachment** — an output of authorization |

**`groups_markings` stays a join table under Option 2.** It is read by the Java resolver (groups → markings →
ordinal expansion, §2.2), never by the SQL predicate, so denormalising it buys nothing on the hot path; and as
authorization data it wants real FKs, keeping a genuine `ON DELETE CASCADE`.

Marking the `groups` table itself, if ever wanted, is just `ALTER TABLE groups ADD COLUMN marking_ids text[]`
— sitting **beside** `groups_markings`, not replacing it.


## 4. How to adjust Tenant API v2 to fit Marking

### 4.1 Class diagram

```mermaid
classDiagram
    class ScopeDimension {
      <<interface>>
      +name() String
      +appliesTo(table) boolean
      +predicateFor(table, alias) String
    }

    class TenantDimension {
      -tables : ScopedTables
      +predicateFor() String  
    }

    class MarkingDimension {
      -tables : MarkedTables
      +predicateFor() String  
    }

  class StatementInspector {
    <<Spring>>
    +inspect
  }
    class ScopeStatementInspector {
      -dimensions : List~ScopeDimension~
      +inspect(sql) String
      -predicatesFor(table, alias) String
      -rewriteUpdate()
    }

    class ScopeFilteringConfig {
      <<Configuration bean>>
      +tenantDimension
      +markingDimension
      +scopeStatementInspector
    }

    class ScopeCtx {
      +tenants() TxCtx
      +markings() MarkingCtx
    }

    class MarkingCtx {
      <<sealed>>
      +toGuc() String
    }

    class MarkingScopeResolver {
      +resolve(user) MarkingCtx
    }

    class MarkingClearanceCache {
      +visibleMarkingIds(userId) Set~String~
      +evictOnGroupOrDefinitionChange()
    }

    class TenantScopeTransactionAspect {
        <<@Aspect>>
      +applyScope(JoinPoint)
    }

    
    ScopeFilteringConfig --> ScopeStatementInspector : factory
    ScopeDimension <|.. TenantDimension
    ScopeDimension <|.. MarkingDimension
    StatementInspector <|-- ScopeStatementInspector
    ScopeStatementInspector --> ScopeDimension

    TenantScopeTransactionAspect --> ScopeCtx
    ScopeCtx --> MarkingCtx
    MarkingScopeResolver --> MarkingClearanceCache
    MarkingScopeResolver --> MarkingCtx

    note for ScopeStatementInspector "This is the class responsible for all SQL rewrite. \nHanding both dimention Tenant and Marking filtering at READ"
    style ScopeStatementInspector fill:#fff59d,stroke:#b28900
    style ScopeDimension fill:#fff59d,stroke:#b28900
    style TenantDimension fill:#fff59d,stroke:#b28900
    style MarkingDimension fill:#fff59d,stroke:#b28900
    style TenantScopeTransactionAspect fill:#fff59d,stroke:#b28900
    style ScopeCtx fill:#fff59d,stroke:#b28900
    style MarkingCtx fill:#fff59d,stroke:#b28900
    style MarkingClearanceCache fill:#fff59d,stroke:#b28900
    style MarkingScopeResolver fill:#fff59d,stroke:#b28900
```

The `ScopeDimension` interface is the whole generalization: the inspector stops knowing *what* a tenant or
a marking is and only asks each dimension for a boolean SQL predicate on a table alias.

#### 4.1.1 What activating a table on marking requires — compared to tenant v2

Activating a table on tenant v2 costs the developer **two** things at every entry point: the method must be
`@Transactional`, **and** it must take a `TxCtx` parameter. Marking needs the first but **not** the second.

| | tenant v2 | marking |
|---|---|---|
| `@Transactional` on the entry point | ✅ required | ✅ **required** |
| a `Ctx` parameter on the REST method | ✅ required (`TxCtx`) | ❌ **not required** |

**Why `@Transactional` is still required.** The scope travels as a *transaction-local* Postgres setting
(`set_config(…, true)`). The aspect that writes it runs `@Before` a `@Transactional` method, i.e. *inside*
an already-open transaction. Outside a transaction there is nothing to attach the setting to, the GUC stays
unset, and every marked row is hidden — fail-closed, but silently. This is identical to tenant v2 and is not
negotiable.

**Why no `MarkingCtx` parameter is needed.** `TxCtx` is a parameter because a tenant scope is a *caller
choice*: a user who belongs to three tenants must say which one they are acting in (`X-Tenant-Ids`, a path
variable, or a job passing one explicitly). It is not derivable from the authenticated principal, and v2's
whole correction over v1 was to stop smuggling that choice through a thread-local.

A **clearance is not a choice**. There is no "act at TLP:GREEN today" selector: it is a pure function of
*(authenticated user, selected tenant)* → groups → markings → expanded id set. Everything that function
needs is already at hand when the aspect runs — the principal in the security context, the tenant in the
`TxCtx` argument that is *already there*. So the aspect resolves it itself:

```
ScopeTransactionAspect#applyScope(joinPoint):
  1. an explicit MarkingCtx argument, if the method has one   → run-as / impersonation, tests
  2. otherwise MarkingScopeResolver.resolve(principal, txCtx) → every HTTP handler, no signature change
  3. otherwise MarkingCtx.Missing                             → empty GUC, fail-closed
```

The practical consequence for step 3: **activating `assets` on marking changes no controller signature.**
The endpoints already carry `TxCtx` and `@Transactional` from tenant v2, and that is enough.

Note what this list does **not** cover. The aspect triggers on `@Transactional`, and background code is
forbidden from using `@Transactional` — it opens transactions through the `TenantScopedTransaction`
primitive instead. So none of the three branches ever runs off the request thread, and branch 3 is not the
background story. §4.1.3 is.

#### 4.1.2 The two dimensions are independent, not layered

`ScopeStatementInspector` holds a `List<ScopeDimension>`. For each table it asks **every** dimension two
questions — *do you cover this table?* and if so *what is your predicate?* — and `AND`s whatever comes back.
No dimension knows the others exist.

So the two allowlists, `openaev.tenant.active-tables` and `openaev.marking.active-tables`, are genuinely
independent, and all four combinations are legal:

| tenant v2 | marking | Result |
|---|---|---|
| ✅ | ✅ | `can_access_tenant(t.tenant_id) AND is_marking_set_allowed(t.marking_ids)` |
| ✅ | ❌ | today's behaviour, unchanged |
| ❌ | ✅ | **marking alone** — the table keeps tenant v1 `@Filter`, or is not tenant-scoped at all |
| ❌ | ❌ | inert |

Row 3 is the one worth stating explicitly, because it is not obvious and it is load-bearing for the rollout:
**a table can be marking-activated while its tenant isolation is still v1 `@Filter`, or while it has no
tenant dimension at all.** Marking does not wait for the v2 tenant migration to reach a table. The v1
coexistence case holds specifically — the marking predicate adds **zero bind parameters**, so it
cannot disturb the positional placeholders a Hibernate `@Filter` relies on.

The one place the dimensions *do* meet is the resolver, not the inspector: which markings a user holds
depends on their groups **in the selected tenant**, so `MarkingScopeResolver` reads the tenant from `TxCtx`.
That is a dependency of the clearance *computation*, not of the SQL rewrite.

#### 4.1.3 Background jobs — worked example: `InjectsExecutionJob`

Take the question directly: Quartz fires `InjectsExecutionJob.execute()`, it picks up an inject whose target
is a **marked asset**. Does the job see that asset?

**There is no user, so there is no clearance to derive.**

**The answer: the job runs at system clearance, assigned by the primitive.** With the change specified in
[step 2.3 of the plan](./implementation-plan-option-c.md) — `setScope` writing `app.current_markings`
alongside `app.current_tenants`, defaulting to all markings of the tenant in scope and refusing to open
unset — `tenantTx.execute(TxCtx.forTenant(t), …)` also writes *every marking of tenant `t`* into
`app.current_markings`. `ARRAY['tlp-red'] <@ ARRAY['tlp-clear','tlp-green','tlp-amber','tlp-red',…]` → true.
The job sees every asset of its tenant, exactly as it does today, and **activating `assets` is a no-op for it**.

> ⚠️ Note the asymmetry with tenant: the job is *narrowed* to one tenant because that is a real boundary it must
> respect, but it is *widened* to all markings because marking is a boundary between **users**, and a
> scheduler is not a user. It is the `isAdminOrBypass()` equivalent, expressed in the primitive.

A second reason to **assign** rather than derive here: `InjectsExecutionJob` runs on the shared
`ForkJoinPool.commonPool`, which borrows the calling thread (see the javadoc on `executeInTenant`). Any
principal-derived clearance would be a thread-local, and a borrowed thread could hand the job whatever
clearance the borrower happened to have — non-deterministic, and occasionally *less* than the job needs.
Explicit assignment removes the question.

> ⚠️ [OUT OF SCOPE of this POC] **But the job also writes.** It creates `InjectExpectation` rows against those
> marked assets, and those rows are later read by real users on the HTTP path. Seeing every asset obliges it
> to **re-apply the marking on the way out** — an expectation naming a `TLP:RED` endpoint must itself be
> `TLP:RED`, or the job has laundered the marking through a table nobody thought to activate. This
> generalises, and it is bigger than it looks: marking propagates transitively along every
> write the system makes on a marked row. Elasticsearch is the other instance — the ES sync legitimately indexes every
> marked row, so the **index** must carry `marking_ids` and the query side must filter on it. Same for
> anything that emails, exports or renders a digest.


### 4.2 Sequence — read path (transparent)

```mermaid
sequenceDiagram
    actor U as User
    participant API as Endpoint API
    participant ARG as TxCtxArgumentResolver
    participant MSR as MarkingScopeResolver
    participant ASP as ScopeTransactionAspect
    participant PG as Postgres
    participant INS as ScopeStatementInspector

    U->>API: GET /api/endpoints/search
    API->>ARG: resolve the TxCtx parameter (unchanged, tenant only)
    ARG-->>API: TxCtx(tenants)
    API->>ASP: @Transactional entered
    ASP->>PG: set_config('app.current_tenants', …, true)
    Note over ASP: no MarkingCtx argument ⇒ derive it (§4.1.1)
    ASP->>MSR: resolve(principal, txCtx)
    MSR->>MSR: groups → markings → max order per type<br/>→ expand to all marking ids ≤ max
    MSR-->>ASP: MarkingCtx(id1,id2,id3)
    ASP->>PG: set_config('app.current_markings', …, true)
    API->>PG: repository query (unchanged code)
    Note over INS: Hibernate emits SQL
    INS->>INS: rewrite: AND can_access_tenant(t.tenant_id)<br/>AND is_marking_set_allowed(t.marking_ids)
    INS->>PG: filtered SQL
    PG-->>API: rows in tenant AND fully within clearance
    API-->>U: 200 (over-clearance rows do not exist → 404 on direct GET)
```

### 4.3 Sequence — write path (explicit guard)

**Reads are filtered structurally; writes are guarded explicitly.** The rewrite answers *"which row am I
touching?"*. It cannot answer *"which value am I writing?"* — the predicate tests the row's **pre-image**,
and the markings being written are in the `SET`/`VALUES` clause, which no `WHERE` can see.

The attack this leaves open, in one line: a `TLP:GREEN` user loads a `TLP:GREEN` asset (✅ visible), sets it
to `TLP:RED`, and the `UPDATE … WHERE is_marking_set_allowed(marking_ids)` still passes — because the row is
*still GREEN at that instant*. After commit it is invisible to them and to every colleague at their level,
and they cannot undo it.

> This is not a gap peculiar to marking. Tenant v2 has the same one and resolves it the same way: the
> inspector guards `INSERT … SELECT` only, and explicitly leaves ordinary ORM writes to the application
> (*"VALUES inserts cannot be distinguished from ORM-generated ones at the SQL level, so their scope
> assignment stays an application concern"* — `ScopeStatementInspector#rewriteInsert`). The marking guard
> therefore belongs in the service layer, on the write path below.

Three independent layers apply on write:

| Layer | Question | Mechanism | Failure |
|---|---|---|---|
| 1 | May I modify this entity type? | `@AccessControl(ENDPOINT, WRITE)` | 403 |
| 2 | May I manage marking assignments at all? | `ASSIGN_MARKING` capability — `DELETE_MARKING_ASSIGNMENT` for removals | 403 |
| 3 | May I write *these* markings? | `MarkingEscalationValidator` clearance check | 403 |

Layer 2 comes from the **"Assign marking"** capability chain (Task 1/US1 AC1) — `Access marking assignment`
→ `Assign marking` → `Delete marking assignment` — independent from the "Marking definitions" chain. Per
AC4, `BYPASS` overrides layers 2 and 3.

The check itself is one containment test, the same one the read uses: **every marking being written must be
one the user holds.** Two properties fall out of that single rule, with no special cases:

- **No self-lockout.** A user can never raise a row above their own clearance, so they can never make a row
  invisible to themselves. Worth an explicit test.
- **Declassification is allowed.** Lowering or removing a marking yields a subset of the clearance, so it
  passes. That is correct — a `TLP:GREEN` user may mark a `TLP:GREEN` asset `TLP:CLEAR` — but it *widens*
  visibility, so it is gated by the separate `DELETE_MARKING_ASSIGNMENT` capability and audited.

```mermaid
sequenceDiagram
    actor U as User
    participant API as Endpoint API
    participant AOP as AccessControlAspect
    participant SVC as EndpointService
    participant V as MarkingEscalationValidator
    participant DB as Repository

    U->>API: PUT /api/endpoints/{id} (marking_ids = [TLP:RED, PAP:AMBER])
    API->>AOP: @AccessControl(ENDPOINT, WRITE)
    AOP-->>API: allow / 403
    API->>SVC: update(id, input)
    Note over SVC: the row was already clearance-filtered<br/>on load — over-clearance ⇒ 404
    SVC->>V: assertCanAssignMarkings(user, added, removed)
    alt lacks ASSIGN_MARKING (or DELETE_MARKING_ASSIGNMENT for removals)
        V-->>SVC: 403 MISSING_CAPABILITY
    else an added marking is above the user's clearance
        V-->>SVC: 403 UNHELD_MARKING
    else allowed (or BYPASS)
        opt marking removed or lowered
            SVC->>SVC: audit declassification (actor, entity, before → after)
        end
        SVC->>DB: write marking_ids
        DB-->>SVC: 200 OK
    end
```


### 4.4 SQL function and the generated predicate

Both schema shapes of §3.1 implement the **same** invariant — *a row is visible when every marking it
carries is one I hold* — and both must reproduce this truth table, which is shape-independent. It is the
acceptance criterion for either implementation.

Clearance **TLP:WARM** (order 2, between GREEN and RED), expanded in Java to `m_clear,m_green,m_warm`:

| asset | markings | visible | why |
|---|---|---|---|
| `a1` | `m_green` | ✅ | GREEN ≤ WARM |
| `a2` | `m_red` | ❌ | RED > WARM |
| `a3` | `m_green` + `p_red` | ❌ | GREEN held, but `p_red` is not — **AND**, not OR |
| `a4` | *(none)* | ✅ | nothing to lack ⇒ visible for free |

`a3` and `a4` are the two rows that separate a correct implementation from a plausible-looking one. The
naive positive form — *"keep rows carrying a marking I hold"* — gets **both** wrong: it shows `a3` (one held
marking was enough ⇒ a PAP:RED asset leaks to a TLP:WARM user) and hides `a4` (an unmarked asset becomes
invisible to everyone). Every predicate below is built around that.

> **Step 1 happens in Java, before any SQL runs.** "WARM ≥ GREEN" is expanded once per request into
> `m_clear,m_green,m_warm` and written to `set_config('app.current_markings', …, true)`. After that,
> ordinality no longer exists: SQL only ever does set membership (§2.2).

---

#### 4.4.1 Option 2 — `marking_ids text[]` column *(chosen, implemented)*

```sql
CREATE OR REPLACE FUNCTION is_marking_set_allowed(row_marking_ids text[])
RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT COALESCE(row_marking_ids, '{}'::text[])
         <@ COALESCE(
              string_to_array(NULLIF(current_setting('app.current_markings', true), ''), ','),
              '{}'::text[])
$$;
```

Generated by `MarkingDimension#readPredicate("assets", "t")`:

```sql
is_marking_set_allowed(t.marking_ids)
```

That is the entire predicate. Postgres's `<@` ("is contained by") **is** the AND semantics, so all four rows
of the truth table fall out of one operator with no special cases.

**Both `COALESCE`s are load-bearing**, and each guards a different row of the table:
- `NULL <@ x` is `NULL`, which a `WHERE` drops — without the left one, an **unmarked row would vanish**
  (`a4`).
- `x <@ NULL` is also `NULL` — without the right one, a user with **no clearance would see nothing at all**,
  including unmarked rows. Fail-closed here means "no marked row", not "no row".

> 🔴 **Do not "optimise" this to `NOT (marking_ids && :lacked)`.** It is GIN-friendly and tempting, but
> `:lacked` is *"all markings minus mine"*, computed when the clearance was resolved. A marking definition
> created **after** that moment is in neither set, so rows carrying it match neither side and become
> visible — it **fails open**. Pinned by a regression test (`FailOpenTrap`).

**Measured, not assumed** (200k rows, real `EXPLAIN (ANALYZE)`, step 1.4):

```
is_marking_set_allowed(marking_ids)   →  94.9 ms   Parallel Seq Scan
  Filter: (COALESCE(marking_ids,'{}') <@ COALESCE(string_to_array(NULLIF(current_setting(…),''),','),'{}'))
marking_ids <@ '{tlp_green}'::text[]  →  43.6 ms   Seq Scan
```

The `Filter:` line is the finding: the function is a single-statement SQL function, so the planner
**inlines** it and sees a real `<@` operator rather than a black box. Residual cost ≈ **0.25 µs/row**, all of
it GUC parsing — the same price tenant v2 already pays.

#### 4.4.2 Option 1 — join table + anti-join *(fallback of record)*

Retained because it is the shape to fall back to if Option 2's missing FK (§3.2) proves unacceptable. It was
implemented and green before the flip (commit `7056fd6a32`), so this is a description of working code, not a
sketch.

```sql
CREATE OR REPLACE FUNCTION is_marking_missing(row_marking_id text)
RETURNS boolean
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT CASE
    -- no clearance ⇒ every marking is missing ⇒ every MARKED row denied (unmarked rows unaffected)
    WHEN current_setting('app.current_markings', true) IS NULL
      OR current_setting('app.current_markings', true) = '' THEN true
    ELSE COALESCE(
           NOT (row_marking_id = ANY (
             string_to_array(current_setting('app.current_markings', true), ','))),
           true)
  END
$$;
```

```sql
NOT EXISTS (SELECT 1
              FROM assets_markings t_mk
             WHERE t_mk.asset_id = t.asset_id
               AND is_marking_missing(t_mk.marking_id))
```

**The function is named — and returns — negatively, on purpose.** `is_marking_missing(m)` is true when the
caller does *not* hold `m`. A positively named `can_access_marking()` reads like a row-level visibility test,
which invites `EXISTS (… AND can_access_marking(…))` — precisely the positive form that breaks `a3` and
`a4`. Named this way it cannot be mistaken for one, and the generated SQL loses a double negative.
Fail-closed therefore means returning `true`, and `COALESCE(…, true)` is load-bearing: `NULL = ANY(…)` is
`NULL`, which would drop the row from the inner `WHERE`, make `EXISTS` false, and expose the marked row.

**Why "anti-join".** The inner query hunts for *problems*, not permissions: it keeps only the markings on the
row that the caller **lacks**. Zero left over ⇒ `NOT EXISTS` ⇒ visible. Postgres names the node literally and
does not run it as a per-row loop — it scans the join table once, keeps the missing rows, hashes them, and
subtracts them from `assets` in a single pass:

```
 Hash Right Anti Join
   Hash Cond: (a_mk.asset_id = a.asset_id)
   ->  Seq Scan on assets_markings a_mk
         Filter: CASE WHEN (current_setting('app.current_markings', true) IS NULL OR … = '')
                      THEN true ELSE COALESCE((marking_id <> ALL (string_to_array(…, ','))), true) END
   ->  Hash
         ->  Seq Scan on assets a
```

Two costs Option 2 does not have: the predicate must know the marked table's **primary key** (so a
composite-key relationship table needs a special case, §3.1), and the join table is itself a table the
inspector must be told to filter — otherwise reading a row's markings leaks the markings of rows the caller
cannot see.

