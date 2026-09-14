package io.openaev.rest.settings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createCustomDashboardWithDefaultParams;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.rest.settings.form.TenantSettingsUpdateInput;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=custom_dashboards,widgets")
@WithMockUser(isAdmin = true)
@DisplayName("tenant home dashboard keeps v2 tenant scope")
class TenantSettingsHomeDashboardTenantScopeTest extends IntegrationTest {

  private static final String HOME_DASHBOARD_URI =
      TENANT_PREFIX + "/tenant-settings/home-dashboard";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private TenantSettingsService tenantSettingsService;

  private String tenantA;
  private DashboardSeed dashboardA;
  private DashboardSeed dashboardB;

  @BeforeEach
  void seedTwoTenantsWithOneHomeDashboardEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("home-dashboard-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("home-dashboard-b").getId();
    dashboardA = seedDashboardWithWidget(tenantA, "home-dashboard-a");
    dashboardB = seedDashboardWithWidget(tenantB, "home-dashboard-b");
    assignHomeDashboard(tenantA, dashboardA.dashboardId());
    assignHomeDashboard(tenantB, dashboardB.dashboardId());
  }

  @Test
  @DisplayName("under tenant A's path: the home dashboard and widget list come from tenant A only")
  void given_tenantAPath_should_return_only_tenantA_homeDashboard() throws Exception {
    // -- ARRANGE --
    String url = HOME_DASHBOARD_URI.replace("{tenantId}", tenantA);

    // -- ACT --
    String body =
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(dashboardA.dashboardId(), JsonPath.read(body, "$.custom_dashboard_id"));
    List<String> widgetIds = JsonPath.read(body, "$.custom_dashboard_widgets");
    assertFalse(widgetIds.contains(dashboardB.widgetId()));
    assertFalse(body.contains(dashboardB.dashboardId()));
  }

  private void assignHomeDashboard(String tenantId, String dashboardId) {
    tenantSettingsService.updateSettings(
        tenantId, new TenantSettingsUpdateInput("OpenAEV", "dark", "en", dashboardId, "", ""));
  }

  private DashboardSeed seedDashboardWithWidget(String tenantId, String name) {
    CustomDashboard dashboard = createCustomDashboardWithDefaultParams();
    dashboard.setName(name);
    dashboard.setTenant(new Tenant(tenantId));

    Widget widget = createDefaultWidget();
    widget.setTenant(new Tenant(tenantId));

    CustomDashboard persistedDashboard =
        customDashboardComposer
            .forCustomDashboard(dashboard)
            .withWidget(widgetComposer.forWidget(widget))
            .persist()
            .get();
    return new DashboardSeed(
        persistedDashboard.getId(), persistedDashboard.getWidgets().getFirst().getId());
  }

  private record DashboardSeed(String dashboardId, String widgetId) {}
}
