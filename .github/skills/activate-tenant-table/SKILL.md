---
name: activate-tenant-table
description: >-
  Activates one table on multi-tenancy v2 (statement inspector + can_access_tenant),
  test-first, following the import_mappers pilot (PR #6255). Use when asked to switch
  a table from v1 @Filter isolation to v2. Covers HTTP paths and, since the background
  transaction primitive (#6398), background writers (scheduler jobs, consumers) once
  they are converted to the primitive. Covers eligibility gates, code-path inventory,
  TDD isolation tests, write attribution, the background conversion, the one-commit
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

If the table has a background writer (a scheduler job, queue consumer or
startup task that writes it), read the background transaction primitive too:
`TenantScopedTransaction` (`openaev-model/src/main/java/io/openaev/context/TenantScopedTransaction.java`).
The HTTP path carries its scope through `@Transactional` + `TxCtx` + the aspect;
the background path must NOT use `@Transactional` (its self-invocation trap
silently skips both the transaction and the scope) and opens transactions through
the primitive instead. Phase 5b (this runbook's background-writer conversion
phase, defined below between Phase 5 and the go-live) converts those writers;
it is the prerequisite for activating any table a background job writes.

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
2. **Background writers go through the primitive, never `@Transactional`.** A
   scheduler job, queue consumer, connector-side path or startup task that
   writes the table is NOT an automatic stop anymore (it was before #6398).
   It becomes activable ONCE every such writer is converted to
   `TenantScopedTransaction` (Phase 5b), carrying a real per-tenant scope. Until
   that conversion is done and tested, an unconverted background writer is still
   a hard blocker: activating the table under it would make the writer read and
   write zero rows, silently.
   ONE documented waiver exists: a writer that only INSERTs fresh rows (VALUES
   inserts are not blocked by the inspector), never READS the table, and
   attributes `tenant_id` correctly (listener + `TenantContext`, or explicit) may
   ship unconverted IF a test pins that write shape under activation AND the
   conversion is a tracked follow-up. Example: the tenant-provisioning datapack
   writing `cwes` (pinned by the provisioning-style test in
   `CweHttpIsolationTest`, conversion tracked with the migration-engine work).
   A background READER, or any read-then-write path, gets no waiver. Background code must never use `@Transactional`
   for the write (self-invocation trap) and must never open raw transactions;
   both are guarded by the ArchUnit rules in
   `openaev-api/src/test/java/io/openaev/architecture/TenantBackgroundTransactionRules.java`.
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
- Phase 1 finds any non-HTTP path that WRITES the table → NOT an automatic stop
  since #6398, but it moves the table into the background-writer track: every
  such writer must be converted to the primitive in Phase 5b before go-live. If
  the conversion is out of scope for this run (e.g. the writer is a large
  execution-surface job you are not converting now), STOP and report it as a
  blocker; the table cannot be activated while an unconverted writer touches it.
  A background READ-only hit (e.g. a telemetry counter) is not a blocker but
  must be listed in the report as a documented degradation: once the table is
  active it reads zero rows unless that reader also carries a scope.
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
- background reader → documented degradation (Phase 0), or give it a scope too
  (wrap its read in `tenantTx.execute(scope, …)`) if it must keep seeing rows
- background writer → convert to the primitive in Phase 5b. If you are not
  converting it in this run, it is a blocker: stop and report (Phase 0)

Then walk the call graph up UNTIL you reach every entrypoint (`@RestController`
method), not just one hop. Stopping at the first caller is the #7188-class
regression: the executors activation (#6409) found `ExerciseService
.throwIfExerciseNotLaunchable`'s callers `updateExerciseStart` and
`deprecatedUpdateExerciseStart` and wired `TxCtx` on both, but
`ExerciseApi#changeExerciseStatus` — a third, equally direct caller of the
exact same gate — was never enumerated and shipped with the tenant-scope gap
undetected. Every intermediate method found this way (a shared gate/helper
like `throwIfXNotLaunchable`, a mapper, a projection builder, ...) becomes its
own grep target:

```bash
# for EVERY shared method discovered while walking up (not just the first one
# found) - repeat until the hit list is only @RestController entrypoints
grep -rn "\.{sharedMethodName}(" openaev-api/src/main/java --include="*.java"
```

List ALL of its callers — they are part of the surface even though they match
neither the repository grep nor the table-name grep. This is where parallel
and DEPRECATED controllers hide: the cwes activation had to wire `CveApi`
(deprecated since 1.19, still deployed, same `VulnerabilityService`
underneath) alongside `VulnerabilityApi`; neither grep sees it because it only
references the service. A deprecated controller that still ships is a live
path. When the walk reaches a shared gate with N callers, count them: N
entrypoints in the inventory must produce N `TxCtx` additions (or N documented
exceptions) — a gate found with only "the callers I happened to notice" is not
a complete inventory.

Separately, ALWAYS list every caller of the association accessor for any
entity that holds a `@ManyToOne`/`@ManyToMany`/`@OneToMany` reference to the
entity being activated
(`grep -rn "\.get{Entities}()\|\.get{Entity}()" ... --include="*.java"`), even
when `{table}` has its own API. Having an own API and being reached through
another aggregate's eager/lazy association are independent facts — the
executors table has `ExecutorApi`, yet `Agent.getExecutor()` (an EAGER
`@ManyToOne`, read by `EnterpriseEditionService.detectEEExecutors` several
calls away from any executor-specific code) was the exact path that broke.
Lazy or eager, an association load bypasses the repository entirely, so the
repository grep and the table-name grep cannot see it — only an explicit scan
of every entity that references `{Entity}` finds it:

```bash
# every entity field of type {Entity} (or a collection of it), anywhere in the model
grep -rln "private {Entity} \|private List<{Entity}>\|private Set<{Entity}>" openaev-model/src/main/java --include="*.java"
# then, for each owning entity found, every caller of its accessor
grep -rn "\.get{Entity}()" openaev-api/src/main/java openaev-model/src/main/java --include="*.java"
```

Also list child tables (FKs pointing at `{table}`). A child without its own
`tenant_id` rides along with the parent and is NOT added to `active-tables`.
A child with its own `tenant_id` is a separate activation; report it.

**Native query shape scan (#7007).** Activating `{table}` does not just gate
the queries that read `{table}` itself — it pulls into the fail-closed
`TenantStatementInspector` rewrite EVERY native `@Query` that so much as
mentions `{table}` in a `JOIN`, however unrelated to the rest of that query's
predicates. `TenantStatementInspector` only accepts a closed list of
FROM/JOIN shapes; anything else is refused with `TENANT_FILTERING_REFUSED`,
even a shape that has nothing to do with tenant isolation. The #6751
(collectors) activation shipped this exact regression to production: adding
`collectors` to `active-tables` pulled `findAgentlessExpectationsNotFilledForSource`
into rewriting because it had `JOIN collectors c`, and its unrelated
`NOT EXISTS (SELECT 1 FROM jsonb_array_elements(...) r ...)` predicate — a
table-function FROM item without the `LATERAL` prefix — was refused fail-closed,
breaking the AI defense collector endpoint on every call (see #7007 / PR #7008).

```bash
# every native @Query that JOINs {table}, anywhere in the codebase - not just
# the table's own repository
grep -rln "JOIN {table}\|join {table}" openaev-model/src/main/java openaev-api/src/main/java --include="*.java"
```

For every hit, read the FULL query text (not just the `JOIN {table}` line)
and check every FROM/JOIN item against what `TenantStatementInspector`
already accepts (see `TenantStatementInspectorTest`). The recurring offender
is a table-function FROM item (`jsonb_array_elements`, `jsonb_each`,
`unnest`, ...) missing `LATERAL`: `LATERAL` is a noise word for a
function-call FROM item in PostgreSQL (identical semantics and plan) but is
exactly the marker the inspector uses to accept it — add it. If the query
uses a FROM/JOIN shape the inspector does not cover at all, that is a
blocker: stop and report (Phase 0), do not attempt to teach the inspector a
new shape inside a table-activation PR.

Pin the fix with a regression test in `TenantStatementInspectorTest` using
the REAL production SQL (read the `@Query` value via reflection off the
repository method, as PR #7008 does), not a hand-simplified paraphrase — the
whole point is to catch the exact shape that broke in production. Also note
for the record: `openaev-api/src/test/resources/application.properties`
ships an EMPTY `openaev.tenant.active-tables`, so `IntegrationTest`-based API
tests never exercise the rewriter for `{table}` and cannot catch this class
of regression — `TenantStatementInspectorTest` (constructed directly with
`{table}` in its active-table set) is the only test layer that does.

**Test compatibility scan.** Adding a `TxCtx` parameter to an endpoint breaks
tests that the repository grep misses. Three failure modes exist:

1. A `standaloneSetup` or `@WebMvcTest` MockMvc test hitting the URL → 500
   (`No primary or single unique constructor found for interface TxCtx`)
   because the test has no `TxCtxArgumentResolver`.
2. A test calling the controller method directly in Java → compile error
   (missing argument).
3. A test relying on the v1 `@Filter` you remove at go-live: any test that
   calls `session.enableFilter("tenantFilter")` (or otherwise counts on
   implicit filtering) and then asserts a `findAll()`-style read on the entity
   silently changes meaning — once the filter is gone and the test context
   keeps the allowlist empty, the read returns EVERY tenant's rows. Found on
   the cwes activation: `TenantServiceTest` expected 7 cwes and saw 14. Fix by
   asserting on explicit attribution instead
   (`.filteredOn(e -> tenantId.equals(e.getTenant().getId()))`), never by
   re-adding the filter.

Scan for all three:

```bash
# 1.1 standaloneSetup tests that hit the API's URL patterns
grep -rln "standaloneSetup" openaev-api/src/test/java | xargs grep -l "{Api}\|/{entities}"

# 1.2 direct Java calls to the API class
grep -rn "{Api}" openaev-api/src/test/java --include="*.java"

# 1.3 tests relying on the v1 filter over the entity you are activating
grep -rln 'enableFilter("tenantFilter")' openaev-api/src/test/java | xargs grep -l "{EntityRepository}\|{Entity}"
```

Fix: register the resolver on the standalone builder
(`.setCustomArgumentResolvers(new TxCtxTestArgumentResolver(...))`) or migrate
to the full-context `IntegrationTest` base class; add the `TxCtx` arg to
direct calls. List every affected test file in the inventory.

### Phase 2 — RED: write the HTTP isolation test first

Model: `openaev-api/src/test/java/io/openaev/rest/mapper/ImportMapperHttpIsolationTest.java`.
If the table has NO API of its own and is reached through another aggregate's
association, prove isolation through THAT aggregate's real endpoints instead;
model: `openaev-api/src/test/java/io/openaev/rest/vulnerability/CweHttpIsolationTest.java`
(cwes proven through the vulnerability endpoints: own-path read exposes the
row, cross-tenant read sees an empty association, ground truth by raw JDBC).
Such a test cannot be `@Transactional` when it must touch two tenant paths:
each request needs its own transaction (see the model's javadoc). Everything it
creates is therefore COMMITTED: clean the table rows explicitly in `@AfterEach`,
then remove the tenants with
`TenantIsolationTestHelper#deleteCommittedTenants` (null-safe, handles the one
non-cascading tenant child).
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
- find-or-create by business key (Phase 4's trap): with the key existing in
  BOTH tenants, a request under a MULTI-tenant scope (plain path, caller in
  two tenants) must be refused with 400 — never 500 on the duplicate, never a
  silent link to another tenant's row. And under one tenant's path, a second
  write with the same key must REUSE that tenant's row, not duplicate it.
  Models: `plainPathWithCweIsRefusedUnderMultiTenantScope` and
  `sameTenantCweIsReusedNotDuplicated` in `CweHttpIsolationTest`.

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

An upsert (or any find-or-create by business key) is both paths at once, and
it hides the nastiest trap of an activation. Do NOT look the row up by the
bare business key under the request scope: after activation the unique key is
per-tenant (`business_key, tenant_id`), so under a MULTI-tenant read scope the
lookup can match one row per in-scope tenant. Concretely it either crashes
(`IncorrectResultSizeDataAccessException` on the `Optional`) or, when a single
in-scope tenant owns the key, silently links ANOTHER tenant's row to your
write. The per-tenant preset data makes duplicated business keys the NORMAL
case, not an edge. The correct order, proven on the cwes activation:

1. Resolve the write tenant FIRST: `tenantForWrite(ctx, null)` (an ambiguous
   multi-tenant scope is refused with 400, loudly, before any lookup).
2. Look up by the per-tenant unique key: a repository method
   `findBy{BusinessKey}AndTenantId(key, writeTenant)` (model:
   `CweRepository#findByExternalIdAndTenantId`, used by
   `VulnerabilityService#updateCweAssociations`).
3. The insert branch stamps the entity with that same write tenant.

The update branch needs nothing extra; the inspector already refuses to touch
a row outside the scope.

The plumbing also offers `@RequireTenantSelector` (400 when the request
carries no explicit selector, see
`openaev-api/src/main/java/io/openaev/config/RequireTenantSelector.java`).
The pilot does not use it: the resolver's single-tenant rule already refuses
ambiguous writes. Do not add it unless the endpoint must refuse even an
implicit single-tenant scope.

Do NOT keep `TenantBaseListener` / `TenantIdBaseListener` on the entity. It is
a v1 pattern that reads from `TenantContext` — which is no longer the source of
truth for activated tables. Write attribution is now explicit via the resolver;
any test that relied on the listener auto-populating tenant must be fixed to set
`tenantId` explicitly. Keeping the listener creates a hidden fallback path that
masks missing write attribution and blocks the eventual removal of
`TenantContext` from the codebase.

**Write-path completeness check — before removing the listener, audit every
persist call on the entity:**

```bash
# 4.1 Find every save/persist of the entity
grep -rn "{EntityRepository}\.\(save\|saveAll\|saveAndFlush\)\|persist({entity}\|merge({entity}" \
  openaev-api/src/main/java openaev-model/src/main/java --include="*.java"

# 4.2 For each hit, confirm one of:
#   (a) it calls writeScopeResolver.tenantForWrite(ctx, ...) and stamps the entity
#       with setTenant(new Tenant(tenantId)) BEFORE the save — a CREATE path
#   (b) it is an UPDATE-only path (the row already exists with tenant_id set;
#       the inspector scopes the statement, no re-attribution needed)
#   (c) it is a background writer covered by Phase 5b (stamped inside a
#       forEachTenant or execute(forTenant(id), ...) scope)
```

Any save that creates a new entity without explicit tenant attribution is a
**silent data corruption** once the listener is removed — `tenant_id` will be
NULL. Fix it (add `tenantForWrite` + `setTenant`) before proceeding to go-live.
This check catches paths that the Phase 1 inventory (which greps for readers)
and the Phase 2 isolation test (which covers the main API only) can miss:
services shared by multiple controllers, internal helpers, bulk importers.

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

### Phase 5b — Background writers: convert to the primitive

Skip this phase only if Phase 1 found NO background path that touches the table.
Otherwise every background writer (and every background reader that must keep
seeing rows) is converted here, BEFORE go-live: once the table is in
`active-tables`, an unconverted background path reads and writes zero rows,
silently.

Model conversion: `UrlAccessTokenPurgeJob`
(`openaev-api/src/main/java/io/openaev/scheduler/jobs/UrlAccessTokenPurgeJob.java`),
converted to `tenantTx.execute(TxCtx.allTenants(), …)` in PR #6398. Injected
dependency: `TenantScopedTransaction tenantTx`.

Maturity note: the background path is newer and less proven than the HTTP path.
At the time of writing, the only converted job is `UrlAccessTokenPurgeJob`, which
uses `allTenants()`; the per-tenant `forEachTenant` idiom has no production caller
yet (it is covered by integration tests, not by a real job). Treat the first
per-tenant conversion as a real dress rehearsal, not a copy-paste, and expand the
model list as jobs are converted.

**Enumerate every background path first.** Phase 1's greps are repository- and
table-name-oriented and can miss a background surface. There is no single
reliable grep, so sweep several and READ each hit:

```bash
# scheduled work and startup tasks
grep -rn "@Scheduled\|implements Job\|extends QuartzJobBean\|ApplicationRunner\|CommandLineRunner\|@PostConstruct" \
  openaev-api/src/main/java/io/openaev/scheduler openaev-api/src/main/java --include="*.java" | grep -i "{table_or_entity}"
# queue / broker consumers
grep -rn "@RabbitListener\|@KafkaListener\|MessageListener\|consume\|@EventListener" \
  openaev-api/src/main/java --include="*.java" | grep -i "{table_or_entity}"
# then, for each candidate job/consumer class, check whether it reaches the repository
grep -rln "{EntityRepository}\|{Entity}Service" openaev-api/src/main/java/io/openaev/scheduler --include="*.java"
```

A background path that WRITES the table and is not converted here is a go-live
blocker (hard rule 2). List every background hit in the Phase 9 report with its
scope choice or its blocker reason.

**Rules for the background write path (all guarded by the ArchUnit rules in
`TenantBackgroundTransactionRules.java`, enforced frozen by
`TenantBackgroundTransactionArchTest.java`):**

- No `@Transactional` on the background write. Its self-invocation trap skips
  both the transaction and the scope with no error. Open the transaction with
  the primitive instead.
- No raw transaction plumbing in jobs (`TransactionTemplate`,
  `PlatformTransactionManager`, manual `getTransaction/commit`). The primitive
  is the one door.
- Never catch-and-continue inside a single transaction. Any runtime exception
  from a joined `@Transactional` service marks the whole transaction
  rollback-only; carrying on dies at commit (`UnexpectedRollbackException`).
  Recover around a boundary, not inside one.

**Choose the scope by what the job does:**

| The job... | Scope | Call |
|------------|-------|------|
| does the same unit of work for every tenant, INSERTING or updating per-tenant rows | per tenant, one transaction each | `tenantTx.forEachTenant(ctx -> …)` |
| already works on one known tenant | that tenant | `tenantTx.execute(TxCtx.forTenant(id), work)` |
| already runs its tenants IN PARALLEL on its own executor (model: `ManagerIntegrationsSyncJob`) | per tenant, one transaction per task | keep the executor; each task calls `tenantTx.execute(TxCtx.forTenant(id), work)` |
| does a single bulk read, or a bulk delete/update by predicate, spanning all tenants | all active tenants, resolved | `tenantTx.execute(TxCtx.allTenants(), work)` |

A **row insert** cannot be attributed under `allTenants()`: a new row belongs to
exactly one tenant, and `TenantWriteScopeResolver.tenantForWrite` refuses the
intention (`TenantWriteScopeException`). So a job that INSERTS per-tenant rows
uses `forEachTenant` (or `execute(forTenant(id), …)`), never `allTenants()`.
`allTenants()` fits a bulk read, or a bulk delete/update BY PREDICATE: those
never call `tenantForWrite`, the inspector simply scopes the statement to the
resolved tenant list, so no per-row attribution is needed. The
`UrlAccessTokenPurgeJob` model is exactly such a bulk delete under
`allTenants()`, not a read.

**Native and raw SQL — the background-job trap.** Background jobs lean on
hand-optimized SQL more than HTTP code (queries rewritten as native to dodge ORM
inefficiency). The two forms behave very differently once the table is active:

- **Native through Hibernate** (`@Query(nativeQuery = true)`,
  `entityManager.createNativeQuery(...)`) DOES pass through the statement
  inspector: it is a Hibernate `StatementInspector`, so it sees this SQL and
  scopes it. The risk is not a leak, it is availability: the inspector is
  fail-closed, so a shape it cannot parse or rewrite (a multi-target `DELETE`, a
  target-side-join `UPDATE`, an exotic `FROM`, an unusual statement) throws
  `TenantFilteringException` and the query BREAKS once the table is active.
  Hand-optimized job queries (CTEs, window functions, unusual joins) are exactly
  the shapes most likely to hit "not yet covered". Every native query on the
  table used by a background path must therefore be EXERCISED by a test (the
  Phase 5b isolation test or the regression suite) so a rewrite failure surfaces
  in CI, not in production. If the inspector refuses a query, rewrite it into a
  covered shape or postpone the activation; never bypass the inspector.
- **Raw JDBC** (`JdbcTemplate`, `NamedParameterJdbcOperations`, a direct
  `Connection`/`Statement`) BYPASSES Hibernate entirely, so the inspector never
  sees it: a silent cross-tenant read and an unattributed write. This is already
  guarded by `TenantNonOrmAccessArchTest`
  (`no_raw_jdbc_outside_the_allowlist`), which fails the build on any new raw
  JDBC in production code outside the audited `@AllowRawJdbc` allowlist (which
  covers non-tenant tables only). A raw-JDBC path touching the table you are
  activating is a HARD BLOCKER: convert it to go through Hibernate (so it gets
  inspected) before go-live. Never `@AllowRawJdbc` a tenant table to make a job
  compile.
- **Narrow exception — a provably insert-only bypass.** The rule above is
  deliberately blunt: it cannot tell an `INSERT ... VALUES`-only path (which the
  inspector would not scope anyway, since tenant assignment on a VALUES insert
  stays an application concern) from a read/update bypass (which silently reads
  or writes across tenants). A raw-JDBC path may keep `@AllowRawJdbc` on a tenant
  table ONLY when all three hold: it emits nothing but `INSERT`, every insert
  carries `tenant_id` as an explicit column, and a test enforces both on every
  build so the exemption cannot silently widen. The seed generator for the
  attack-path tables is the reference (`AttackPathSeedServiceTest`). Absent that
  enforced insert-only proof, the HARD BLOCKER stands.

Add a grep for both to the inventory and read each hit:

```bash
grep -rn "nativeQuery *= *true\|createNativeQuery" openaev-api/src/main/java openaev-model/src/main/java --include="*.java" | grep -i "{table_or_entity}"
grep -rn "JdbcTemplate\|NamedParameterJdbc\|getConnection\|@AllowRawJdbc" openaev-api/src/main/java --include="*.java" | grep -i "{table_or_entity}"
```

**Write attribution is the same as HTTP.** Inside the scope, resolve and stamp:

```java
String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
entity.setTenant(new Tenant(tenantId));
```

Under `forEachTenant` each iteration carries a single-tenant scope, so
attribution resolves cleanly per tenant.

**Nesting.** If the converted job joins a `@Transactional` service that carries
a NARROWER `TxCtx`, the aspect's nesting guard refuses it and poisons the
transaction. Open the narrower scope with `executeNew` from the start, never by
narrowing inside the same transaction.

**RED first, then GREEN — the background isolation test.** Model:
`openaev-api/src/test/java/io/openaev/context/TenantScopedTransactionIntegrationTest.java`
(single scope) and
`openaev-api/src/test/java/io/openaev/context/TenantScopeAllTenantsIntegrationTest.java`
(`allTenants()` and `forEachTenant`, including the per-tenant rollback proof).
Key differences from the HTTP test:

- The test class is NOT `@Transactional`. The primitive's `execute` refuses to
  open inside an active transaction, so seed and clean through auto-committed
  `JdbcTemplate`, not a rolled-back test transaction.
- Activate the table with `@TestPropertySource(properties =
  "openaev.tenant.active-tables={table}")`, same as the HTTP test.
- Prove: the converted job under its scope sees and writes only the in-scope
  tenant's rows; a cross-tenant row is invisible; for a per-tenant loop, one
  tenant's failure is rolled back on its own and does not poison the others.

Run it red, wire the conversion, run it green, keep both outputs (hard rule 8):

```bash
mvn -ntp -pl openaev-api test -Dtest='{Entity}BackgroundIsolationTest'
```

**ArchUnit baseline.** Converting a writer that currently sits on the frozen
background baseline (a `@Transactional` job, a raw-template job) SOLVES recorded
violations — and that FAILS the locked build: `FreezingArchRule` removes solved
violations from the store, which is a store write, and under
`freeze.store.default.allowStoreUpdate=false` the write throws
`StoreUpdateFailedException` (verified in ArchUnit 1.4.2). The conversion PR must
therefore include a deliberate store refresh; the full procedure (triage, fix
patterns, tests, re-freeze commands) is its own runbook:
`.github/skills/reduce-tx-baseline/SKILL.md`. Never hand-edit the store files.

**Known limits of the background path — name them in the report, do not paper
over them:**

- No runtime scope guarantee for background writers. The HTTP side is pinned by
  `TenantScopedEntrypointsTxCtxArchTest`, which fails the build if an active
  table's handler loses its `TxCtx`. There is NO background analogue yet: a NEW
  job that writes an already-active table without going through the primitive
  would read and write zero rows with no failing test. The existing rules forbid
  the wrong SHAPE (`@Transactional`, raw plumbing, raw JDBC) but do not assert
  that every writer of an active table carries a real scope. Until that guard
  exists, converting a table's writers is a point-in-time fact, not an invariant
  — say so in the report.
- The per-tenant loop is serial and single-threaded, one transaction per tenant.
  For a job over thousands of tenants, watch total runtime against the job's
  window (`@DisallowConcurrentExecution` means an overrun skips the next fire).
  The loop itself provides no batching or parallelism, but parallel per-tenant
  work is NOT a workaround: a job with its own executor opens one transaction
  per task (see the scope table above), and concurrent scoped transactions are
  proven isolated by test, for reads and writes. Size such an executor against
  the connection pool: each concurrent task holds one pooled connection for its
  whole transaction, and an oversized fan-out starves the HTTP path. **A nested
  `executeNew` holds two.** When the converted write is not a top-level job but a
  hook inside an already-transactional caller (e.g. a per-inject write from the
  run flow), `executeNew` opens a second, REQUIRES_NEW transaction, so that task
  holds two pooled connections at once for the duration of the inner write.
  Concurrency is then bounded by `pool_size / 2`, not `pool_size`, and K parallel
  callers each demanding their second connection at once can deadlock on the pool
  until the Hikari timeout. Size the caller's concurrency against half the pool,
  or keep the inner write short so the second connection is held briefly. And
  await every task before the job method returns: a fire-and-forget job defeats
  `@DisallowConcurrentExecution`, so the next fire could open a second
  transaction on the SAME tenant. If the SEQUENTIAL loop's runtime becomes the
  concern, raise it rather than hand-rolling a second loop idiom.
- Partial failure is visible only in logs. `forEachTenant` runs every tenant and
  throws one aggregate at the end, so the job is marked failed even when most
  tenants succeeded. Operators see "failed"; the per-tenant `log.warn` and the
  aggregate's suppressed causes carry what actually happened. A success/failure
  summary metric is a follow-up, not part of the primitive.

### Phase 6 — Go-live: ONE commit

Model (from PR #6255): commit "feat(multi-tenancy): activate import_mappers on v2 isolation (#6212)"
(find it with `git log --oneline --grep "activate import_mappers on v2 isolation"`). Four changes, together, nothing else:

1. Remove `@Filter(name = "tenantFilter", ...)` and remove the `TenantBaseListener.class`
   (or `TenantIdBaseListener.class`) from `@EntityListeners` in
   `openaev-model/src/main/java/io/openaev/database/model/{Entity}.java`.
   Replace it with a javadoc comment stating the table is fully on v2 and why
   the v1 filter and listener must not come back. Fix any test that relied on
   the listener to auto-populate `tenantId` — set it explicitly instead.
2. Append `{table}` to `openaev.tenant.active-tables` in
   `openaev-api/src/main/resources/application.properties` (comma-separated,
   keep existing entries).
3. Re-enable the one `@Disabled` header-route test from Phase 2.
4. Extend the production-config guard so dropping the table from the allowlist
   fails the build. Model:
   `openaev-api/src/test/java/io/openaev/config/ImportMapperActivationConfigTest.java`.
   Prefer extending a shared guard over cloning the file; the assertion must
   name `{table}` explicitly.
5. Extend the access guard
   (`openaev-api/src/test/java/io/openaev/architecture/TenantActiveTableAccessArchTest.java`):
   add `{table}` to `GUARDED_TABLES`, a repository rule allowlisting the Phase 1
   inventory (each entry commented with its scope mechanism), and an
   association-accessor rule for every entity accessor that lazy-loads the table
   from another aggregate (the cwes model: `Vulnerability#getCwes`). The guard's
   completeness check fails the build if the table is activated without this
   step — that is deliberate.

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
| `TenantBaseListener` on `{Entity}` | Yes | **Remove** — it is a v1 pattern that reads from `TenantContext`; write attribution is now explicit via the resolver. Fix any test or code path that relied on the listener to auto-populate tenant; set `tenantId` explicitly instead |
| Specification with `tenant_id` predicate on `{table}` | Yes | **Remove the predicate** — inspector handles it |

**Output: audit report**

Produce a table listing every hit, its file and line, whether it targets the
activated table, and the action taken (removed / reported-only). Include it
in the Phase 9 report. Hits on other tables are informational — they document
the v1 surface that remains for future activations.

### Phase 8 — Full regression pass

```bash
# 8.1 re-run the inventory greps: a reader, a background path, a native or raw
# query added while you worked ships broken (or unscoped)
grep -rln "{EntityRepository}" openaev-api/src/main/java openaev-model/src/main/java
grep -rn "nativeQuery *= *true\|createNativeQuery\|JdbcTemplate\|NamedParameterJdbc" \
  openaev-api/src/main/java openaev-model/src/main/java --include="*.java" | grep -i "{table_or_entity}"

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
- the background writers converted (Phase 5b): each one, its scope choice
  (`forEachTenant` / `forTenant` / `allTenants`) and the reason, and its
  isolation test
- the v1 remnant audit table from Phase 7 (hits found, actions taken, reported-only items)
- background readers left degraded (from Phase 0/1), each with a one-line impact
- child tables and how they are covered
- client impact: writes now require a single-tenant scope. Calls using the tenant path
  (`/api/tenants/{tenantId}/...`) already satisfy this; callers using the header route or no selector
  may get 400 on create/import until they switch to a single-tenant selector.

## Definition of Done

- [ ] Phase 0 gates passed (strict table; background writers either converted in
      Phase 5b or reported as blockers), stop conditions reported if hit
- [ ] unique constraints on business keys include tenant_id, or a prep
      migration was done first (own reviewed change)
- [ ] inventory complete; every reader classified; redone before go-live
- [ ] call graph walked to every `@RestController` entrypoint (not one hop):
      for each shared gate/helper method found along the way, its full caller
      list was grepped and the count of `TxCtx` additions matches the count of
      callers found (#6409 regression: `changeExerciseStatus` was a third,
      unwired caller of `throwIfExerciseNotLaunchable` missed this way)
- [ ] association-accessor scan run for every entity holding a reference to
      the activated entity, regardless of whether the activated table has its
      own API (eager/lazy loads bypass the repository grep either way)
- [ ] isolation test written first and seen red for the mechanism, then green;
      raw red/green outputs captured in the report
- [ ] reads: own row visible, cross-tenant 404, path and header selectors
- [ ] writes: attribution asserted at the SQL level, no selector → 400;
      upsert of the same business key from two tenants yields two rows
- [ ] other APIs from the inventory wired and tested
- [ ] background writers converted to the primitive (no `@Transactional`, no raw
      plumbing), each with a per-tenant or `allTenants` scope and a green
      background isolation test (Phase 5b)
- [ ] native queries on the table (`@Query(nativeQuery=true)`,
      `createNativeQuery`) are exercised by a test so a fail-closed rewrite
      refusal surfaces in CI, not production; any raw JDBC on the table converted
      to Hibernate (never `@AllowRawJdbc` on a tenant table)
- [ ] codebase-wide scan for any native `@Query` that `JOIN`s the table (not
      just its own repository) done and every FROM/JOIN shape in those queries
      checked against `TenantStatementInspector`'s accepted shapes; any
      table-function FROM item (`jsonb_array_elements`, `unnest`, ...) carries
      `LATERAL`; a regression test pins the real production SQL (#7007)
- [ ] non-admin variant green
- [ ] arch tests updated and green
- [ ] go-live is one commit: @Filter removed + allowlist entry + re-enabled test + config guard
- [ ] v1 remnant audit complete: no `TenantContext`/`findByIdAndTenantId`/`TenantBaseListener`
      targeting the activated table remains in the call stack
- [ ] spotless, compile
- [ ] report written (degradations, children, client impact, v1 audit table)
