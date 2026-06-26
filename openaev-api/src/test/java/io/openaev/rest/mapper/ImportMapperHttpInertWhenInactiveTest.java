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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The companion to {@link ImportMapperHttpIsolationTest}: it proves the same wired endpoints stay
 * inert while {@code import_mappers} is not in the allowlist (the default). The TxCtx is still
 * resolved and the scope still set, but the rewriter leaves the table alone, so a write reaches
 * another tenant's mapper exactly as it did before the wiring. This is what makes the read, update
 * and delete wiring safe to land before the coordinated go-live: it only takes effect once the
 * table is activated.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("import_mappers endpoints are inert until the table is activated")
class ImportMapperHttpInertWhenInactiveTest extends IntegrationTest {

  private static final String MAPPER_BY_ID = "/api/tenants/{tenantId}/mappers/{mapperId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

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
      "with the table inactive, A's path reaches B's mapper (no isolation, behaviour unchanged)")
  void updateUnderTenantAReachesBMapperWhenInactive() throws Exception {
    ImportMapperUpdateInput input = new ImportMapperUpdateInput();
    input.setName("changed-by-a");
    input.setInjectTypeColumn("A");
    mvc.perform(
            put(MAPPER_BY_ID, tenantA, mapperB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());
    String name =
        (String)
            entityManager
                .createNativeQuery(
                    "SELECT mapper_name FROM import_mappers WHERE mapper_id = CAST(:id AS uuid)")
                .setParameter("id", mapperB)
                .getSingleResult();
    assertEquals("changed-by-a", name, "with the table inactive there is no tenant isolation");
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
