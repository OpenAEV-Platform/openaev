---
name: add-tenant-isolation-test
description: >-
  Adds tenant isolation tests to an existing API test class. Verifies that
  tenant-scoped entities cannot leak between tenants (cross-tenant read, search,
  update, delete). Uses TenantIsolationTestHelper to set up real RBAC
  (capabilities/tenants/groups/roles) instead of isAdmin=true.
  Use when a tenant-scoped API (path contains /api/tenants/{tenantId}/...) is
  missing isolation tests.
---

# Add Tenant Isolation Tests

## When to Use

- An API endpoint has a tenant-scoped path: `/api/tenants/{tenantId}/...`
- The entity table has a `tenant_id` column (listed in `TenantScopedTables.java`)
- The existing `*ApiTest.java` does NOT have a `TenantIsolation` nested class

## Prerequisites

- The API test class extends `IntegrationTest`
- `TenantIsolationTestHelper` is available (`@Autowired`)
- The entity has tenant-scoped CRUD endpoints

## Procedure

### Step 1 — Identify the API and Required Capabilities

1. Open the API controller (e.g., `ScenarioApi.java`, `TenantGroupApi.java`)
2. Find the `@AccessControl` annotations on CRUD methods
3. Note the `resourceType` and `actionPerformed` values
4. Map to capabilities using `Capability.of(resourceType, action)`:
   - CREATE → `MANAGE_*` capability
   - READ → `ACCESS_*` capability
   - SEARCH → `ACCESS_*` capability (search endpoints are open for grantable resources)
   - WRITE → `MANAGE_*` capability
   - DELETE → `DELETE_*` capability

### Step 2 — Add TenantIsolationTestHelper to the Test Class

```java
@Autowired private TenantIsolationTestHelper tenantIsolationHelper;
```
We also need the `EntityManager` to populate with pre-requisites data.
```java
@Autowired private jakarta.persistence.EntityManager entityManager;
```

Also add imports:
```java
import io.openaev.utils.TenantIsolationTestHelper;
import jakarta.persistence.EntityManager;
```

### Step 3 — Create the Nested `TenantIsolation` Class

Add a `@Nested` class at the end of the test class with `@WithMockUser` (bare — no
capabilities via annotation; real capabilities come from DB via `createTenantWithCapabilities`).

```java
@Nested
@DisplayName("Tenant Isolation")
@WithMockUser
class TenantIsolation {
  // tests go here
}
```

### Step 4 — Evict Hibernate L1 Cache Between Create and Cross-Tenant Access

**Critical**: When a test creates an entity and then reads it within the same `@Transactional`
test, Hibernate's L1 (session) cache may return the entity directly **without hitting the
database**. Since RLS filtering happens at the PostgreSQL level, a cached `findById()` bypasses
RLS entirely — making the test pass even when isolation is broken.

**Always call `entityManager.flush()` + `entityManager.clear()` between the create and the
cross-tenant access** to force Hibernate to issue a real SQL query that goes through RLS.

> NOTE: this is done in switchToTenant() for example.
```java
public void switchToTenant(String tenantId, EntityManager entityManager) {
  entityManager.flush();
  entityManager.clear();
  ...
}
```
### Step 5 — Implement Test Methods

Use this template. Replace placeholders:
- `{Entity}` — entity name (e.g., `Scenario`, `Group`)
- `{entity}` — lowercase (e.g., `scenario`, `group`)
- `{EntityFixture}` — fixture class name (e.g., `AssetGroupFixture`, `ExerciseFixture`)
- `{MANAGE_CAP}` — capability for create/update (e.g., `Capability.MANAGE_ASSESSMENT`)
- `{ACCESS_CAP}` — capability for read/search (e.g., `Capability.ACCESS_ASSESSMENT`)
- `{DELETE_CAP}` — capability for delete (e.g., `Capability.DELETE_ASSESSMENT`)
- `{TENANT_URI}` — tenant-scoped URI (e.g., `/api/tenants/{tenantId}/scenarios`)
- `{entity_id_json_path}` — JSON path to entity ID in response (e.g., `$.scenario_id`)
- `{entity_name_json_path}` — JSON path to entity name (e.g., `$.scenario_name`)
- `{CreateInput}` — DTO class for create (e.g., `ScenarioInput`)

#### Test 1 — Cross-tenant READ blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be readable from tenant Y")
void given_{entity}InTenantX_should_notBeReadableFromTenantY() throws Exception {
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y", Set.of({ACCESS_CAP}));

  // Use the entity's Fixture class to create the input DTO
  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("RLS Isolation Test");

  String createResponse =
      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/{entities}")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  // Evict L1 cache so findById() hits the DB (where RLS filters)
  entityManager.flush();
  entityManager.clear();

  // -------- Act — read from tenant Y (expect 403 or 404) --------
  int responseStatus =
      mvc.perform(
              get("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andReturn()
          .getResponse()
          .getStatus();

  // -------- Assert --------
  assertTrue(
      responseStatus == 403 || responseStatus == 404,
      "Expected 403 or 404 but got " + responseStatus
          + " — cross-tenant read was NOT blocked");
}
```

> **Fixture convention**: Each entity should have a Fixture class (e.g., `AssetGroupFixture`,
> `ExerciseFixture`) with a factory method that creates a default input DTO. Use
> `{EntityFixture}.createDefault{CreateInput}("name")` instead of inline
> `new {CreateInput}()` + `setName(...)`. If the fixture method doesn't exist yet, add it
> following the pattern in `AssetGroupFixture.createDefaultAssetGroupInput()`.
>
> **Composer convention**: When creating entities directly (bypassing the REST API), use the
> entity's Composer (e.g., `injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get()`)
> combined with `tenantIsolationHelper.switchToTenant()` to set the correct tenant context.

#### Test 2 — Same-tenant READ works

```java
@Test
@DisplayName("{Entity} created in tenant X should be readable from tenant X")
void given_{entity}InTenantX_should_beReadableFromTenantX() throws Exception {
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("Same Tenant Entity");

  String createResponse =
      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/{entities}")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  // -------- Act & Assert — read from same tenant should succeed --------
  mvc.perform(
          get("/api/tenants/" + tenantX.getId() + "/{entities}/" + entityId)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("{entity_name_json_path}").value("Same Tenant Entity"));
}
```

#### Test 3 — Cross-tenant SEARCH filtered

```java
@Test
@DisplayName("{Entity} search in tenant Y should NOT return entities from tenant X")
void given_{entity}InTenantX_should_notAppearInTenantYSearch() throws Exception {
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y", Set.of({ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("CrossTenantSearch");

  mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful());

  // Evict L1 cache
  entityManager.flush();
  entityManager.clear();

  // -------- Act — search from tenant Y --------
  SearchPaginationInput searchInput = new SearchPaginationInput();
  searchInput.setTextSearch("CrossTenantSearch");
  searchInput.setSize(100);
  searchInput.setPage(0);

  String searchResponse =
      mvc.perform(
              post("/api/tenants/" + tenantY.getId() + "/{entities}/search")
                  .content(asJsonString(searchInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

  // -------- Assert — no results from tenant X --------
  assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
}
```

#### Test 4 — Cross-tenant UPDATE blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be updatable from tenant Y")
void given_{entity}InTenantX_should_notBeUpdatableFromTenantY() throws Exception {
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y", Set.of({MANAGE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("Update Isolation Test");

  String createResponse =
      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/{entities}")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  // Evict L1 cache
  entityManager.flush();
  entityManager.clear();

  // -------- Act — update from tenant Y --------
  {CreateInput} updateInput = {EntityFixture}.createDefault{CreateInput}("Hijacked Name");

  int responseStatus =
      mvc.perform(
              put("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
                  .content(asJsonString(updateInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andReturn()
          .getResponse()
          .getStatus();

  // -------- Assert --------
  assertTrue(
      responseStatus == 403 || responseStatus == 404,
      "Expected 403 or 404 but got " + responseStatus
          + " — cross-tenant update was NOT blocked");
}
```

#### Test 5 — Cross-tenant DELETE blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be deletable from tenant Y")
void given_{entity}InTenantX_should_notBeDeletableFromTenantY() throws Exception {
  // ... (same pattern as above)
}
```

### Step 6 — Use `@WithoutRls` to Identify APIs That Rely Solely on RLS

The `@WithoutRls` annotation disables PostgreSQL Row-Level Security for a single test method
by executing `RESET ROLE` (switching to superuser, which bypasses RLS). After the test,
`SET ROLE openaev_app` is restored automatically.

**Purpose**: distinguish between two levels of tenant isolation:

| Layer | Mechanism | What `@WithoutRls` reveals |
|-------|-----------|---------------------------|
| **Application-level** | `findByIdAndTenantId()`, Hibernate `@Filter`, `WHERE tenant_id = ?` in native queries | Test still passes with `@WithoutRls` → ✅ app code filters correctly |
| **RLS (safety net)** | PostgreSQL RLS policy on `openaev_app` role | Test fails with `@WithoutRls` → ⚠️ only RLS protects this endpoint |

**How to use**:

1. Write the tenant isolation test normally (it should pass with RLS active)
2. Add `@WithoutRls` to the test method
3. Run the test again:
   - **Still passes (403/404)** → the application code properly scopes by tenant ✅
   - **Fails (200)** → the API relies on RLS as its only protection ⚠️ — flag for a
     production code fix (add `findByIdAndTenantId` or tenant-scoped query)

```java
@Test
@WithoutRls // Disables RLS → test checks if app-level filtering exists
@DisplayName("{Entity} created in tenant X should NOT be readable from tenant Y")
void given_{entity}InTenantX_should_notBeReadableFromTenantY() throws Exception {
  // ... same test body ...
  // If this returns 200 instead of 403/404, the API has no app-level tenant check
}
```

**Example from `PayloadApiTest`** — commented toggle for diagnostic use:

```java
@Test
@DisplayName("Payload created in tenant X should NOT be readable from tenant Y")
//@WithoutRls // uncomment to verify: does app-level filtering exist, or only RLS?
void given_payloadInTenantX_should_notBeReadableFromTenantY() throws Exception {
  // ...
}
```

**Implementation details** (`WithoutRls.java` + `RlsToggleExtension.java`):
- `@WithoutRls` is a JUnit 5 composed annotation with `@ExtendWith(RlsToggleExtension.class)`
- `RlsToggleExtension.beforeEach()` → `RESET ROLE` (superuser bypasses RLS)
- `RlsToggleExtension.afterEach()` → `SET ROLE openaev_app` (restore RLS)
- Works inside `@Transactional` tests because it uses native SQL on the current connection

> **When to `@Disabled` vs `@WithoutRls`**: Use `@Disabled` with a FIXME when a test reveals
> a real vulnerability (e.g., `deleteById()` without tenant check returns 200). Use
> `@WithoutRls` as a diagnostic tool to check whether app-level filtering exists — don't
> leave it uncommented in committed tests (RLS should remain active by default).



