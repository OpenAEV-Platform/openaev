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
import io.openaev.database.model.Capability;
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
import java.util.Set;
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
@WithMockUser
@DisplayName("tenant home dashboard v2 isolation does not depend on admin bypass")
class TenantSettingsHomeDashboardNonAdminIsolationTest extends IntegrationTest {

  private static final String HOME_DASHBOARD_URI =
      TENANT_PREFIX + "/tenant-settings/home-dashboard";
  private static final Set<Capability> READ_TENANT_SETTINGS =
      Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private TenantSettingsService tenantSettingsService;

  private String tenantA;
  private DashboardSeed dashboardA;
  private DashboardSeed dashboardB;

  @BeforeEach
  void seedTwoTenantsWithReadCapabilityAndOneHomeDashboardEach() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("home-dashboard-nonadmin-a", READ_TENANT_SETTINGS)
            .getId();
    String tenantB =
        tenantHelper
            .createTenantWithCapabilities("home-dashboard-nonadmin-b", READ_TENANT_SETTINGS)
            .getId();
    dashboardA = seedDashboardWithWidget(tenantA, "home-dashboard-nonadmin-a");
    dashboardB = seedDashboardWithWidget(tenantB, "home-dashboard-nonadmin-b");
    assignHomeDashboard(tenantA, dashboardA.dashboardId());
    assignHomeDashboard(tenantB, dashboardB.dashboardId());
  }

  @Test
  @DisplayName("under tenant A's path: a non-admin sees only tenant A's configured home dashboard")
  void given_nonAdminTenantAPath_should_return_only_tenantA_homeDashboard() throws Exception {
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
