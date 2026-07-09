---
name: activate-tenant-table
description: >-
  Activates one table on multi-tenancy v2 (statement inspector + can_access_tenant),
  test-first, following the import_mappers pilot (PR #6255). Use when asked to switch
  a table from v1 @Filter isolation to v2, for HTTP-only tables. Covers eligibility
  gates, code-path inventory, TDD isolation tests, write attribution, the one-commit
  go-live, and the full regression pass.
---

# Activate a Table on Multi-Tenancy v2

Switch one table from v1 isolation (Hibernate `@Filter` on the entity) to v2
(SQL rewriting by `TenantStatementInspector` + the `can_access_tenant` function).

The reference implementation is the pilot, `import_mappers`, in PR #6255.
Every template in this skill points to a real pilot file. Open it and copy the
pattern; do not invent new mechanisms.

For the full mechanism (read path, write path, exact names), read the pilot
PR #6255 description once before starting, plus the javadoc of
`TenantStatementInspector`, `TenantWriteScopeResolver` and
`TenantScopeTransactionAspect`.

## Inputs

- The table name (e.g. `mitigations`) and its API class (e.g. `MitigationApi`).
- The activation issue (e.g. #6400). Its Context block names the mapped
  table(s) and the current state.

## Hard rules

These are not style preferences. Each one prevents a security or availability
incident. Do not trade them away to make a test pass.

1. **TDD, strictly.** Write the isolation test first and run it. It must fail
   for the expected reason before you write any production code. Never weaken,
   delete or `@Disabled` an existing test to get green, with one exception
   introduced in Phase 2 and resolved in Phase 6 (the documented go-live guard).
2. **HTTP-only scope.** If any scheduler job, queue consumer, connector-side
   code or startup task writes the table, STOP and report. Those tables wait
   for the background transaction primitive (#6398).
3. **Strict tables only.** If `tenant_id` is nullable (dual-scope: `roles`,
   `groups`, `parameters`, ...), STOP and report. Platform-row writes are an
   open policy question (Q7); this skill does not cover them.
4. **One-commit go-live.** Removing the v1 `@Filter` and adding the table to
   `openaev.tenant.active-tables` happen in the same commit, never split.
5. **No hardcoded Flyway numbers.** Refer to migrations by name. Never pin a
   version number in code comments, docs or tests.
6. **Fail-closed is the point.** If a query returns zero rows after wiring,
   the fix is to pass the scope correctly, never to bypass the inspector,
   never to add raw JDBC, never to widen the scope.
7. **Full regression before done.** The activation rewrites the SQL of every
   query touching the table. The full API suite must pass, not just your new
   tests.
8. **Evidence over claims.** Every red and every green leaves a trace: keep
   the raw test output (the failing assertion for the red, the passing
   summary for the green) and paste it into the Phase 8 report. A TDD step
   without its output did not happen.
9. **Do not improvise on drift.** If a model file referenced by this skill
   does not exist anymore, stop and find its successor
   (`git log --oneline --follow --all -- <path>`). Never substitute an
   invented pattern for a missing reference.

## Procedure

### Phase 0 — Eligibility gate (stop conditions)

Replace `{table}`, `{Entity}`, `{EntityRepository}`, `{Api}` below with your
target (e.g. `mitigations`, `Mitigation`, `MitigationRepository`,
`MitigationApi`).

```bash
# 0.1 The entity must be strict tenant-scoped: implements TenantBase, not DualScopeBase
grep -n "TenantBase\|DualScopeBase" openaev-model/src/main/java/io/openaev/database/model/{Entity}.java

# 0.2 The tenant column must be non-nullable. The entity mapping is the
# reliable static check; tenant_id was added to most tables by bulk
# migrations that loop over a table list, so grepping migrations for
# "{table}" + "tenant_id" on one line finds nothing.
grep -n -A2 'name = "tenant_id"' openaev-model/src/main/java/io/openaev/database/model/{Entity}.java
# expect: @JoinColumn(name = "tenant_id", ..., nullable = false)
# authoritative check when a DB is running:
#   SELECT is_nullable FROM information_schema.columns
#   WHERE table_name = '{table}' AND column_name = 'tenant_id';

# 0.3 Unique constraints must be tenant-aware. A global unique index on a
# business key (external id, name, key) means two tenants cannot hold the
# same value: activation would turn that into cross-tenant interference.
grep -rn -i "unique" openaev-api/src/main/java/io/openaev/migration/ | grep -i "{table}"
# the grep misses multi-line definitions; on a live DB, `\d {table}` in psql
# lists every index and constraint and is authoritative
```

0.4 Hot-path check. The inspector wraps every active table in a filtered
sub-query, which can change query plans. If the table sits in frequent joins
or heavy list endpoints, look at the rewritten SQL before go-live: captured
real SQL can be replayed through the inspector with
`openaev-api/src/test/java/io/openaev/config/TenantSqlReplayMeasurementTest.java`
(gated by `-Dtenant.sql.replay.file`), and the heaviest query deserves an
`EXPLAIN` with the rewrite applied. For a small config table this is a
one-line note in the report; for a hot table it is a real measurement.

There is no reliable one-line grep for "no background writer" (a keyword
filter on scheduler/job/consumer misses renamed packages and matches string
literals). The authoritative classification is the Phase 1 inventory: read
every hit.

STOP conditions, report instead of continuing:
- entity implements `DualScopeBase`, or the tenant column is nullable →
  dual-scope, out of scope (hard rule 3)
- Phase 1 finds any non-HTTP path that WRITES the table → background path,
  out of scope (hard rule 2). A background read-only hit (e.g. a telemetry
  counter) is not a blocker but must be listed in the report as a documented
  degradation: once the table is active it reads zero rows.
- 0.3 finds a unique index on a business key that does not include
  `tenant_id` → the schema needs a prep migration first (model: the existing
  `__Update_unique_constraints_for_tenants` migration in
  `openaev-api/src/main/java/io/openaev/migration/`; same pattern, new migration).
  `CREATE UNIQUE INDEX mitigations_unique ON mitigations (mitigation_external_id)`,
  global, so two tenants cannot both hold MITRE mitigation M1013. Fix the
  constraint (add `tenant_id` to it) in its own reviewed change BEFORE the
  activation, and only if per-tenant duplication is the intended semantics;
  if the rows are meant to be platform-shared reference data, the table may
  not be a good activation candidate at all. Report and let a human decide.

When a stop condition is hit, still run Phase 1 (the inventory is what makes
the stop useful), then produce a stop report instead of code and post it on
the table's activation issue. Format:

```markdown
## activate-tenant-table skill run: STOPPED at eligibility (gate <n>)

**Gates**
- 0.1 PASS/STOP: <entity check result>
- 0.2 PASS/STOP: <tenant column nullability>
- 0.3 PASS/STOP: <unique constraints; quote the offending index if any>
- 0.4: <hot-path note>

**Blocker to decide before activation**
<the failed gate, the options this skill lists for it, and what each option
needs (e.g. prep migration modeled on V4_82 vs shared-reference-data
discussion)>

**Inventory (phase 1)**
<repository users and their classification; child tables; other APIs>

**Side findings**
<anything found on the way that someone should look at, one line of impact each>
```

The evidence rule (hard rule 8) applies here too: quote the actual grep
output or file lines behind each gate verdict, do not paraphrase them.

### Phase 1 — Inventory every code path that touches the table

The table does not belong to one API. Any `@Transactional` path that reads it
without a `TxCtx` gets no scope once the table is active, and no scope means
zero rows, silently.

```bash
grep -rln "{EntityRepository}" openaev-api/src/main/java openaev-model/src/main/java
grep -rn "{table}" openaev-api/src/main/java --include="*.java" | grep -v "^Binary"
```

The table-name grep matches string literals too (e.g. "apply mitigations"
inside seeded CVE descriptions). Read each hit before classifying it; a
textual match is not a code path.

Write down every hit and classify it:
- the table's own API and service → wired in Phases 3-4
- another API or service that reads the table → needs `TxCtx` too (Phase 5).
  The pilot found two: `ScenarioImportApi` and `ExerciseImportApi` both look up
  an import mapper.
- background reader → documented degradation (Phase 0)
- background writer → you should have stopped in Phase 0

Also list child tables (FKs pointing at `{table}`). A child without its own
`tenant_id` rides along with the parent and is NOT added to `active-tables`.
A child with its own `tenant_id` is a separate activation; report it.

**Test compatibility scan.** Adding a `TxCtx` parameter to an endpoint breaks
tests that the repository grep misses. Two failure modes exist:

1. A `standaloneSetup` or `@WebMvcTest` MockMvc test hitting the URL → 500
   (`No primary or single unique constructor found for interface TxCtx`)
   because the test has no `TxCtxArgumentResolver`.
2. A test calling the controller method directly in Java → compile error
   (missing argument).

Scan for both:

```bash
# 1.1 standaloneSetup tests that hit the API's URL patterns
grep -rln "standaloneSetup" openaev-api/src/test/java | xargs grep -l "{Api}\|/{entities}"

# 1.2 direct Java calls to the API class
grep -rn "{Api}" openaev-api/src/test/java --include="*.java"
```

Fix: register the resolver on the standalone builder
(`.setCustomArgumentResolvers(new TxCtxTestArgumentResolver(...))`) or migrate
to the full-context `IntegrationTest` base class; add the `TxCtx` arg to
direct calls. List every affected test file in the inventory.

### Phase 2 — RED: write the HTTP isolation test first

Model: `openaev-api/src/test/java/io/openaev/rest/mapper/ImportMapperHttpIsolationTest.java`.
Place the new test next to the API under test
(`openaev-api/src/test/java/io/openaev/rest/{domain}/`).
Copy the model's structure. Key elements that must all be present:

```java
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables={table}")
@WithMockUser(isAdmin = true)
class {Entity}HttpIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @BeforeEach
  void seedTwoTenantsWithOneRowEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("http-iso-b").getId();
    // seed one row per tenant with a native INSERT carrying an explicit tenant_id
  }
}
```

Notes that make or break the test:
- `@TestPropertySource` activates the table for this test only. The test
  classpath keeps the allowlist empty on purpose; never add your table to the
  test-wide properties.
- Seed with a native `INSERT ... VALUES` including `tenant_id`, like the
  pilot's `seedMapper`. The inspector does not block VALUES inserts.
- Ground-truth assertions (prove a row was NOT touched) use raw JDBC on the
  test's own connection, like the pilot's `rawName`/`rawCount` helpers, with
  an `entityManager.flush()` first.
- Each test method stays on ONE tenant path. Changing the scope inside the
  same transaction is refused by the nesting guard in
  `TenantScopeTransactionAspect`.

Cover, one test method each (match your API's real endpoints):
- read own row under own path → 200; other tenant's row under your path → 404
- list/search under a path, and via the `X-Tenant-Ids` header → only that tenant's rows
- create under a tenant path → row stored with that `tenant_id` (assert with a
  native query); create with no selector → 400
- update and delete cross-tenant → 404 or no-op, ground-truth read proves the
  row is untouched
- import/duplicate/export where the API has them
- upsert where the API has one: upserting the same business key under two
  different tenant paths must yield two distinct rows, each with its own
  `tenant_id`. This test only passes if the unique constraints are
  tenant-aware (Phase 0.3); a unique-violation failure here means that gate
  was skipped.

Run it and check the failure reasons:

```bash
mvn -ntp -pl openaev-api test -Dtest='{Entity}HttpIsolationTest'
```

Expected RED: reads under a tenant path fail (no `TxCtx` on the handler yet,
so no scope, so zero rows) and write attribution fails (no resolver yet). If a
test fails for a different reason (compile error, fixture problem), fix that
first; the red must be the mechanism, not noise.

Save the raw output now: the failing assertions are the red evidence that
goes into the Phase 8 report (hard rule 8).

The header-route list test will stay red even after wiring, while the v1
`@Filter` is still on the entity: v1's thread-local predicate ANDs with v2's
and returns nothing. `@Disabled` that one test with a comment saying exactly
that, referencing the go-live phase. This is the ONE allowed `@Disabled`, and
Phase 6 removes it.

### Phase 3 — GREEN: wire `TxCtx` on the table's own API (reads)

Model: `openaev-api/src/main/java/io/openaev/rest/mapper/MapperApi.java`.

- Map the controller on both URIs:
  `@RequestMapping({{Api}.URI, {Api}.TENANT_URI})` with
  `TENANT_URI = TenantUriUtils.TENANT_PREFIX + "/..."`.
- Add a `TxCtx ctx` parameter to every handler whose transaction reads or
  writes the table. The handler body does not use it; the transaction aspect
  does. Copy the pilot's one-line comment explaining that, so a reviewer does
  not delete the "unused" parameter.
- The aspect only fires on `@Transactional` methods. If a handler is not
  `@Transactional`, make it so; a `TxCtx` parameter without the annotation is
  silently ignored and the endpoint stays fail-closed.
- A handler that provably never touches the table (works on other tables, or
  on transient objects never persisted) does not need one. When in doubt, wire it.

Re-run the test class after each endpoint. Read tests go green one by one.
Do not move to writes until all reads are green.

### Phase 4 — RED then GREEN: write attribution

The inspector cannot attribute `INSERT ... VALUES`. Attribution is application
code, and it is the part most often forgotten.

Model: `createImportMapper` and `importMappers` in `MapperApi.java`, plus
`MapperService.createAndSaveImportMapper` in
`openaev-api/src/main/java/io/openaev/service/MapperService.java`.

On every create/import endpoint:

```java
String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
```

and in the service, stamp the entity before save:

```java
entity.setTenant(new Tenant(tenantId));
```

Rules enforced by `TenantWriteScopeResolver` (do not reimplement them, inject
the component): single-tenant scope → that tenant; supplied tenant outside
scope → 400; multi-tenant scope without selector → 400.

An upsert endpoint is both paths at once: its lookup by business key is a
read (scoped by the inspector once `TxCtx` is wired), and its insert branch
is a create (resolve the tenant with `tenantForWrite` and stamp the entity,
exactly like a create). The update branch needs nothing extra; the inspector
already refuses to touch a row outside the scope.

The plumbing also offers `@RequireTenantSelector` (400 when the request
carries no explicit selector, see
`openaev-api/src/main/java/io/openaev/config/RequireTenantSelector.java`).
The pilot does not use it: the resolver's single-tenant rule already refuses
ambiguous writes. Do not add it unless the endpoint must refuse even an
implicit single-tenant scope.

Leave `TenantBaseListener` on the entity: it only fills a null tenant, the
resolver-set value wins.

Re-run: create/import/upsert tests green, including "no selector → 400".

### Phase 5 — Other paths from the inventory

For every other API or service found in Phase 1 that reads the table:
- add a `TxCtx` parameter on its `@Transactional` entrypoint
- add an isolation test proving the cross-tenant case through that path.
  Models: `openaev-api/src/test/java/io/openaev/rest/scenario/ScenarioImportApiTenantIsolationTest.java`
  and `openaev-api/src/test/java/io/openaev/rest/exercise/ExerciseImportApiTenantIsolationTest.java`.

Then add the non-admin proof. Model:
`openaev-api/src/test/java/io/openaev/rest/mapper/ImportMapperNonAdminIsolationTest.java`
(`@WithMockUser(isAdmin = false)`, tenants seeded with
`tenantHelper.createTenantWithCapabilities(...)`). Isolation must hold without
the admin flag.

Do NOT re-prove the out-of-rights selector refusal (403): that is shared
plumbing, already covered by
`openaev-api/src/test/java/io/openaev/config/TenantSelectorMembershipTest.java`.

Finally, register every new `TxCtx`-bearing entrypoint in
`openaev-api/src/test/java/io/openaev/architecture/TenantScopedEntrypointsTxCtxArchTest.java`
(add `"{package}.{Api}#methodName"` entries to `TX_SCOPED_ENTRYPOINTS`), then
run it:

```bash
mvn -ntp -pl openaev-api test -Dtest='TenantScopedEntrypointsTxCtxArchTest,TenantNonOrmAccessArchTest'
```

### Phase 6 — Go-live: ONE commit

Model (from PR #6255): commit "feat(multi-tenancy): activate import_mappers on v2 isolation (#6212)"
(find it with `git log --oneline --grep "activate import_mappers on v2 isolation"`). Four changes, together, nothing else:

1. Remove `@Filter(name = "tenantFilter", ...)` and remove the `TenantBaseListener.class`
   from the entity in
   `openaev-model/src/main/java/io/openaev/database/model/{Entity}.java`.
   Replace it with the pilot's two-line comment stating the table is fully on
   v2 and why the v1 filter must not come back
   (see `ImportMapper.java` after the pilot commit).
2. Append `{table}` to `openaev.tenant.active-tables` in
   `openaev-api/src/main/resources/application.properties` (comma-separated,
   keep existing entries).
3. Re-enable the one `@Disabled` header-route test from Phase 2.
4. Extend the production-config guard so dropping the table from the allowlist
   fails the build. Model:
   `openaev-api/src/test/java/io/openaev/config/ImportMapperActivationConfigTest.java`.
   Prefer extending a shared guard over cloning the file; the assertion must
   name `{table}` explicitly.

If anything in this phase needs "just one more fix" in production code, stop
and go back to the phase that owns that fix. The go-live diff stays minimal.

Rollback story, decide it now, not during an incident: if production
misbehaves after go-live, revert the WHOLE go-live commit (the `@Filter`
comes back in the same change that removes the allowlist entry). Never remove
just the property: with the `@Filter` gone that is an isolation hole, and the
config guard fails the build precisely to stop that move.

Parallel activations: other tables go live through the same
`application.properties` line and the same `TX_SCOPED_ENTRYPOINTS` set.
Rebase right before go-live and re-read the merged allowlist line; a bad
merge that drops another table's entry is what the shared config guard
catches, do not rely on it alone.

### Phase 7 — v1 remnant audit

Before running the regression suite, audit the full call stack of every public
method on `{Api}` (and on the other APIs from Phase 5) for v1 isolation
patterns that conflict with or duplicate the v2 mechanism.

**What to search for:**

1. **`TenantContext.getCurrentTenant()`** — the v1 thread-local tenant. Any
   call in the controller, service, repository, or specification layer that
   touches `{table}` is a v1 remnant. The v2 inspector handles scoping; the
   thread-local is no longer the source of truth for activated tables.
2. **`findByIdAndTenantId`** or any repository method that explicitly filters
   by `tenant_id` on `{table}` — redundant and restrictive. The inspector
   already scopes reads; an explicit `AND tenant_id = ?` prevents multi-tenant
   queries that v2 intentionally supports.
3. **`TenantBaseListener`** on `@EntityListeners` of the entity — if write
   attribution is now explicit via `TenantWriteScopeResolver`, the listener is
   dead code for this entity and should be removed to avoid a hidden fallback
   to `TenantContext`.

**How to search:**

```bash
# 7.1 Direct usage in the API package and its services
grep -rn "TenantContext.getCurrentTenant\|findByIdAndTenantId\|findByTenantId" \
  openaev-api/src/main/java/io/openaev/rest/{domain}/ \
  openaev-api/src/main/java/io/openaev/service/ \
  --include="*.java" | grep -i "{table_or_entity}"

# 7.2 Repository-level tenant filtering on the activated table
grep -rn "TenantId\|tenant_id\|tenantId" \
  openaev-model/src/main/java/io/openaev/database/repository/{EntityRepository}.java \
  openaev-model/src/main/java/io/openaev/database/specification/*{Entity}*.java

# 7.3 Deep stack: check services called by the API methods
grep -rn "TenantContext" \
  $(grep -rln "{EntityRepository}\|{Entity}Service" openaev-api/src/main/java --include="*.java")

# 7.4 TenantBaseListener still on entity
grep -n "TenantBaseListener" \
  openaev-model/src/main/java/io/openaev/database/model/{Entity}.java
```

**Classification and action:**

| Finding | On `{table}`? | Action |
|---------|---------------|--------|
| `TenantContext.getCurrentTenant()` used to look up `{Entity}` | Yes | **Remove** — the inspector scopes the query |
| `TenantContext.getCurrentTenant()` used to look up a *different* entity | No | **Report only** — out of scope for this activation |
| `findByIdAndTenantId` on `{EntityRepository}` | Yes | **Replace with `findById`** — inspector handles scoping, explicit tenant filter blocks multi-tenant reads |
| `findByIdAndTenantId` on a *different* repository | No | **Report only** — that table is still on v1 |
| `TenantBaseListener` on `{Entity}` | Yes | **Remove only if every write path attributes the tenant explicitly** (resolver / `setTenant`); otherwise **keep** — a listener-dependent path (e.g. an importer that `save()`s without `setTenant`) would write a NULL tenant |
| Specification with `tenant_id` predicate on `{table}` | Yes | **Remove the predicate** — inspector handles it |

**Output: audit report**

Produce a table listing every hit, its file and line, whether it targets the
activated table, and the action taken (removed / reported-only). Include it
in the Phase 9 report. Hits on other tables are informational — they document
the v1 surface that remains for future activations.

### Phase 8 — Full regression pass

```bash
# 8.1 re-run the inventory grep: a reader added while you worked ships broken
grep -rln "{EntityRepository}" openaev-api/src/main/java openaev-model/src/main/java

# 8.2 format and compile
mvn -B -ntp spotless:check || mvn -ntp spotless:apply
mvn -ntp clean install -DskipTests

# 8.3 your tests, then the FULL API suite (needs the Docker services from
# openaev-dev/docker-compose.yml: PostgreSQL, MinIO, OpenSearch, RabbitMQ)
mvn -ntp -pl openaev-api test -Dtest='{Entity}*IsolationTest'
```

Any pre-existing test that now fails is signal, not noise: it is a query on
your table that lost its scope. Fix it by passing `TxCtx`, never by
deactivating the table or weakening the test.

### Phase 9 — Report

Before marking the issue done, write down:
- the red and green evidence: the raw failing assertions from Phase 2 and the
  final passing summary from Phase 8 (hard rule 8)
- the endpoints wired and the arch-test entries added
- the v1 remnant audit table from Phase 7 (hits found, actions taken, reported-only items)
- background readers left degraded (from Phase 0/1), each with a one-line impact
- child tables and how they are covered
- client impact: writes now require a single-tenant scope. Calls using the tenant path
  (`/api/tenants/{tenantId}/...`) already satisfy this; callers using the header route or no selector
  may get 400 on create/import until they switch to a single-tenant selector.

## Definition of Done

- [ ] Phase 0 gates passed (strict table, HTTP-only), stop conditions reported if hit
- [ ] unique constraints on business keys include tenant_id, or a prep
      migration was done first (own reviewed change)
- [ ] inventory complete; every reader classified; redone before go-live
- [ ] isolation test written first and seen red for the mechanism, then green;
      raw red/green outputs captured in the report
- [ ] reads: own row visible, cross-tenant 404, path and header selectors
- [ ] writes: attribution asserted at the SQL level, no selector → 400;
      upsert of the same business key from two tenants yields two rows
- [ ] other APIs from the inventory wired and tested
- [ ] non-admin variant green
- [ ] arch tests updated and green
- [ ] go-live is one commit: @Filter removed + allowlist entry + re-enabled test + config guard
- [ ] v1 remnant audit complete: no `TenantContext`/`findByIdAndTenantId`/`TenantBaseListener`
      targeting the activated table remains in the call stack
- [ ] spotless, compile
- [ ] report written (degradations, children, client impact, v1 audit table)
