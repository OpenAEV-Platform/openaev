package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createCustomDashboardWithDefaultParams;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultIncidentResponseScenario;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
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
@DisplayName("scenario-linked dashboards keep the request tenant scope on v2")
class ScenarioDashboardTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private String tenantA;
  private LinkedDashboardSeed scenarioA;
  private LinkedDashboardSeed scenarioB;

  @BeforeEach
  void seedTwoTenantsWithOneScenarioDashboardEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("scenario-dashboard-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("scenario-dashboard-b").getId();
    scenarioA = seedScenarioWithDashboard(tenantA, "scenario-a", "scenario-dashboard-a");
    scenarioB = seedScenarioWithDashboard(tenantB, "scenario-b", "scenario-dashboard-b");
  }

  @Test
  @DisplayName(
      "under tenant A's path: scenario A returns A's dashboard and scenario B stays hidden")
  void given_tenantAPath_should_scope_linkedScenarioDashboard() throws Exception {
    // -- ARRANGE --
    String ownUrl =
        TENANT_SCENARIO_URI.replace("{tenantId}", tenantA)
            + "/"
            + scenarioA.resourceId()
            + "/dashboard";
    String foreignUrl =
        TENANT_SCENARIO_URI.replace("{tenantId}", tenantA)
            + "/"
            + scenarioB.resourceId()
            + "/dashboard";

    // -- ACT --
    String body =
        mvc.perform(get(ownUrl))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(scenarioA.dashboardId(), JsonPath.read(body, "$.custom_dashboard_id"));
    List<String> widgetIds = extractWidgetIds(body);
    assertFalse(widgetIds.contains(scenarioB.widgetId()));
    assertFalse(body.contains(scenarioB.dashboardId()));
    mvc.perform(get(foreignUrl)).andExpect(status().isNotFound());
  }

  private List<String> extractWidgetIds(String body) {
    try {
      return JsonPath.read(body, "$.custom_dashboard_widgets");
    } catch (ClassCastException e) {
      return JsonPath.read(body, "$.custom_dashboard_widgets[*].widget_id");
    } catch (PathNotFoundException e) {
      return List.of();
    }
  }

  private LinkedDashboardSeed seedScenarioWithDashboard(
      String tenantId, String scenarioName, String dashboardName) {
    CustomDashboard dashboard = createCustomDashboardWithDefaultParams();
    dashboard.setName(dashboardName);
    dashboard.setTenant(new Tenant(tenantId));

    Widget widget = createDefaultWidget();
    widget.setTenant(new Tenant(tenantId));

    CustomDashboard persistedDashboard =
        customDashboardComposer
            .forCustomDashboard(dashboard)
            .withWidget(widgetComposer.forWidget(widget))
            .persist()
            .get();

    Scenario scenario = createDefaultIncidentResponseScenario();
    scenario.setName(scenarioName);
    scenario.setTenant(new Tenant(tenantId));
    scenario.setCustomDashboard(persistedDashboard);

    Scenario persistedScenario = scenarioComposer.forScenario(scenario).persist().get();
    return new LinkedDashboardSeed(
        persistedScenario.getId(),
        persistedDashboard.getId(),
        persistedDashboard.getWidgets().getFirst().getId());
  }

  private record LinkedDashboardSeed(String resourceId, String dashboardId, String widgetId) {}
}
