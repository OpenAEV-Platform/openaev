package io.openaev.rest.dashboard;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createCustomDashboardWithDefaultParams;
import static io.openaev.utils.fixtures.WidgetFixture.createNumberWidgetWithEntity;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
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
@WithMockUser(isAdmin = true)
@DisplayName("dashboard widget queries honor the request tenant scope once widgets are v2-active")
class DashboardApiTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;

  private String tenantA;
  private String widgetA;
  private String widgetB;

  @BeforeEach
  void seedTwoTenantsWithOneWidgetEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("dashboard-api-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("dashboard-api-b").getId();
    widgetA = seedWidget(tenantA, "dashboard-a");
    widgetB = seedWidget(tenantB, "dashboard-b");
  }

  @Test
  @DisplayName("via X-Tenant-Ids: tenant A can query its widget and not tenant B's")
  void countViaHeaderIsScoped() throws Exception {
    String ownBody =
        mvc.perform(
                post("/api/dashboards/count/" + widgetA)
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(new HashMap<>()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(ownBody.isBlank(), "tenant A's own widget must return a non-empty count payload");

    mvc.perform(
            post("/api/dashboards/count/" + widgetB)
                .header("X-Tenant-Ids", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(new HashMap<>()))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  private String seedWidget(String tenantId, String dashboardName) {
    CustomDashboard dashboard = createCustomDashboardWithDefaultParams();
    dashboard.setName(dashboardName);
    dashboard.setTenant(new Tenant(tenantId));

    Widget widget = createNumberWidgetWithEntity("asset");
    widget.setTenant(new Tenant(tenantId));

    CustomDashboard persisted =
        customDashboardComposer
            .forCustomDashboard(dashboard)
            .withWidget(widgetComposer.forWidget(widget))
            .persist()
            .get();
    String widgetId = persisted.getWidgets().getFirst().getId();
    // See CustomDashboardHttpIsolationTest: a first-level cache hit emits no SQL, so the inspector
    // never sees the read. Detaching the seeded aggregate forces the endpoint to query it.
    entityManager.flush();
    entityManager.detach(persisted);
    return widgetId;
  }
}
