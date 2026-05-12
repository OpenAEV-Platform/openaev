# Tenant Isolation Test Templates

> Referenced by [TENANT_ISOLATION.md](../TENANT_ISOLATION.md) — Step 5.

## Placeholders

| Placeholder | Example |
|---|---|
| `{Entity}` | `Scenario`, `AssetGroup` |
| `{entity}` | `scenario`, `assetGroup` |
| `{EntityFixture}` | `AssetGroupFixture`, `ExerciseFixture` |
| `{MANAGE_CAP}` | `Capability.MANAGE_ASSETS` |
| `{ACCESS_CAP}` | `Capability.ACCESS_ASSETS` |
| `{DELETE_CAP}` | `Capability.DELETE_ASSETS` |
| `{entities}` | `asset_groups`, `exercises` |
| `{entity_id_json_path}` | `$.asset_group_id` |
| `{entity_name_json_path}` | `$.asset_group_name` |
| `{CreateInput}` | `AssetGroupInput` |

## Real-world examples

- [`AssetGroupApiTest.TenantIsolation`](../../../../openaev-api/src/test/java/io/openaev/rest/asset_group/AssetGroupApiTest.java) — fixture + REST API create
- [`AtomicTestingApiTest.TenantIsolation`](../../../../openaev-api/src/test/java/io/openaev/rest/AtomicTestingApiTest.java) — composer + `switchToTenant()`
- [`ExerciseApiTest.TenantIsolation`](../../../../openaev-api/src/test/java/io/openaev/rest/exercise/ExerciseApiTest.java) — REST API create

## Status code expectations

Cross-tenant access typically returns **404** (Hibernate `@Filter` or RLS blocks the SELECT →
`ElementNotFoundException`).

---

## Test 1 — Cross-tenant READ blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be readable from tenant Y")
void given_{entity}InTenantX_should_notBeReadableFromTenantY() throws Exception {
  Tenant tenantX = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant Y", Set.of({ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("RLS Isolation Test");

  String createResponse = mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful())
      .andReturn().getResponse().getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  entityManager.flush();
  entityManager.clear();

  int responseStatus = mvc.perform(
          get("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andReturn().getResponse().getStatus();

  assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
}
```

## Test 2 — Same-tenant READ works

```java
@Test
@DisplayName("{Entity} created in tenant X should be readable from tenant X")
void given_{entity}InTenantX_should_beReadableFromTenantX() throws Exception {
  Tenant tenantX = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("Same Tenant Entity");

  String createResponse = mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful())
      .andReturn().getResponse().getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  mvc.perform(
          get("/api/tenants/" + tenantX.getId() + "/{entities}/" + entityId)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("{entity_name_json_path}").value("Same Tenant Entity"));
}
```

## Test 3 — Cross-tenant SEARCH filtered

```java
@Test
@DisplayName("{Entity} search in tenant Y should NOT return entities from tenant X")
void given_{entity}InTenantX_should_notAppearInTenantYSearch() throws Exception {
  Tenant tenantX = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant Y", Set.of({ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("CrossTenantSearch");

  mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful());

  entityManager.flush();
  entityManager.clear();

  SearchPaginationInput searchInput = PaginationFixture.simpleTextSearch("CrossTenantSearch");

  String searchResponse = mvc.perform(
          post("/api/tenants/" + tenantY.getId() + "/{entities}/search")
              .content(asJsonString(searchInput))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful())
      .andReturn().getResponse().getContentAsString();

  assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
}
```

## Test 4 — Cross-tenant UPDATE blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be updatable from tenant Y")
void given_{entity}InTenantX_should_notBeUpdatableFromTenantY() throws Exception {
  Tenant tenantX = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant Y", Set.of({MANAGE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("Update Isolation Test");

  String createResponse = mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful())
      .andReturn().getResponse().getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  entityManager.flush();
  entityManager.clear();

  {CreateInput} updateInput = {EntityFixture}.createDefault{CreateInput}("Hijacked Name");

  int responseStatus = mvc.perform(
          put("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
              .content(asJsonString(updateInput))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andReturn().getResponse().getStatus();

  assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
}
```

## Test 5 — Cross-tenant DELETE blocked

```java
@Test
@DisplayName("{Entity} created in tenant X should NOT be deletable from tenant Y")
void given_{entity}InTenantX_should_notBeDeletableFromTenantY() throws Exception {
  Tenant tenantX = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant X", Set.of({MANAGE_CAP}, {ACCESS_CAP}));
  Tenant tenantY = tenantIsolationHelper.createTenantWithCapabilities(
      "Tenant Y", Set.of({DELETE_CAP}, {ACCESS_CAP}));

  {CreateInput} input = {EntityFixture}.createDefault{CreateInput}("Delete Isolation Test");

  String createResponse = mvc.perform(
          post("/api/tenants/" + tenantX.getId() + "/{entities}")
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()))
      .andExpect(status().is2xxSuccessful())
      .andReturn().getResponse().getContentAsString();

  String entityId = JsonPath.read(createResponse, "{entity_id_json_path}");

  entityManager.flush();
  entityManager.clear();

  int responseStatus = mvc.perform(
          delete("/api/tenants/" + tenantY.getId() + "/{entities}/" + entityId)
              .with(csrf()))
      .andReturn().getResponse().getStatus();

  assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
}
```

