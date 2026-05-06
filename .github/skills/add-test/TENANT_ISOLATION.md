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

Also add imports:
```java
import io.openaev.utils.TenantIsolationTestHelper;
import java.util.Set;
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

### Step 4 — Implement Test Methods

Use this template. Replace placeholders:
- `{Entity}` — entity name (e.g., `Scenario`, `Group`)
- `{entity}` — lowercase (e.g., `scenario`, `group`)
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

  {CreateInput} input = new {CreateInput}();
  input.setName("RLS Isolation Test");

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

#### Test 2 — Same-tenant READ works

```java
@Test
@DisplayName("{Entity} created in tenant X should be readable from tenant X")
void given_{entity}InTenantX_should_beReadableFromTenantX() throws Exception {
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = new {CreateInput}();
  input.setName("Same Tenant Entity");

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

  {CreateInput} input = new {CreateInput}();
  input.setName("CrossTenantSearch");

  mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful());

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

  {CreateInput} input = new {CreateInput}();
  input.setName("Update Isolation Test");

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

  // -------- Act — update from tenant Y --------
  {CreateInput} updateInput = new {CreateInput}();
  updateInput.setName("Hijacked Name");

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
  // -------- Arrange --------
  Tenant tenantX =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY =
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y", Set.of({DELETE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = new {CreateInput}();
  input.setName("Delete Isolation Test");

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

  // -------- Act — delete from tenant Y --------
  int responseStatus =
      mvc.perform(
              delete("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andReturn()
          .getResponse()
          .getStatus();

  // -------- Assert --------
  assertTrue(
      responseStatus == 403 || responseStatus == 404,
      "Expected 403 or 404 but got " + responseStatus
          + " — cross-tenant delete was NOT blocked");
}
```

### Step 5 — Verify

```bash
mvn test -pl openaev-api -Dtest="{Feature}ApiTest\$TenantIsolation"
```

## Key Points

| Concern | How it's handled |
|---|---|
| RBAC | `createTenantWithCapabilities` creates Role → Group → User in the tenant |
| Tenant context | The `/api/tenants/{tenantId}/...` path sets `TenantContext` via interceptor |
| Expected responses | Cross-tenant operations return **403 or 404** (both acceptable — permission layer or data layer blocks) |
| flush/clear | `TenantIsolationTestHelper` flushes and clears the EntityManager so `userService.currentUser()` sees the new groups |
| No `isAdmin=true` | Tests use real capabilities to verify both RBAC and data isolation together |

## Reference Implementations

- `ScenarioApiTest.TenantIsolation` — grant-based resource (SCENARIO in `RESOURCES_MANAGED_BY_GRANTS`)
- `TenantGroupApiTest.TenantIsolation` — capability-only resource (USER_GROUP)

