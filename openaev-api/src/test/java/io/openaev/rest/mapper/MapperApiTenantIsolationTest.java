package io.openaev.rest.mapper;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.rest.mapper.form.ImportMapperUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mapper isolation tests through the real API, asserting v2 behaviour: cross-tenant reads and
 * updates return 404, and a cross-tenant delete is a scoped no-op (2xx, the row survives). The
 * mapper is seeded straight into the table so no scope is set during setup, and each test stays on
 * a single tenant path so the per-request scope is set once (the aspect forbids redefining the
 * scope within one transaction). Ground-truth reads use raw JDBC so the rewriter never touches
 * them. Overlaps {@link ImportMapperHttpIsolationTest} by design; kept until {@code
 * import_mappers}' v1 path is fully retired, then consolidated via the TENANT_ISOLATION.md skill.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("Tenant Isolation (mapper API, adapted to v2)")
class MapperApiTenantIsolationTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/tenants/{tenantId}/mappers/{mapperId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @Test
  @DisplayName("Mapper created in tenant X should NOT be readable from tenant Y")
  void given_mapperInTenantX_should_notBeReadableFromTenantY() throws Exception {
    Tenant tenantX = tenantHelper.createTenantWithCurrentUser("Tenant X");
    Tenant tenantY = tenantHelper.createTenantWithCurrentUser("Tenant Y");
    String mapperId = seedMapper(tenantX.getId(), "Read Isolation Mapper");

    int responseStatus =
        mvc.perform(get(MAPPER_BY_ID, tenantY.getId(), mapperId).accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getStatus();

    assertEquals(HttpStatus.NOT_FOUND.value(), responseStatus);
  }

  @Test
  @DisplayName("Mapper created in tenant X should be readable from tenant X")
  void given_mapperInTenantX_should_beReadableFromTenantX() throws Exception {
    Tenant tenantX = tenantHelper.createTenantWithCurrentUser("Tenant X");
    String mapperId = seedMapper(tenantX.getId(), "Same Tenant Mapper");

    String response =
        mvc.perform(get(MAPPER_BY_ID, tenantX.getId(), mapperId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(mapperId, JsonPath.read(response, "$.import_mapper_id"));
  }

  @Test
  @DisplayName("Mapper created in tenant X should NOT be updatable from tenant Y")
  void given_mapperInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
    Tenant tenantX = tenantHelper.createTenantWithCurrentUser("Tenant X");
    Tenant tenantY = tenantHelper.createTenantWithCurrentUser("Tenant Y");
    String mapperId = seedMapper(tenantX.getId(), "Update Isolation Mapper");

    ImportMapperUpdateInput updateInput = new ImportMapperUpdateInput();
    updateInput.setName("Hijacked Mapper");
    updateInput.setInjectTypeColumn("B");

    int responseStatus =
        mvc.perform(
                put(MAPPER_BY_ID, tenantY.getId(), mapperId)
                    .content(asJsonString(updateInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus();

    assertEquals(HttpStatus.NOT_FOUND.value(), responseStatus);
    assertEquals("Update Isolation Mapper", rawName(mapperId), "X's mapper must be left untouched");
  }

  @Test
  @DisplayName("Mapper created in tenant X should NOT be deletable from tenant Y")
  void given_mapperInTenantX_should_notBeDeletableFromTenantY() throws Exception {
    Tenant tenantX = tenantHelper.createTenantWithCurrentUser("Tenant X");
    Tenant tenantY = tenantHelper.createTenantWithCurrentUser("Tenant Y");
    String mapperId = seedMapper(tenantX.getId(), "Delete Isolation Mapper");

    // Under v2 a cross-tenant delete is a scoped no-op: it returns 2xx but matches no row.
    mvc.perform(delete(MAPPER_BY_ID, tenantY.getId(), mapperId).with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertEquals(1L, rawCount(mapperId), "X's mapper must survive tenant Y's delete attempt");
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
}
