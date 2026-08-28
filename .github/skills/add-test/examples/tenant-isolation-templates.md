# v2 Tenant Isolation Test Templates

> Referenced by [TENANT_ISOLATION.md](../TENANT_ISOLATION.md).
> The structure and the core read/write patterns come from the real pilot,
> `openaev-api/src/test/java/io/openaev/rest/mapper/ImportMapperHttpIsolationTest.java`
> (and `TenantScopeFailClosedTest.java` for the fail-closed case), with
> placeholders where the pilot had import_mappers specifics. A few snippets
> have no pilot equivalent: they either generalize a pilot pattern to a case
> the pilot lacks (`rawTenant`, the upsert) or bring back a case from the v1
> skill (the options/batch lookup). Each such snippet is marked where it
> appears; all of them follow the same raw-JDBC, one-tenant-per-test rules.

## Placeholders

| Placeholder | Pilot value | How to derive |
|---|---|---|
| `{table}` | `import_mappers` | the physical table name |
| `{Entity}` | `ImportMapper` | the entity class |
| `{Api}` | `MapperApi` | the controller class |
| `{EntityRepository}` | `ImportMapperRepository` | the Spring Data repository |
| `{domain}` | `mapper` | the API's package under `io.openaev.rest` |
| `{plain_uri}` | `/api/mappers` | the API's base `@RequestMapping` |
| `{entities}` | `mappers` | the URI segment of the API |
| `{id_column}` | `mapper_id` | from the CREATE TABLE migration |
| `{id_sql}` | `CAST(? AS uuid)` | `?` if the id column is varchar; CAST if uuid |
| `{seed_columns}` | `mapper_id, mapper_name, mapper_inject_type_column, tenant_id` | every NOT NULL column without a default, plus tenant_id |
| `{name_column}` | `mapper_name` | the human-readable column used in ground-truth asserts |
| `{id_json_path}` | `$.import_mapper_id` | the id field in the API's JSON output |

Only the placeholders in this table get substituted. In URI strings,
`{tenantId}` and `{entityId}` (e.g. `{phaseId}`, `{mapperId}`) are Spring URI
template variables: `mvc.perform(get(URI, tenantA, rowA))` fills them at run
time, so they stay as-is in the copied code.

## The canonical file

```java
package io.openaev.rest.{domain};

// imports: same set as ImportMapperHttpIsolationTest

/**
 * End-to-end proof that, with {table} activated, the tenant scope set from the
 * request isolates the table through the real {Api} endpoints.
 *
 * <p>Each test stays on a single tenant path: the per-request scope is set once
 * and the aspect refuses to redefine it inside one transaction.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables={table}")
@WithMockUser(isAdmin = true)
// @CoversTenantIsolation("{table}") // uncomment when the tenant-scope-coverage CI gate (#6389) lands
@DisplayName("{table} read and write isolation through the real HTTP endpoint")
class {Entity}HttpIsolationTest extends IntegrationTest {

  private static final String BY_ID = "/api/tenants/{tenantId}/{entities}/{entityId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private {EntityRepository} repository; // for the fail-closed case

  private String tenantA;
  private String tenantB;
  private String rowA;
  private String rowB;

  @BeforeEach
  void seedTwoTenantsWithOneRowEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("http-iso-b").getId();
    rowA = seedRow(tenantA, "row-a");
    rowB = seedRow(tenantB, "row-b");
  }

  // ... cases from the matrix, see below ...

  // -- helpers, all from the pilot --

  // Native insert, not an API create: the setup seeds two tenants, and two
  // MockMvc creates would set the tenant scope twice in one transaction, which
  // TenantScopeTransactionAspect rejects. See the "why native seed" rule in
  // TENANT_ISOLATION.md.
  private String seedRow(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO {table} ({seed_columns})"
                + " VALUES (...explicit values incl. :tenant...)")
        .setParameter("tenant", tenantId)
        // one setParameter per seed column
        .executeUpdate();
    return id;
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own
  // connection sees the uncommitted seed and the rewriter does not touch a
  // statement it never generated. A flush first forces any pending scoped
  // UPDATE/DELETE to reach the database.
  private String rawName(String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT {name_column} FROM {table} WHERE {id_column} = {id_sql}")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM {table} WHERE {id_column} = {id_sql}")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
```

## Always-generated cases

### Read: own row visible, other tenant's row hidden

```java
@Test
@DisplayName("under tenant A's path: A's row is visible, B's is hidden")
void underTenantAPath() throws Exception {
  mvc.perform(get(BY_ID, tenantA, rowA)).andExpect(status().isOk());
  mvc.perform(get(BY_ID, tenantA, rowB)).andExpect(status().isNotFound());
}
```

### Search or list, under the path and via the header

```java
@Test
@DisplayName("under tenant A's path: search returns A's row and not B's")
void searchUnderTenantAReturnsOnlyA() throws Exception {
  String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
  String response =
      mvc.perform(
              post("/api/tenants/{tenantId}/{entities}/search", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
  assertTrue(response.contains(rowA), "A's row must appear in A's search results");
  assertFalse(response.contains(rowB), "B's row must not appear in A's search results");
}

@Test
@DisplayName("via the X-Tenant-Ids header (no path tenant): search returns A's row and not B's")
void searchViaHeaderReturnsOnlyA() throws Exception {
  // While the v1 @Filter is still on the entity, this case fails (v1 falls back
  // to the default tenant on the plain route and contradicts v2's predicate).
  // @Disabled it with a comment naming the go-live, like the pilot did; the
  // activation go-live re-enables it.
  String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
  String response =
      mvc.perform(
              post("{plain_uri}/search")
                  .header("X-Tenant-Ids", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
  assertTrue(response.contains(rowA));
  assertFalse(response.contains(rowB));
}
```

If the API has a plain `GET` list instead of a search endpoint, assert on the
list response body the same way.

### Fail-closed (model: `TenantScopeFailClosedTest.java`)

```java
@Test
@DisplayName("no scope set: the read is empty although the row exists")
void readWithoutScopeReturnsNothing() {
  // No TxCtx in this transaction, so the aspect never set app.current_tenants
  // and the inspector's can_access_tenant denies every row. Fail-closed, not
  // fail-open: this is what protects the table when an endpoint loses its
  // TxCtx.
  assertEquals(0L, repository.count(), "a scope-less read must see no rows");
  assertEquals(1L, rawCount(rowA), "the row exists, it is only hidden");
}
```

## Conditional cases (generate only if the endpoint exists)

### Update (PUT)

```java
@Test
@DisplayName("under tenant A's path: updating B's row is not found and leaves it untouched")
void updateUnderTenantAOfBRowIsBlocked() throws Exception {
  mvc.perform(
          put(BY_ID, tenantA, rowB)
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(updateInput("hijacked")))
              .with(csrf()))
      .andExpect(status().isNotFound());
  assertEquals("row-b", rawName(rowB), "B's row must be untouched by tenant A");
}
```

### Delete: v2 semantics, a cross-tenant delete is a 2xx no-op

```java
@Test
@DisplayName("under tenant A's path: A can delete its own row")
void deleteUnderTenantADeletesOwnRow() throws Exception {
  mvc.perform(delete(BY_ID, tenantA, rowA).with(csrf()))
      .andExpect(status().is2xxSuccessful());
  assertEquals(0L, rawCount(rowA), "A's own row must be deleted");
}

@Test
@DisplayName("under tenant A's path: deleting B's row is a no-op and leaves it in place")
void deleteUnderTenantAOfBRowIsBlocked() throws Exception {
  // v2: the inspector adds can_access_tenant to the DELETE's WHERE; the
  // statement matches no row and succeeds. 2xx + surviving row, NOT 404.
  mvc.perform(delete(BY_ID, tenantA, rowB).with(csrf()))
      .andExpect(status().is2xxSuccessful());
  assertEquals(1L, rawCount(rowB), "B's row must survive tenant A's delete attempt");
}
```

### Create: write attribution (also for import; red until the resolver is wired)

```java
@Test
@DisplayName("a create under tenant A's path is attributed to tenant A")
void createUnderTenantAIsAttributedToA() throws Exception {
  String response =
      mvc.perform(
              post("/api/tenants/{tenantId}/{entities}", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(createInput("created-under-a")))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
  String createdId = JsonPath.read(response, "{id_json_path}");
  // Raw JDBC, not an entityManager native query: the inspector rewrites the
  // latter, so its result would depend on the transaction's scope. Raw JDBC
  // reads the same in every state (before wiring, after wiring, red or green).
  assertEquals(tenantA, rawTenant(createdId), "the created row must belong to tenant A");
}
```

`rawTenant` is one more raw-JDBC helper next to the pilot's `rawName`/`rawCount`
(the pilot does not have `rawTenant` itself), selecting `tenant_id` by id.

```java

@Test
@DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
void createWithoutSelectorIsRejected() throws Exception {
  mvc.perform(
          post("{plain_uri}")
              .contentType(MediaType.APPLICATION_JSON)
              .content(asJsonString(createInput("no-selector")))
              .with(csrf()))
      .andExpect(status().isBadRequest());
}
```

### Upsert (only if the API has one)

> No pilot equivalent: `import_mappers` has no upsert. Generalized from the
> pilot's write-attribution and raw-JDBC patterns.

One tenant per test also holds here: a MockMvc call joins the test's
transaction, so calling two tenant paths in one test would set the scope
twice and hit the nesting guard. Instead, seed the business key in tenant B
during setup and upsert it under tenant A only. Ground truth in raw JDBC:
a count through the entityManager would be rewritten by the inspector and
would depend on the scope.

```java
@Test
@DisplayName("upserting a key that exists in another tenant creates a new row, not a takeover")
void upsertOfKeySeededInBCreatesARowForA() throws Exception {
  // rowB was seeded with the same business key in tenant B during setup.
  // Requires tenant-aware unique constraints on the business key; a
  // unique-violation failure here means the schema prep was skipped
  // (see the activate-tenant-table skill, PR #6594, gate 0.3).
  mvc.perform(post("/api/tenants/{tenantId}/{entities}/upsert", tenantA)
          .contentType(MediaType.APPLICATION_JSON)
          .content(asJsonString(upsertInput("shared-key"))).with(csrf()))
      .andExpect(status().isOk());
  assertEquals(2L, rawCountByKey("shared-key"),
      "A's upsert must create its own row next to B's, not touch B's");
  assertEquals(tenantA, rawTenantOfOtherRow("shared-key", rowB),
      "the new row must belong to tenant A");
  // rawCountByKey / rawTenantOfOtherRow: raw JDBC helpers like rawCount,
  // keyed on the business key column (the second one excludes B's known id).
}
```

### Batch lookups: options / search-by-id (only if the API has one)

> No pilot equivalent: `import_mappers` has no batch id lookup. The v1 skill
> covered this case; it is kept here on the same read-isolation pattern.

Batch id lookups are the enumeration surface: a caller who guesses or replays
ids from another tenant must not resolve them. The old v1 skill covered this
(search-by-ids); keep covering it in v2.

```java
@Test
@DisplayName("under tenant A's path: options by ids return A's row and not B's")
void optionsUnderTenantAReturnOnlyAsRow() throws Exception {
  String response =
      mvc.perform(
              post("/api/tenants/{tenantId}/{entities}/options", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of(rowA, rowB)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
  assertTrue(response.contains(rowA), "A's row must be resolvable as an option");
  assertFalse(response.contains(rowB), "B's row must not leak through the options endpoint");
}
```

## Pending scaffold (never emitted active)

```java
// PENDING (Q7): platform-row write on a dual-scope table. The sanctioned
// platform-write path does not exist yet; a tenant scope refuses platform
// rows on writes by design. Activate this case when the platform-write
// policy lands.
// @Test
// void platformRowWriteOnDualScopeTable() { ... }
```
