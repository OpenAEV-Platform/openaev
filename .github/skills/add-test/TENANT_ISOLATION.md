---
name: add-tenant-isolation-test
description: >-
  Generates a v2 tenant-isolation test for a given table, modeled on the real
  ImportMapperHttpIsolationTest (pilot PR #6255). Proves through the real HTTP
  endpoints that tenant-scoped rows never leak between tenants. Use when a
  table is being activated on multi-tenancy v2 (openaev.tenant.active-tables)
  or when an activable table has no isolation test yet.
---

# Generate a v2 Tenant Isolation Test

This skill produces one test file for one table. The model is the pilot:
`openaev-api/src/test/java/io/openaev/rest/mapper/ImportMapperHttpIsolationTest.java`
(PR #6255, table `import_mappers`). Every rule below exists in that file for a
reason; the reasons are written down so a hand-written test does not drift.

Full code templates: [`examples/tenant-isolation-templates.md`](examples/tenant-isolation-templates.md).

This skill pairs with:
- the `activate-tenant-table` skill
  (`.github/skills/activate-tenant-table/SKILL.md`): its isolation-test phase
  produces exactly this file, first red, then green as the wiring lands;
- the future "tenant scope coverage" CI gate: once it lands, this file will
  carry `@CoversTenantIsolation("{table}")`. Until then the template puts the
  annotation in a comment.

## v1 is gone from this skill

The old version of this skill taught two rules that are now wrong. If you see
them in a review, they are v1 leftovers:

- "cross-tenant access returns 404, most common case". In v2 the status depends
  on the verb (see the semantics table). A cross-tenant DELETE returns 2xx and
  touches zero rows.
- `switchToTenant()` in the middle of a test. In v2 the tenant scope is set
  once per transaction; the aspect (`TenantScopeTransactionAspect`) throws if a
  test tries to redefine it. One tenant per test method, always.

Existing v1 `TenantIsolation` nested classes keep running; do not migrate them
with this skill. This skill only generates tests for v2 activations. The v1
recipe lives in git history if ever needed.

## Step 0 — Probe the table and the API (do not ask, derive)

Replace `{table}`, `{Entity}`, `{Api}` with your target. Derive every input
from the code; an input written from memory produces a test that lies.

```bash
# endpoints the API really has (this decides which cases to generate)
grep -n "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@RequestMapping" \
  openaev-api/src/main/java/io/openaev/rest/{domain}/{Api}.java

# ID column type: uuid needs CAST(? AS uuid) in raw JDBC, varchar does not
grep -rn -A3 "CREATE TABLE {table}" openaev-api/src/main/java/io/openaev/migration/

# NOT NULL columns without defaults: the seed INSERT must set all of them
# (plus tenant_id; on a live DB, \d {table} is authoritative)

# strict or dual-scope (this skill covers strict; dual-scope is pending Q7)
grep -n "TenantBase\|DualScopeBase" openaev-model/src/main/java/io/openaev/database/model/{Entity}.java
```

Record what you found: the case matrix below is driven by it.

## The case matrix

**Always generated** (every table, every API):

| Case | Expected |
|---|---|
| read own row under own tenant path | 200 |
| read other tenant's row under own path | 404 |
| list/search under a tenant path | only that tenant's rows |
| list/search with `X-Tenant-Ids` header on the plain route | only that tenant's rows |
| fail-closed (repository read with no scope in the test transaction) | zero rows, raw JDBC proves the row exists |

**Generated only if the API has the endpoint** (from the Step 0 probe):

| Endpoint present | Cases |
|---|---|
| PUT update | cross-tenant update → 404, ground truth proves the row untouched |
| DELETE | own delete → 2xx and gone; cross-tenant delete → 2xx no-op, ground truth proves the row survives |
| POST create | row stored with the path's tenant (assert `tenant_id` with a raw-JDBC read, not an `entityManager` query, which the inspector rewrites); create with no selector → 400 |
| import | same two attribution cases as create |
| upsert | upsert under A of a key already seeded in B → a new row for A next to B's (raw JDBC proves both rows and the new row's tenant); upsert with no selector → 400 |
| duplicate / export | copy inherits the tenant; export skips out-of-scope ids |
| options / search-by-id / any batch id lookup | asking for both tenants' ids under A's path resolves only A's (the enumeration surface: guessed or replayed ids must not resolve) |

Do not generate a case for an endpoint that does not exist: the test must
compile. The write-attribution cases are generated whenever the endpoint
exists, even if `TenantWriteScopeResolver` is not wired yet: they are the red
of the activation TDD and go green when the wiring lands.

**Scaffolded as pending, never active**: the dual-scope platform-row write
(`roles`, `groups`, `parameters`). The sanctioned platform-write path does not
exist yet (Q7). The template holds a commented block for it; emitting it active
would test a path that cannot work.

## v2 semantics, each with its reason

| Rule | Reason |
|---|---|
| One tenant path per test method | the scope is set once per transaction; `TenantScopeTransactionAspect` refuses to redefine it. Testing the other tenant means a new request in a new test. |
| Seed with a native `INSERT ... VALUES` carrying an explicit `tenant_id`, not through the API | the setup seeds **two** tenants (A and B). A MockMvc create joins the test transaction and sets the scope; two API creates would set it **twice in one transaction** and `TenantScopeTransactionAspect` throws (same reason as one-tenant-per-test). Native inserts set no scope, so both rows land cleanly; they also let a read test run without depending on the create endpoint working. This is a hard v2 constraint, not a style choice: a second API create to a different tenant throws `IllegalStateException` ("Tenant scope … is already set for this transaction … tried to change it"). |
| Ground-truth reads in raw JDBC on the test's own connection | the inspector only rewrites Hibernate-emitted SQL; raw JDBC sees the table unfiltered. This is how the test proves a cross-tenant write really did not happen, and that a hidden row really exists. `entityManager.flush()` first, so pending scoped writes reach the database. |
| Cross-tenant DELETE asserts 2xx and a surviving row, not 404 | the inspector adds `can_access_tenant` to the DELETE's WHERE; the statement matches no row and succeeds. Expecting 404 is v1 reasoning and fails on correct v2 code. |
| Cross-tenant UPDATE asserts 404 | the handler looks the row up first inside the scope; the lookup finds nothing. |
| `@TestPropertySource(properties = "openaev.tenant.active-tables={table}")` on the class | activates the table for this test only, before the production go-live. The test classpath keeps the allowlist empty on purpose; never add the table to test-wide properties. |
| Dedicated test file, not a nested class in `*ApiTest` | `@TestPropertySource` is class-level and forks the Spring context. On `*ApiTest` it would impose the activated context on every unrelated test in the class. This diverges from the old nested-class convention deliberately. |
| Admin user is fine for the isolation cases | the scope comes from tenant membership, never from the isAdmin flag. The non-admin proof exists once at plumbing level (`ImportMapperNonAdminIsolationTest`, `TenantSelectorMembershipTest` for the 403); per-table tests do not re-prove it. |

On boilerplate: the seed stays a native insert (see the rule above), but the
rest does not have to be hand-rolled. For a **create/write** test, build the
input DTO from the entity's `*Fixture` if one exists instead of inlining
setters. And the generic JDBC plumbing of the ground-truth helpers
(`doReturningWork` + `PreparedStatement`) is identical across tables — factor
it into a shared test base and keep only the per-table SQL string in each test.

## Red and green: what to expect before the wiring

Generated for a not-yet-wired table, the test is the red of the activation TDD.
Know which failures mean what:

- **positive cases (own row → 200) are the reliable red signal**: no `TxCtx` on
  the handler means no scope, fail-closed, so even the legitimate read fails.
- negative cases (cross-tenant → 404) can pass for the wrong reason. If the API
  is not mapped on the tenant path yet (`TENANT_PREFIX` missing from
  `@RequestMapping`), every path-route request 404s, including the ones that
  should be 200. Never trust a green negative case while a positive case is red.
  Adding that mapping is part of the activation wiring (model: `MapperApi`).
- the header-route case stays red while the v1 `@Filter` is still on the
  entity (v1 falls back to the default tenant on the plain route and its
  predicate contradicts v2's). `@Disabled` it with a comment naming the
  go-live, exactly like the pilot did; the activation go-live re-enables it.
- the "create with no selector → 400" case is the reliable attribution red: it
  stays red until `TenantWriteScopeResolver` is wired. The attribution-under-
  path case may already be green before wiring: on `/api/tenants/{id}/...`
  routes the v1 interceptor sets the thread-local and the v1 listener stamps
  the right tenant. It stays green after wiring, so it is a valid case, just
  not a red signal.

Two rules the validation run on `kill_chain_phases` proved the hard way:
- a MockMvc call joins the test's transaction, so the first request's scope IS
  the test's scope. Never call two different tenant paths in one test method;
  that is the same one-tenant-per-test rule, seen from the transaction side.
- ground-truth asserts must use raw JDBC, never an `entityManager` native
  query: the inspector rewrites the latter, so its result flips with the
  transaction's scope and reads differently before and after wiring.

Keep the raw red output: the activation workflow requires it as evidence.

## Context cost

`@TestPropertySource` forks one Spring context per distinct property value.
Every pre-go-live table therefore adds one context to the CI run; that is the
price of proving activation before it is live, keep it. After the table's
go-live, align the class's property value with the other activated tables'
test classes: an identical value means a shared, cached context. Once several
tables are live, hold that value in one shared test constant so it cannot
drift between files.

## Run

```bash
# needs the Docker services from openaev-dev/docker-compose.yml
mvn -ntp -pl openaev-api test -Dtest='{Entity}HttpIsolationTest'
```

## File placement and naming

`openaev-api/src/test/java/io/openaev/rest/{domain}/{Entity}HttpIsolationTest.java`,
next to the API under test. One file per table.
