package io.openaev.rest.mapper;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.rest.mapper.form.ImportMapperAddInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
@DisplayName("import_mappers read isolation through the real HTTP endpoint")
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
