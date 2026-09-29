package io.openaev.rest.custom_dashboard;

import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.rest.custom_dashboard.form.WidgetInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
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
@DisplayName(
    "widgets stay isolated through the real custom-dashboard widget endpoints once v2 is active")
class CustomDashboardWidgetHttpIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;

  private String tenantA;
  private String tenantB;
  private String dashboardA;
  private String widgetA;
  private String widgetB;

  @BeforeEach
  void seedTwoTenantsWithOneWidgetEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("widget-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("widget-http-iso-b").getId();
    Widget ownWidget = seedWidget(tenantA, "widget-a");
    dashboardA = ownWidget.getCustomDashboard().getId();
    widgetA = ownWidget.getId();
    widgetB = seedWidget(tenantB, "widget-b").getId();
  }

  @Test
  @DisplayName("under tenant A's path: A's widget is readable and B's is not")
  void widgetReadsStayScoped() throws Exception {
    mvc.perform(
            get(
                TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA)
                    + "/"
                    + dashboardA
                    + "/widgets/"
                    + widgetA))
        .andExpect(status().isOk());
    mvc.perform(
            get(
                TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA)
                    + "/"
                    + dashboardA
                    + "/widgets/"
                    + widgetB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("a widget create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    WidgetInput input = new WidgetInput();
    input.setType(createDefaultWidget().getType());
    input.setWidgetConfiguration(createDefaultWidget().getWidgetConfiguration());
    input.setWidgetLayout(createDefaultWidget().getLayout());

    String body =
        mvc.perform(
                post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA)
                        + "/"
                        + dashboardA
                        + "/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = JsonPath.read(body, "$.widget_id");
    entityManager.flush();
    assertEquals(tenantA, rawSingle("SELECT tenant_id FROM widgets WHERE widget_id = ?", id));
  }

  @Test
  @DisplayName("a widget create with no tenant selector is refused under a multi-tenant scope")
  void createWithoutSelectorIsRejected() throws Exception {
    Widget widget = createDefaultWidget();
    WidgetInput input = new WidgetInput();
    input.setType(widget.getType());
    input.setWidgetConfiguration(widget.getWidgetConfiguration());
    input.setWidgetLayout(widget.getLayout());

    mvc.perform(
            post(CUSTOM_DASHBOARDS_URI + "/" + dashboardA + "/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  private Widget seedWidget(String tenantId, String dashboardName) {
    CustomDashboard dashboard = createDefaultCustomDashboard();
    dashboard.setName(dashboardName);
    dashboard.setTenant(new Tenant(tenantId));
    Widget widget = createDefaultWidget();
    widget.setTenant(new Tenant(tenantId));
    return customDashboardComposer
        .forCustomDashboard(dashboard)
        .withWidget(widgetComposer.forWidget(widget))
        .persist()
        .get()
        .getWidgets()
        .getFirst();
  }

  private String rawSingle(String sql, String id) {
    entityManager.flush();
    return (String)
        entityManager
            .unwrap(org.hibernate.Session.class)
            .doReturningWork(
                connection -> {
                  try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                      rs.next();
                      return rs.getString(1);
                    }
                  }
                });
  }
}
