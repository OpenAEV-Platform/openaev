package io.openaev.rest.mitigation;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
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
 * tests run as admin (RBAC bypassed); this one runs as a non-admin that is a member of two tenants.
 * The mitigation API uses {@code skipRBAC = true}, so isolation is the only gate that matters, and
 * this test proves the rewriter still returns one tenant's mitigations under its path for a
 * non-admin.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=mitigations")
@WithMockUser(isAdmin = false)
@DisplayName("mitigations isolation holds for a non-admin spanning two tenants")
class MitigationNonAdminIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String mitigationA;
  private String mitigationB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneMitigationEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("nonadmin-miti-a", Set.of()).getId();
    String tenantB = tenantHelper.createTenantWithCapabilities("nonadmin-miti-b", Set.of()).getId();
    mitigationA = seedMitigation(tenantA, "nonadmin-miti-name-a", "M9801");
    mitigationB = seedMitigation(tenantB, "nonadmin-miti-name-b", "M9802");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's mitigation")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/mitigations/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(mitigationA), "A's mitigation must appear for the non-admin member of A");
    assertFalse(response.contains(mitigationB), "B's mitigation must not leak to A's scope");
  }

  private String seedMitigation(String tenantId, String name, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO mitigations"
                + " (mitigation_id, mitigation_name, mitigation_external_id,"
                + "  mitigation_stix_id, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, externalId)
        .setParameter(4, "course-of-action--" + UUID.randomUUID())
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }
}
