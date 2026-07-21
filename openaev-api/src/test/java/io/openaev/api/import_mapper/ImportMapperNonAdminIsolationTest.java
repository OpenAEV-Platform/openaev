package io.openaev.api.import_mapper;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
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
 * The data isolation must not depend on the caller being an administrator. The other isolation
 * tests run as admin (RBAC bypassed); this one runs as a non-admin that is a member of two tenants
 * and holds only the capability needed to read mappers, and shows the rewriter still returns one
 * tenant's mappers under its path. It closes the gap left by the admin-only tests: that the scope,
 * set from the request and never from the isAdmin flag, is what isolates a non-admin too.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = false)
@DisplayName("import_mappers isolation holds for a non-admin spanning two tenants")
class ImportMapperNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_MAPPERS = Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String mapperA;
  private String mapperB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneMapperEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-iso-a", READ_MAPPERS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("nonadmin-iso-b", READ_MAPPERS).getId();
    mapperA = seedMapper(tenantA, "nonadmin-mapper-a");
    mapperB = seedMapper(tenantB, "nonadmin-mapper-b");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's mapper")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
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
    assertTrue(response.contains(mapperA), "A's mapper must appear for the non-admin member of A");
    assertFalse(response.contains(mapperB), "B's mapper must not leak to A's scope");
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
