package io.openaev.api.import_mapper;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.import_mapper.form.ExportMapperInput;
import io.openaev.api.import_mapper.form.ImportMapperAddInput;
import io.openaev.api.import_mapper.form.ImportMapperUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code import_mappers} activated, the tenant scope set from the URL
 * path isolates the table through the real {@link MapperApi} endpoints (not just at the SQL layer,
 * which {@code ImportMapperTenantIsolationTest} already covers). A user who belongs to two tenants
 * sees a mapper only under its own tenant's path, never another tenant's, proving the request
 * binding actually reaches the rewriter in production wiring.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("import_mappers read and write isolation through the real HTTP endpoint")
class ImportMapperHttpIsolationTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/tenants/{tenantId}/mappers/{mapperId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String mapperA;
  private String mapperB;

  @BeforeEach
  void seedTwoTenantsWithOneMapperEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("http-iso-b").getId();
    mapperA = seedMapper(tenantA, "mapper-a");
    mapperB = seedMapper(tenantB, "mapper-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's mapper is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(MAPPER_BY_ID, tenantA, mapperA)).andExpect(status().isOk());
    mvc.perform(get(MAPPER_BY_ID, tenantA, mapperB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's mapper is visible, A's is hidden")
  void underTenantBPath() throws Exception {
    mvc.perform(get(MAPPER_BY_ID, tenantB, mapperB)).andExpect(status().isOk());
    mvc.perform(get(MAPPER_BY_ID, tenantB, mapperA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header (no path tenant): search returns A's mapper and not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/mappers/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(mapperA), "A's mapper must appear when A is selected via header");
    assertFalse(response.contains(mapperB), "B's mapper must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's mapper and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/mappers/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(mapperA), "A's mapper must appear in A's search results");
    assertFalse(response.contains(mapperB), "B's mapper must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    ImportMapperAddInput input = new ImportMapperAddInput();
    input.setName("created-under-a");
    input.setInjectTypeColumn("A");
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/mappers", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.import_mapper_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT tenant_id FROM import_mappers WHERE mapper_id = CAST(:id AS uuid)")
                .setParameter("id", createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created mapper must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    ImportMapperAddInput input = new ImportMapperAddInput();
    input.setName("no-selector");
    input.setInjectTypeColumn("A");
    mvc.perform(
            post("/api/mappers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own mapper")
  void updateUnderTenantAUpdatesOwnMapper() throws Exception {
    mvc.perform(
            put(MAPPER_BY_ID, tenantA, mapperA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("renamed-a")))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(mapperA), "A's own mapper must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's mapper is not found and leaves it untouched")
  void updateUnderTenantAOfBMapperIsBlocked() throws Exception {
    mvc.perform(
            put(MAPPER_BY_ID, tenantA, mapperB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(updateInput("hijacked")))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("mapper-b", rawName(mapperB), "B's mapper must be untouched by tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own mapper")
  void deleteUnderTenantADeletesOwnMapper() throws Exception {
    mvc.perform(delete(MAPPER_BY_ID, tenantA, mapperA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(mapperA), "A's own mapper must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's mapper is a no-op and leaves it in place")
  void deleteUnderTenantAOfBMapperIsBlocked() throws Exception {
    mvc.perform(delete(MAPPER_BY_ID, tenantA, mapperB).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(1L, rawCount(mapperB), "B's mapper must survive tenant A's delete attempt");
  }

  @Test
  @DisplayName(
      "under tenant A's path: export returns A's mapper and skips B's even if B's id is asked")
  void exportUnderTenantAReturnsOnlyA() throws Exception {
    ExportMapperInput input = new ExportMapperInput();
    input.setName("export-test");
    input.setIdsToExport(List.of(mapperA, mapperB));
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/mappers/export", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains("mapper-a"), "A's mapper must be in A's export");
    assertFalse(response.contains("mapper-b"), "B's mapper must not be in A's export");
  }

  @Test
  @DisplayName("under tenant A's path: duplicating A's mapper attributes the copy to tenant A")
  void duplicateUnderTenantAIsAttributedToA() throws Exception {
    String response =
        mvc.perform(post(MAPPER_BY_ID, tenantA, mapperA).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String copyId = JsonPath.read(response, "$.import_mapper_id");
    String copyTenant =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT tenant_id FROM import_mappers WHERE mapper_id = CAST(:id AS uuid)")
                .setParameter("id", copyId)
                .getSingleResult();
    assertEquals(tenantA, copyTenant, "the duplicated mapper must belong to tenant A");
  }

  @Test
  @DisplayName("under tenant A's path: duplicating B's mapper is not found (cross-tenant blocked)")
  void duplicateUnderTenantAOfBMapperIsBlocked() throws Exception {
    mvc.perform(post(MAPPER_BY_ID, tenantA, mapperB).with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: imported mappers are attributed to tenant A")
  void importUnderTenantAIsAttributedToA() throws Exception {
    mvc.perform(
            multipart("/api/tenants/{tenantId}/mappers/import", tenantA)
                .file(mapperFile())
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
    String tenant =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT tenant_id FROM import_mappers WHERE mapper_name LIKE 'imported%'")
                .getSingleResult();
    assertEquals(tenantA, tenant, "an imported mapper must belong to tenant A");
  }

  @Test
  @DisplayName("an import with no tenant selector is refused (a single-tenant scope is required)")
  void importWithoutSelectorIsRejected() throws Exception {
    mvc.perform(multipart("/api/mappers/import").file(mapperFile()).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  private static MockMultipartFile mapperFile() {
    String json =
        "[{\"import_mapper_name\":\"imported\",\"import_mapper_inject_type_column\":\"A\"}]";
    return new MockMultipartFile(
        "file", "mappers.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
  }

  private static ImportMapperUpdateInput updateInput(String name) {
    ImportMapperUpdateInput input = new ImportMapperUpdateInput();
    input.setName(name);
    input.setInjectTypeColumn("A");
    return input;
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String mapperId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT mapper_name FROM import_mappers WHERE mapper_id = CAST(? AS uuid)")) {
                statement.setString(1, mapperId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String mapperId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM import_mappers WHERE mapper_id = CAST(? AS uuid)")) {
                statement.setString(1, mapperId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedMapper(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, :col, :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("col", "inject_type")
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
