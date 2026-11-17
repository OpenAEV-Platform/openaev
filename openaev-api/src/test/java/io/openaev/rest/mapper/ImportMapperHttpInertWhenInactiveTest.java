package io.openaev.rest.mapper;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.rest.mapper.form.ImportMapperUpdateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The companion to {@link ImportMapperHttpIsolationTest}: it proves the same wired endpoints stay
 * inert while {@code import_mappers} is not in the allowlist (the default). The TxCtx is still
 * resolved and the scope still set, but the rewriter leaves the table alone, so a write reaches
 * another tenant's mapper exactly as it did before the wiring. This is what makes the read, update
 * and delete wiring safe to land before the coordinated go-live: it only takes effect once the
 * table is activated. When the table is in the active list, as in the production configuration, the
 * same call is refused and the mapper is untouched.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("import_mappers endpoints are inert until the table is activated")
class ImportMapperHttpInertWhenInactiveTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/tenants/{tenantId}/mappers/{mapperId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @Value("${openaev.tenant.active-tables:}")
  private List<String> activeTables;

  private String tenantA;
  private String mapperB;

  @BeforeEach
  void seedTwoTenantsWithOneMapperUnderB() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("inert-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("inert-b").getId();
    mapperB = seedMapper(tenantB, "mapper-b");
  }

  @Test
  @DisplayName(
      "A's path reaches B's mapper only while the table is inactive, and never once it is active")
  void updateUnderTenantAReachesBMapperOnlyWhenInactive() throws Exception {
    boolean active = activeTables.contains("import_mappers");
    ImportMapperUpdateInput input = new ImportMapperUpdateInput();
    input.setName("changed-by-a");
    input.setInjectTypeColumn("A");
    mvc.perform(
            put(MAPPER_BY_ID, tenantA, mapperB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(active ? status().isNotFound() : status().isOk());
    String name = rawName(mapperB);
    assertEquals(
        active ? "mapper-b" : "changed-by-a",
        name,
        "tenant isolation applies exactly when the table is active");
  }

  // Ground-truth read on the test's own connection: the scope rewriter does not touch a statement
  // it never generated, so the read sees the row whichever tenant owns it.
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
