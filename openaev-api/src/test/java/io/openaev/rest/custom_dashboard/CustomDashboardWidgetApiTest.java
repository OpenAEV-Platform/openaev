package io.openaev.rest.custom_dashboard;

import static io.openaev.engine.api.WidgetType.AVERAGE;
import static io.openaev.engine.api.WidgetType.VERTICAL_BAR_CHART;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.WidgetFixture.NAME;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.database.model.WidgetLayout;
import io.openaev.database.repository.WidgetRepository;
import io.openaev.engine.api.DateHistogramWidget;
import io.openaev.engine.api.HistogramInterval;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.rest.custom_dashboard.form.WidgetInput;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CustomDashboardWidgetApiTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private WidgetRepository repository;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  WidgetComposer.Composer createWidgetComposer() {
    return this.widgetComposer
        .forWidget(createDefaultWidget())
        .withCustomDashboard(
            customDashboardComposer.forCustomDashboard(createDefaultCustomDashboard()))
        .persist();
  }

  private WidgetInput createDefaultWidgetInput(String title) {
    WidgetInput input = new WidgetInput();
    input.setType(VERTICAL_BAR_CHART);
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    widgetConfig.setTitle(title);
    widgetConfig.setDateAttribute("base_updated_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
    widgetConfig.setSeries(new ArrayList<>());
    widgetConfig.setInterval(HistogramInterval.day);
    widgetConfig.setStart("2012-12-21T10:45:23Z");
    widgetConfig.setEnd("2012-12-22T10:45:23Z");
    input.setWidgetConfiguration(widgetConfig);
    input.setWidgetLayout(new WidgetLayout());
    return input;
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_valid_widget_input_when_creating_widget_should_return_created_widget()
      throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    WidgetInput input = new WidgetInput();
    input.setType(VERTICAL_BAR_CHART);
    String name = "My new widget";
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    widgetConfig.setTitle(name);
    widgetConfig.setDateAttribute("base_updated_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
    widgetConfig.setSeries(new ArrayList<>());
    widgetConfig.setInterval(HistogramInterval.day);
    widgetConfig.setStart("2012-12-21T10:45:23Z");
    widgetConfig.setEnd("2012-12-22T10:45:23Z");
    input.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    input.setWidgetLayout(widgetLayout);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            post(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId() + "/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(name));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void
      given_valid_average_widget_input_when_creating_widget_should_return_created_widget_and_can_be_deleted()
          throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    WidgetInput input = new WidgetInput();
    input.setType(AVERAGE);
    String name = "My new average widget";
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    widgetConfig.setTitle(name);
    widgetConfig.setDateAttribute("base_updated_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
    widgetConfig.setSeries(new ArrayList<>());
    widgetConfig.setInterval(HistogramInterval.day);
    widgetConfig.setStart("2012-12-21T10:45:23Z");
    widgetConfig.setEnd("2012-12-22T10:45:23Z");
    input.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    input.setWidgetLayout(widgetLayout);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            post(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId() + "/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(name));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widgets_should_return_all_widgets() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            get(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId() + "/widgets").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].widget_config.title").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widget_id_when_fetching_widget_should_return_widget() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            get(CUSTOM_DASHBOARDS_URI
                    + "/"
                    + customDashboard.getId()
                    + "/widgets/"
                    + composer.get().getId())
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_updated_widget_input_when_updating_widget_should_return_updated_widget()
      throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    Widget widget = composer.get();
    WidgetLayout widgetLayout = new WidgetLayout();
    widgetLayout.setX(10);
    widgetLayout.setY(10);
    widget.setLayout(widgetLayout);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(CUSTOM_DASHBOARDS_URI
                    + "/"
                    + customDashboard.getId()
                    + "/widgets/"
                    + widget.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(widget))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(NAME))
        .andExpect(jsonPath("$.widget_layout.widget_layout_x").value(10));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widget_id_when_deleting_widget_should_return_no_content() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    Widget widget = composer.get();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            delete(
                    CUSTOM_DASHBOARDS_URI
                        + "/"
                        + customDashboard.getId()
                        + "/widgets/"
                        + widget.getId())
                .with(csrf()))
        .andExpect(status().isNoContent());

    assertThat(repository.existsById(widget.getId())).isFalse();
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Widget created in tenant X should NOT be readable from tenant Y")
    void given_widgetInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      // Seeded via composer under tenant X's context, not through the create endpoints: creating
      // via the API would set the tenant scope (TxCtx) to X on this test's wrapping transaction,
      // and the read call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Composer persistence bypasses that
      // entirely (no controller/TxCtx involved).
      Widget widget = seedWidgetInTenant(tenantX);
      String dashboardId = widget.getCustomDashboard().getId();
      String widgetId = widget.getId();

      // Act
      int response =
          mockMvc
              .perform(
                  get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantY.getId())
                          + "/"
                          + dashboardId
                          + "/widgets/"
                          + widgetId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isEqualTo(0);
    }

    @Test
    @DisplayName("Widget created in tenant X should be readable from tenant X")
    void given_widgetInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput dashboardInput = new CustomDashboardInput();
      dashboardInput.setName("Tenant X Dashboard Same Tenant Read");
      String dashboardResponse =
          mockMvc
              .perform(
                  post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantX.getId()))
                      .content(asJsonString(dashboardInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String dashboardId = JsonPath.read(dashboardResponse, "$.custom_dashboard_id");

      WidgetInput widgetInput = createDefaultWidgetInput("Widget same tenant");
      String widgetResponse =
          mockMvc
              .perform(
                  post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantX.getId())
                          + "/"
                          + dashboardId
                          + "/widgets")
                      .content(asJsonString(widgetInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String widgetId = JsonPath.read(widgetResponse, "$.widget_id");

      // Act + Assert
      mockMvc
          .perform(
              get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantX.getId())
                      + "/"
                      + dashboardId
                      + "/widgets/"
                      + widgetId)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Widget list in tenant Y should NOT contain widgets from tenant X")
    void given_widgetInTenantX_should_notAppearInTenantYWidgetList() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      // Seeded via composer under tenant X's context, not through the create endpoints: creating
      // via the API would set the tenant scope (TxCtx) to X on this test's wrapping transaction,
      // and the list call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Composer persistence bypasses that
      // entirely (no controller/TxCtx involved).
      Widget widget = seedWidgetInTenant(tenantX);
      String dashboardId = widget.getCustomDashboard().getId();

      // Act + Assert
      mockMvc
          .perform(
              get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantY.getId())
                      + "/"
                      + dashboardId
                      + "/widgets")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Widget created in tenant X should NOT be updatable from tenant Y")
    void given_widgetInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      // Seeded via composer under tenant X's context, not through the create endpoints: creating
      // via the API would set the tenant scope (TxCtx) to X on this test's wrapping transaction,
      // and the update call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Composer persistence bypasses that
      // entirely (no controller/TxCtx involved).
      Widget widget = seedWidgetInTenant(tenantX);
      String dashboardId = widget.getCustomDashboard().getId();
      String widgetId = widget.getId();

      WidgetInput updateWidgetInput = createDefaultWidgetInput("Hijacked widget title");

      // Act
      int response =
          mockMvc
              .perform(
                  put(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantY.getId())
                          + "/"
                          + dashboardId
                          + "/widgets/"
                          + widgetId)
                      .content(asJsonString(updateWidgetInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isEqualTo(0);
    }

    @Test
    @DisplayName("Widget created in tenant X should NOT be deletable from tenant Y")
    void given_widgetInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      // Seeded via composer under tenant X's context, not through the create endpoints: creating
      // via the API would set the tenant scope (TxCtx) to X on this test's wrapping transaction,
      // and the delete call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Composer persistence bypasses that
      // entirely (no controller/TxCtx involved).
      Widget widget = seedWidgetInTenant(tenantX);
      String dashboardId = widget.getCustomDashboard().getId();
      String widgetId = widget.getId();

      // Act
      int response =
          mockMvc
              .perform(
                  delete(
                          TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantY.getId())
                              + "/"
                              + dashboardId
                              + "/widgets/"
                              + widgetId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isEqualTo(0);
    }

    /**
     * Seeds a widget (with its own custom dashboard) via composers under the given tenant's context
     * instead of through the create endpoints: creating through the API sets the tenant scope
     * (TxCtx) on this test's wrapping transaction, which conflicts with a subsequent call scoped to
     * a different tenant within the same test (see TenantScopeTransactionAspect). Composer
     * persistence goes straight to the repository, bypassing any controller/TxCtx.
     */
    private Widget seedWidgetInTenant(Tenant tenant) {
      tenantIsolationHelper.switchToTenant(tenant.getId(), entityManager);
      Widget widget =
          widgetComposer
              .forWidget(createDefaultWidget())
              .withCustomDashboard(
                  customDashboardComposer.forCustomDashboard(createDefaultCustomDashboard()))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();
      return widget;
    }
  }
}
