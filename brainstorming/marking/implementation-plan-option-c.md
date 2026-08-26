# Option C — implementation plan

**Design doc**: [tech-design-option-c.md](./tech-design-option-c.md) — the mechanism and its rationale
**Parent doc**: [tech-design.md](./tech-design.md) — Option C
**Decision of record**: [ADR-007](../../adr/ADR-007-Marking-based-access-control.md)
**Status**: in progress — steps 1.1–1.4 and 2.1–2.4 delivered; step 3 next

> **Reference convention.** `§N.M` always refers to a section of
> [tech-design-option-c.md](./tech-design-option-c.md); `Qn` refers to its §6 Decisions table.
> Plain numbers such as *step 2.2* or *5.6* are steps of the plan below.

---

## 1. Delivery plan

Feature flag: `marking-isolation`. Property `openaev.marking.active-tables` empty by default.

**Two tracks run in parallel, then converge:**

```
Track 1 (inspector)   1.1 ──► 1.2 ──► 1.3 ──► 1.4 ─┐
                      no dependencies              ├──► 3 (activation) ──► 4 ──► 5
Track 2 (channel)     2.1 ──► 2.2 ──► 2.3 ──► 2.4 ─┘
                      external teams
```

**Status at a glance**

| Step | | Commit |
|---|---|---|
| 1.1 `ScopeDimension` extraction | ✅ done | `4100dac` |
| 1.2 marking SQL function | ✅ done, superseded by 1.4 | |
| 1.3 `MarkingDimension` + allowlist | ✅ done, reshaped by 1.4 | |
| 1.4 schema-shape spike → **Option 2 adopted** | ✅ done | `a0dcbd471c` |
| 2.1 `marking_definitions` schema + CRUD + UI | ✅ done | `3033c7759b` |
| 2.2 `MarkingCtx` + resolver + cache + eviction wiring | ✅ done | |
| 2.3 marking scope written on both transaction paths | ✅ done | |
| 2.4 `groups_markings` write path (assign endpoint + escalation guard) | ✅ done — lifted the step-3 gate | |
| 3 activate `assets` | ⬅️ **next** | |
| 4 `activate-marking-table` skill — *the PoC's real deliverable* | pending | |
| 5 go-live hardening | out of PoC | |

### Step 1 (Track 1) — Inspector (starts immediately, no dependencies)

**1.1 — ✅ DONE** (commit `4100dac`) — Extract `ScopeDimension`; refactor `TenantStatementInspector` →
`ScopeStatementInspector`, tenant-only, zero behaviour change. *(deps: none)*
   - **Context**: pure refactor; `TenantDimension` wraps today's `TenantTables` + `can_access_tenant` call.
   - **DoD**: the entire existing tenant test suite green, **unmodified**. No marking test in this commit.
     Land it on a clean base, before `TxCtx` is reshaped by 2.3, so a bisect stays readable.
   - **Delivered**: `TenantStatementInspector` is now an 18-line subclass, so all existing wiring and the
     79 tenant inspector tests are untouched. `ScopeStatementInspectorTest` pins the composition contract.

**1.2 — ✅ DONE, ⚠️ superseded by 1.4 — `is_marking_missing()` migration** — Flyway Java migration
mirroring `V5_27`. Belongs to the **Option 1 fallback**; under Option 2 it is replaced by
`is_marking_set_allowed(text[])`, which takes the whole set instead of one id. *(deps: none)*
   - **Context**: statement inspector → `is_marking_missing(am.marking_id)` → reads `app.current_markings`.
   - **DoD**: integration test on the truth table (GUC unset / GUC empty / id in set / id out of set).
   - **Delivered**: `V6_20260825090000000__Add_is_marking_missing_function` + `IsMarkingMissingFunctionTest`
     (6 cases, incl. exact-match-only and a null marking id).

**1.3 — ✅ DONE, ⚠️ `MarkedTable` reshaped by 1.4 — `MarkingDimension` +
`openaev.marking.active-tables` (empty) + `MarkedTable` schema derivation.** *(deps: 1.1, 1.2)*
   - **DoD**: with an empty allowlist, emitted SQL is byte-identical to before (pinned by test); unknown
     table name in the property fails startup. **Plus the hypothesis test**: a fixture table + join table,
     GUC set manually via `set_config`, asserting unmarked-visible / in-clearance-visible /
     out-of-clearance-hidden / multi-marking-AND — no resolver, no product code, no Task 1 dependency.
   - **Delivered**: `MarkedTable` / `MarkedTables` / `MarkingDimension` / `MarkingFilteringConfig`, plus
     `ScopeFilteringConfig` which now owns the single Hibernate inspector composed of both dimensions
     (`TenantFilteringConfig` only contributes the `TenantDimension` bean). `MarkingDimensionTest` (12 cases)
     and `MarkingRewriteHypothesisTest` (6 cases on real rows, including a guarded UPDATE).
   - **Deviation from the sketch in §4.1**: derivation lives in its own `MarkingFilteringConfig` next to
     `TenantFilteringConfig` rather than in a single `ScopeFilteringConfig`; `ScopeFilteringConfig` is kept
     to the one thing that must be central — composing and installing the inspector.

> **Go/no-go gate**: ✅ **passed.** 1.1 and 1.3 landed with the tenant suite green and unmodified, and the
> hypothesis test proves the four M2M behaviours on real rows.

**1.4 — 🔴 NEXT — schema-shape spike: validate Option 2 (`marking_ids text[]`) before any activation work.**
*(deps: 1.3, blocks 3 and 4)*

§3.2 chose Option 2 on argument; 1.1–1.3 validated Option 1 on evidence. This step equalises them before a
single table is activated, because activating on the wrong shape is what makes the choice expensive.

   - **Build**: `is_marking_set_allowed(text[])` migration; a second `MarkingDimension` implementation whose
     `readPredicate` is the one-liner `is_marking_set_allowed(<alias>.marking_ids)`; `MarkedTable` degenerated
     to `(table, markingColumn)`.
   - **DoD** — port `MarkingRewriteHypothesisTest` to the `<@` predicate and assert:
     1. the **identical truth table** to §4.4 on real rows — the same four M2M behaviours, unchanged;
     2. **fail-closed** on GUC unset, GUC empty, `NULL` array and `'{}'` array;
     3. a **regression test that pins the fail-open trap**: demonstrate that the `&&`-on-lacked form leaks a
        row carrying a marking created mid-session, so nobody "optimises" into it later;
     4. composite-PK viability: the predicate applies unchanged to a fixture table with a two-column PK —
        the property Option 1 cannot deliver;
     5. `EXPLAIN (ANALYZE, BUFFERS)` for both predicates on seeded `assets`, with and without the GIN index,
        recorded in §5.5.
   - **Exit**: if all five hold, Option 2 is confirmed — drop 1.2's function, reshape `MarkedTable`, and
     proceed. **If any fail, revert to Option 1**, which is already built and green; only this step is lost.
   - *This is the cheapest moment to be wrong. After step 3 the cost includes a data migration; after step 4
     it also includes rewriting the skill and every table activated through it.*

### Step 2 (Track 2) — Scope channel (blocked on a **skimmed** Task 1 / Task 2)

> **Scope call**: the PoC needs a *clearance to read*, not a governed marking product. Task 1 and Task 2
> are therefore taken in a **skimmed** form: enough schema and plumbing to populate
> `app.current_markings`, and nothing else. Capabilities, RBAC and the assignment UX come back in step 5,
> once step 3 has proven the mechanism is worth building them on.

**2.1 — Skimmed prerequisites** — ✅ **DONE**
   - `marking_definitions` table + entity + **plain CRUD** (id, type, name, order, colour). TLP + PAP
     defaults seeded per tenant, both by migration and for tenants created later.
   - `groups_markings` join table. **The assign/unassign endpoint was deliberately NOT built here**:
     it is only consumed by 2.2, so it lands there, where a test can prove it does something.
   - **Explicitly NOT in this step**: the "Marking definitions" and "Assign marking" capability chains,
     a dedicated `@AccessControl` subject, RBAC beyond reusing `*_TENANT_SETTINGS`, the assignment UX.
     Deferred to 5.6.
   - **Frontend pulled forward from 5.6** at the maintainer's request: a CRUD screen under
     *Settings → Security → Marking definitions*. Reason: the vocabulary is what a human has to see to
     reason about step 3, and it is cheap — no new capability, no new permission subject.
   - **Tenant isolation is v2** (statement inspector + `can_access_tenant`), not v1 `@Filter`. A
     brand-new table has no legacy read paths, so there is nothing to migrate, and 2.3 composes
     cleanly on top.

   **Delivered**

   | Artefact | Note |
   |---|---|
   | `V6_20260825140000000__Add_marking_definitions.java` | both tables, composite unique `(marking_name, tenant_id)`, 9 seeds × tenant |
   | `MarkingDefinition` + `MarkingDefinitionRepository` | `TenantBase`, **no** `@Filter`, **no** `TenantBaseListener` |
   | `api/markings/` — Api, Service, Mapper, QueryHelper, Input, Output | every handler takes `TxCtx`; writes attributed via `TenantWriteScopeResolver` |
   | `MarkingDefinitionDependenciesManager` | seeds defaults for tenants created after the migration |
   | `application.properties` | `marking_definitions` appended to `openaev.tenant.active-tables` |
   | frontend `marking_definitions/` + `SecurityMenu` + route + 9 lang files | `Can I={MANAGE} a={TENANT_SETTINGS}` |
   | `MarkingDefinitionFixture`, `MarkingDefinitionComposer` | unique names — the 9 seeds per tenant make name reuse a collision |
   | `MarkingDefinitionApiTest` (14), `MarkingDefinitionHttpIsolationTest` (10) | isolation covers read/create/update/delete, each with a **positive and a negative** case |

   **DoD met**: 474 tests green across `Marking*`/`Scope*`/`Tenant*`, including the 29 tenant arch
   tests and the untouched 79-case `TenantStatementInspectorTest`. Verified live: an unscoped admin
   read returns the union across tenants (standard v2 semantic), a read carrying `X-Tenant-Ids`
   returns exactly that tenant's 9 markings.

   **Two findings worth carrying forward**
   - `assertNameIsFree` originally used a single-result `findByName`, but the unique index is
     composite on `(marking_name, tenant_id)` — several tenants legitimately own `TLP:RED`. It was
     only safe *because* the inspector scopes the query, which means it would have thrown the moment
     the table was inactive. Changed to a `List`-returning finder. **This is a general trap for any
     v2 table**: a derived single-result finder on a per-tenant-unique column is a latent failure,
     and it belongs in the `activate-marking-table` skill (step 4.1).
   - A cross-tenant `PUT`/`DELETE` returns **404**, not the 2xx no-op the v2 skill describes, because
     the service does a scoped `findById` before writing. Both are safe; the divergence is worth
     stating explicitly so a future reader does not read 404 as a bug.

> **Why `groups_markings` is *not* itself marking-activated** — and stays that way until after step 3.
> It is the table the clearance is *derived from*. Marking-filtering it would make the resolver's own read
> depend on a clearance that does not exist yet: a circular dependency that fails closed to "no clearance",
> i.e. every marked row invisible to everyone. Activating it is a deliberate, separate decision (5.6), not
> an oversight.

**2.2 — `MarkingCtx` + `MarkingScopeResolver` + `MarkingClearanceCache`** — groups → markings → max order
per type → expanded id set. Pure Java, no SQL. *(deps: 2.1)*
   - **DoD**: unit tests for highest-wins across groups, per-type independence, empty clearance,
     admin/BYPASS; cache eviction test per §5.4.
   - 🔴 **Cache invalidation is a correctness requirement, not an optimisation detail.**
     `is_marking_set_allowed` is pure set containment against the GUC — it never consults
     `marking_definitions`. So a *stale, larger* cached clearance grants access that the current data no
     longer justifies. Verified:

     ```
     row marking_ids = {m_green,m_deleted}
     clearance = m_green,m_warm            -> is_marking_set_allowed = false  row hidden
     clearance = m_green,m_warm,m_deleted  -> is_marking_set_allowed = true   row VISIBLE
     ```

     Every **reduction** of a clearance must evict: user removed from a group, marking unassigned from a
     group, group deleted, marking definition archived/deleted, marking order lowered. During the window,
     users with warm caches see rows that users with cold caches do not. This applies under **every** schema
     option in §3.2 — it is a property of the GUC channel, not of the join table.
   - *Reusable under Options A/B — the one item that survives a fallback.*

   **Delivered**

   | Artefact | Note |
   |---|---|
   | `MarkingCtx` (`openaev-model`) | sealed `None` / `Restricted` / `All`, shaped like `TxCtx`; `all()` throws on `toGuc()` — an unresolved intention must not reach the channel |
   | `MarkingScopeResolver` | pure function, highest-order-per-type then expand downward; mirrors `TenantScopeResolver` |
   | `MarkingClearanceCacheManager` | `@AllowRawJdbc`; `findClearance` + `evict` / `evictForUser` / `evictForUsers` / `evictAll` |
   | eviction wiring | `TenantGroupService` + `PlatformGroupService` (`updateGroupUsers`, `delete`), `MarkingDefinitionService` (`update`, `delete`), and the assign endpoint from 2.4 |

   **Three corrections to the sketch above, each found by checking rather than assuming**

   - 🔴 **`evict(userId, tenantId)` was the wrong API for membership changes.** `Group` implements
     `DualScopeBase`, so a *platform* group (`tenant_id IS NULL`) grants markings into many tenants at
     once, and `users_groups` carries no tenant of its own. Evicting one tenant leaves the others
     stale — fail-open, the exact case eviction exists to prevent. `evictForUser(userId)` walks every
     tenant the user belongs to. `evict` also drops **both** bypass variants: making the caller name
     the right one lets a stale *larger* entry survive under the other key.
   - 🔴 **Asset marking updates need NO eviction** — this was in the list above and is wrong.
     `is_marking_set_allowed(row_marking_ids)` takes the row's array as a function **argument**; only
     the *clearance* lives in the GUC, and the row is re-read on every query. An evict on asset save
     would be a no-op that *looks* like protection, which is worse than none: the next reader assumes
     coverage. The reductions that genuinely need eviction are the ones that shrink a **clearance** —
     group membership, group deletion, grant removal, definition delete, **order lowered**.
   - `MarkingDefinitionService.update` therefore calls `evictAll()`, not a targeted evict: `order` and
     `type` are resolver *inputs*, not labels. Raising a marking's order pushes it above clearances
     that previously covered it, and the affected set is "everyone holding a grant of this type".

**2.3 — marking scope written on both transaction paths** — ✅ **DONE** — set
`app.current_markings` next to `app.current_tenants` on **both** paths. The GUC is written but nothing reads
it until step 3. *(deps: 2.2)*
   - **HTTP path** — no REST signature changes: the aspect derives the `MarkingCtx` itself (§4.1.1).
   - **Background path** — `TenantScopedTransaction` sets the marking GUC in the same `setScope` call that
     sets the tenant one, defaulting to all markings of the tenant(s) in scope.
   - 🔴 **This is the step that makes activation safe for existing jobs.** Skipping the background half does
     not fail a test — it makes every collector, executor and the ES sync silently read a subset once step 3
     lands.

   **Delivered**

   | Artefact | Note |
   |---|---|
   | `MarkingScopeSupplier` (`openaev-model`) | the seam — `openaev-model` has no dependency on `openaev-api` |
   | `HttpMarkingScopeSupplier` (`openaev-api`) | derives from the principal + clearance cache; unions per-tenant clearances for a multi-tenant scope |
   | `TenantScopeTransactionAspect` | writes both GUCs; `markingScopeFor()` fail-closed |
   | `TenantScopedTransaction.systemClearance(TxCtx)` | resolves `all()` into the tenants' explicit marking ids |
   | `TenantScopedTransactionMarkingScopeTest` (6, real DB) | mutation-checked: stubbing the clearance fails 2 |

   **The module-direction fork, and why the seam won.** The aspect lives in `openaev-model`; clearance
   derivation needs `UserService` and the clearance cache, both in `openaev-api`. Three options: a seam
   interface, a second aspect in `openaev-api`, or resolving in `TxCtxArgumentResolver`. The seam was
   chosen because the invariant worth protecting is that **one** component owns "what scope may this
   transaction have": a second aspect makes ordering load-bearing, and the argument resolver splits
   scope arrival across two routes. `ObjectProvider` keeps the supplier optional so model-only slices
   still start.

   **Tenant is passed, clearance is derived.** A caller legitimately chooses which tenant to act in;
   nobody chooses their own clearance. A marking REST parameter would be a forgettable security
   boundary. This is why there is no `X-Markings` header and never should be.

   🔴 **The asymmetry that is easiest to misread.** `TxCtx.Missing` → zero rows. `MarkingCtx.None` →
   **unmarked rows still visible** (the empty set is contained in the empty set). Fail-closed for
   marking means "see less", never "see nothing" and never "see more".

   ⚠️ **Known blast radius for step 3**: a `@Transactional` method with **no `TxCtx` parameter** writes
   *neither* GUC. Once a table is marking-active, such a transaction sees only unmarked rows — a silent
   partial narrowing, not an error. Documented in the aspect javadoc; step 3's inventory must account
   for it.

**2.4 — `groups_markings` write path** — ✅ **DONE** — the assign endpoint, deferred from 2.1 and again
from 2.2. *(deps: 2.2)*

> **Why this became a gate on step 3 rather than a step-5 nicety.** Until it existed, nothing wrote
> `groups_markings`, so *every* user resolved to `MarkingCtx.none()` and the clearance path was proven
> only against a stubbed `JdbcTemplate`. Step 3's DoD is "user cleared `TLP:GREEN` cannot read a
> `TLP:RED` asset" — undemonstrable with an empty grant table.

   | Artefact | Note |
   |---|---|
   | `PUT /api/tenants/{t}/groups/{g}/markings` | replace-the-whole-set; empty list revokes |
   | `Group.markings` `@ManyToMany` | the *read* path stays raw JDBC (OSIV/Hikari); the *write* path has no such constraint |
   | `MarkingEscalationValidator` | design **Q7**, pulled forward from 3.3 — it was needed here first |
   | `TenantGroupMarkingsApiTest` (6) | the three manual flows end-to-end; eviction mutation-checked |
   | `MarkingEscalationValidatorTest` (6) | incl. "higher implies lower is allowed" |
   | [`manual-testing-markings.md`](./manual-testing-markings.md) | curl walkthrough + the SQL to read a resolved clearance |

   **Escalation is checked against the resolved clearance, not the raw grants** — so a user holding
   `TLP:AMBER` *may* grant `TLP:GREEN`. They can already read every GREEN row, so granting it discloses
   nothing they could not disclose otherwise; forbidding it would be annoying rather than safer.

   🔴 **Finding: tenant isolation cannot be the only guard on a cross-tenant assignment.** The statement
   inspector rewrites *queries* — it cannot filter a read that is never **issued**, and an entity already
   in the persistence context is served from Hibernate's first-level cache. The independent guarantee is
   the escalation guard: a clearance is per tenant, so nobody holds another tenant's marking. **The two
   are not redundant, and this generalises to every v2 table** — it belongs in the step-4 skill.

   **Testing note carried into step 3**: the clearance read is raw JDBC and joins the test transaction,
   but Hibernate has not flushed. An isolation test must `entityManager.flush()` before asserting, or it
   measures the flush rather than the feature.

   **Deliberately still deferred to 5.6**: the `ASSIGN_MARKING` capability chain (Q8) — the endpoint
   currently reuses the group's own `WRITE` control — and the platform-group equivalent, since a platform
   group granting tenant markings is a cross-tenant question the PoC should not answer by accident.

### Step 3 — PoC activation on `assets`, the **first** marking-enabled table *(deps: 1.4, 2.3)*

`assets` is chosen first deliberately: it is the largest and most-joined of the three (§3.1 — Endpoint and
Security Platform share it), so it is the honest test of both the fail-closed blast radius (§5.1) and the
predicate cost (§5.5). If marking survives `assets`, the remaining tables are formalities. Per **Q10** it
does **not** need to be on tenant v2 first.

**3.1** — Migration: `ALTER TABLE assets ADD COLUMN marking_ids text[]` + GIN index (§3.3); map it on `Asset`
with `@Type(StringArrayType.class)`, reusing the pattern already used by `asset_ips` on the same entity.
   - *Fallback shape (if 1.4 failed): `assets_markings` join table + `ON DELETE CASCADE` both sides +
     `@ManyToMany` + the `marking_usage` view.*

**3.2** — Activate `openaev.marking.active-tables=assets`.
   - **DoD**: isolation tests — user cleared `TLP:GREEN` cannot read/search a `TLP:RED` endpoint (404, not
     403); unmarked endpoint visible to all; **multi-marking AND semantics** (`TLP:GREEN` + `PAP:RED` hidden
     from a TLP-only-cleared user); two users in different groups see different subsets; a multi-group user
     gets the highest clearance; tenant + marking compose correctly.

**3.3** — Wire `MarkingEscalationValidator` (already built in **2.4**) into the asset write paths, plus the
declassification audit event (§4.3).
    - **Reduced scope**: the validator and its tests exist; what is missing is the *asset-side* wiring —
      setting `marking_ids` on an asset must go through the same "you may not assign what you do not hold"
      check the group endpoint already uses — and the audit event.
    - 🔴 **Plus the invariant the group path does not need**: self-lockout. Marking an asset above your own
      clearance makes it invisible **to you**, immediately. Decide explicitly whether to forbid it or allow
      it with a warning; do not leave it to emerge.
    - **DoD**: unit + API tests for — 403 on over-clearance assignment; the self-lockout invariant; an audit
      event on every removal/downgrade and on nothing else. *(The capability-based layers of §4.3 land with
      5.6; until then the clearance check is the only guard.)*

### Step 4 — Capture the procedure as an AI skill, then prove it *(deps: 3)*

Step 3 is the only time anyone will have the whole activation procedure in their head. Capture it
immediately, the way `activate-tenant-table` captured the tenant equivalent.

**4.1 — Write `.github/skills/activate-marking-table/SKILL.md`**, mirroring the phase structure of
`activate-tenant-table`:

| Phase | Content |
|---|---|
| **0 — Eligibility gate** | Table is not on the **clearance-resolution path** (`groups`, `users_groups`, `groups_markings` — filtering these makes the resolver depend on a clearance it is computing, failing closed for everyone; §3.3); no raw-JDBC **writers** (§5.7 — the FK no longer guards id validity) or raw-JDBC readers that would bypass the inspector. **No PK constraint** — Option 2 marks composite-PK and relationship tables unchanged. *(Under the Option 1 fallback this phase must additionally reject composite-PK tables.)* |
| **1 — Inventory: reads** | Every read path: repository methods, native `@Query` joins, ES/OpenSearch reads (§5.2 — these bypass the inspector entirely), background jobs (§5.3), raw-JDBC `@AllowRawJdbc` sites |
| **1b — Inventory: downstream** | Which tables hold an **FK to this table** *and* denormalise any of its content (e.g. `injects_expectations.asset_id` + `inject_expectation_name`), and which **native aggregates** read them. Marking propagates transitively along every write the system makes on a marked row, and each aggregate is a place where "filtered rows" and "one number" disagree (§5.8). Produce the inventory — do **not** activate those tables; the per-viewer-vs-system aggregate decision is deferred to the next epic. Far cheaper to trace while activating than afterwards |
| **2 — RED** | Write the isolation test first: unmarked-visible / in-clearance / out-of-clearance 404 / multi-marking AND |
| **3 — GREEN: migration** | `ALTER TABLE <table> ADD COLUMN marking_ids text[]` + GIN index; map with `@Type(StringArrayType.class)`. One statement, no FK, no cascade — and **nothing global to regenerate**. *(Under the Option 1 fallback: `<table>_markings` + `ON DELETE CASCADE` both sides + reverse index + `@ManyToMany` + regenerate `marking_usage`.)* |
| **4 — Activate** | Add the table to `openaev.marking.active-tables` |
| **5 — Write guard** | Wire `MarkingEscalationValidator` into the table's write paths (§4.3) |
| **6 — Regression** | Full tenant suite + marking suite; measure the marking predicate against a pre-activation baseline (§5.5) |

   - **DoD**: the skill names the exact files, properties and test classes, and states its stop conditions.
     Register it in `AGENTS.md`.

**4.2 — Prove the skill and the "cheap to extend" claim** — run it on `asset_groups`, then
`secret_references`.
   - **DoD**: the *only* Java change per table is the migration class + the `@ManyToMany` mapping. If any
     repository or service read code needs editing, Option C's core promise is broken → stop and
     re-evaluate. `secret_references` is the valuable one: it is already tenant-v2-active, so it proves
     tenant and marking compose on a real table.

### Step 5 — Out of PoC, required for go-live

**5.1** ES/OpenSearch filtering (§5.2). **5.2** Background-job marking scope (§5.3). **5.3** Join-table
exposure review (§5.7). **5.7** Decide and implement derived-data propagation + the aggregate semantics (§5.8). **5.4** Frontend marking multi-select + bulk edit. **5.5** Performance
validation (§5.5). **5.6** **Un-skim Task 1 / Task 2**: the two capability chains (Q8), `@AccessControl`
on the marking CRUD, the assignment UX — and the separate decision on whether to marking-activate
`groups_markings` itself, now that the resolver's bootstrap order is settled.
---

## 2. PoC definition of done

The PoC is successful when **all** of the following hold:

1. A `TLP:RED` endpoint is invisible (search + direct GET) to a user whose group is cleared `TLP:GREEN`,
   **with no change to `EndpointRepository` or `EndpointService` read code**. *(step 3.2)*
2. An endpoint marked `TLP:GREEN` + `PAP:RED` is hidden from a user cleared `TLP:AMBER` only (AND
   semantics). *(step 3.2)*
3. The full pre-existing tenant isolation test suite is green, unmodified. *(step 1.1 — the go/no-go gate)*
4. Onboarding `asset_groups` costs exactly one `ALTER TABLE ... ADD COLUMN marking_ids` + one
   `@Type(StringArrayType.class)` mapping + one property entry. *(step 4.2)*
5. The activation procedure is reproducible by someone who did not do step 3, from the
   `activate-marking-table` skill alone. *(step 4.1, demonstrated by 4.2)*
6. `assets` search latency is within an agreed budget of the pre-marking baseline. *(§5.5, step 5.5)*

7. The `<@` predicate reproduces the §4.4 truth table on real rows, fails closed on every empty/null
   input, and applies unchanged to a composite-PK fixture table. *(step 1.4 — the schema-shape gate)*

**Earliest signal**: criterion 3 plus the step 1.3 hypothesis test validate the whole mechanism on a
fixture table, with the GUC set by hand — reachable without any Task 1 or Task 2 work at all, and the
point at which Option C is proven or abandoned. Criterion 7 then settles *which schema shape* it is built
on, still before any real table is touched.

**Explicitly out of the PoC** (deferred to 5.6, per Q12): the marking capability chains, `@AccessControl`
on the marking CRUD, the assignment UX, and any decision about marking-activating `groups_markings`
itself. The PoC answers "does transparent marking isolation work?", not "is marking well governed?".
