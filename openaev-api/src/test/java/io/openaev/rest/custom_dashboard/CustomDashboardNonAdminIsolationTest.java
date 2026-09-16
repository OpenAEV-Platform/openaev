package io.openaev.rest.custom_dashboard;

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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=custom_dashboards,widgets")
@WithMockUser(isAdmin = false)
@DisplayName("custom dashboards stay isolated for non-admin callers too")
class CustomDashboardNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_DASHBOARDS = Set.of(Capability.ACCESS_DASHBOARDS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String dashboardA;
  private String dashboardB;

  @BeforeEach
  void seedTwoTenantsTheCallerBelongsToWithOneDashboardEach() throws Exception {
    tenantA =
        tenantHelper.createTenantWithCapabilities("dashboard-nonadmin-a", READ_DASHBOARDS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("dashboard-nonadmin-b", READ_DASHBOARDS).getId();
    dashboardA = seedDashboard(tenantA, "nonadmin-a");
    dashboardB = seedDashboard(tenantB, "nonadmin-b");
  }

  @Test
  @DisplayName("a non-admin searching under tenant A's path sees only A's dashboard")
  void searchUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/custom-dashboards/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(PaginationFixture.getDefault().textSearch("nonadmin").build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(
        body.contains(dashboardA), "A's dashboard must appear for the non-admin member of A");
    assertFalse(body.contains(dashboardB), "B's dashboard must not leak to A's scope");
  }

  private String seedDashboard(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO custom_dashboards (custom_dashboard_id, custom_dashboard_name, tenant_id)"
                + " VALUES (CAST(:id AS uuid), :name, CAST(:tenant AS uuid))")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
