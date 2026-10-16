package io.openaev.rest.custom_dashboard;

import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CustomDashboardFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.WidgetFixture;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The custom dashboard search returns raw {@code CustomDashboard} entities. Their lazy {@code
 * custom_dashboard_widgets}, serialized open-in-view after the scoped transaction has committed,
 * fail closed to an empty array. The test reads a dashboard through the search, sends it back to
 * the update endpoint as it came, and checks the widgets in the database.
 *
 * <p>The tests run with the production list of isolated tables. The class is deliberately NOT
 * {@code @Transactional}: a rolled-back test transaction never commits, so the tenant-local scope
 * would stay alive through serialization and mask the failure. Seeding and cleanup run in committed
 * transactions scoped to the test tenant, and the database checks read the widgets table directly,
 * outside any scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Custom dashboard search keeps the widgets across a read and an unchanged save")
class CustomDashboardWidgetsRoundTripTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private CustomDashboardRepository customDashboardRepository;

  private JdbcTemplate jdbc;
  private String tenant;
  private String otherTenant;

  @BeforeEach
  void setUp() throws Exception {
    customDashboardComposer.reset();
    widgetComposer.reset();
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("custom-dashboard-round-trip").getId();
  }

  @AfterEach
  void cleanup() {
    if (otherTenant != null) {
      // The other tenant's widget may point at this tenant's dashboard: remove it first.
      widgetComposer.generatedItems.forEach(
          widget -> jdbc.update("DELETE FROM widgets WHERE widget_id = ?", widget.getId()));
      tenantTx.execute(
          TxCtx.forTenant(otherTenant),
          () -> {
            customDashboardRepository.deleteAllById(
                customDashboardComposer.generatedItems.stream()
                    .map(CustomDashboard::getId)
                    .toList());
            tenantHelper.deleteCommittedTenants(otherTenant);
          });
      otherTenant = null;
    }
    inTenantScope(
        () -> {
          // Deleting the dashboard removes its widgets through the orphan removal cascade.
          customDashboardRepository.deleteAllById(
              customDashboardComposer.generatedItems.stream().map(CustomDashboard::getId).toList());
          // The helper's deletes on collectors and collector_types (isolated in production) join
          // this transaction, so they run with the tenant in scope.
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Search custom dashboards")
  class SearchCustomDashboards {

    @Test
    @DisplayName(
        "given a dashboard with widgets should return and keep its widgets when the searched"
            + " dashboard is saved")
    void given_dashboardWithWidgets_should_returnAndKeepItsWidgets_when_searchedDashboardIsSaved()
        throws Exception {
      // -- Arrange --
      CustomDashboard dashboard = seedDashboardWithTwoWidgets();
      List<String> seededWidgets = widgetIds();
      SoftAssertions softly = new SoftAssertions();

      // -- Act --
      JsonNode searched =
          findById(
              mapper
                  .readTree(
                      mvc.perform(
                              post(TENANT_CUSTOM_DASHBOARDS_URI + "/search", tenant)
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(
                                      mapper.writeValueAsString(
                                          PaginationFixture.simpleTextSearch(dashboard.getName())))
                                  .with(csrf()))
                          .andExpect(status().isOk())
                          .andReturn()
                          .getResponse()
                          .getContentAsString())
                  .get("content"),
              dashboard.getId());
      softly
          .assertThat(widgetIdsOf(searched))
          .as("widgets in the search response")
          .containsExactlyInAnyOrderElementsOf(seededWidgets);
      mvc.perform(
              put(TENANT_CUSTOM_DASHBOARDS_URI + "/{customDashboardId}", tenant, dashboard.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(searched))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      softly
          .assertThat(widgetsInDatabase(dashboard))
          .as("widgets after saving the searched dashboard")
          .containsExactlyInAnyOrderElementsOf(seededWidgets);
      softly.assertAll();
    }
  }

  @Nested
  @DisplayName("Widgets of another tenant")
  class WidgetsOfAnotherTenant {

    @Test
    @DisplayName(
        "given a widget of another tenant linked to the dashboard should leave it out when a"
            + " scope covering both tenants searches")
    void given_widgetOfAnotherTenantLinkedToDashboard_should_leaveItOut_when_bothTenantsSearch()
        throws Exception {
      // -- Arrange --
      CustomDashboard dashboard = seedDashboardWithTwoWidgets();
      List<String> ownWidgets = widgetIds();
      otherTenant = tenantHelper.createTenantWithCurrentUser("custom-dashboard-other").getId();
      String foreignWidget = seedWidgetOfOtherTenantLinkedTo(dashboard);

      // -- Act --
      // The non-prefixed route with no tenant header scopes the request to every tenant of the
      // caller, so both widgets are readable and only the query's correlation separates them.
      JsonNode searched =
          findById(
              mapper
                  .readTree(
                      mvc.perform(
                              post(CUSTOM_DASHBOARDS_URI + "/search")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(
                                      mapper.writeValueAsString(
                                          PaginationFixture.simpleTextSearch(dashboard.getName())))
                                  .with(csrf()))
                          .andExpect(status().isOk())
                          .andReturn()
                          .getResponse()
                          .getContentAsString())
                  .get("content"),
              dashboard.getId());

      // -- Assert --
      SoftAssertions softly = new SoftAssertions();
      softly
          .assertThat(widgetsInDatabase(dashboard))
          .as("widgets linked to the dashboard in the database")
          .contains(foreignWidget);
      softly
          .assertThat(widgetIdsOf(searched))
          .as("widgets in the search response")
          .containsExactlyInAnyOrderElementsOf(ownWidgets);
      softly.assertAll();
    }

    /**
     * A widget of the other tenant whose dashboard link is then pointed at {@code dashboard}: a
     * cross-tenant link no endpoint creates, written directly so the correlation has something to
     * refuse.
     */
    private String seedWidgetOfOtherTenantLinkedTo(CustomDashboard dashboard) {
      CustomDashboard ownDashboard = CustomDashboardFixture.createDefaultCustomDashboard();
      ownDashboard.setName("other-tenant-dashboard-" + UUID.randomUUID());
      ownDashboard.setTenant(new Tenant(otherTenant));
      Widget widget = WidgetFixture.createDefaultWidget();
      widget.setTenant(new Tenant(otherTenant));
      tenantTx.execute(
          TxCtx.forTenant(otherTenant),
          () ->
              customDashboardComposer
                  .forCustomDashboard(ownDashboard)
                  .withWidget(widgetComposer.forWidget(widget))
                  .persist());
      jdbc.update(
          "UPDATE widgets SET widget_custom_dashboard = ? WHERE widget_id = ?",
          dashboard.getId(),
          widget.getId());
      return widget.getId();
    }
  }

  // -- SEEDING --

  private CustomDashboard seedDashboardWithTwoWidgets() {
    CustomDashboard dashboard = CustomDashboardFixture.createDefaultCustomDashboard();
    dashboard.setName("round-trip-dashboard-" + UUID.randomUUID());
    dashboard.setTenant(new Tenant(tenant));
    return inTenantScope(
        () ->
            customDashboardComposer
                .forCustomDashboard(dashboard)
                .withWidget(newWidget())
                .withWidget(newWidget())
                .persist()
                .get());
  }

  private WidgetComposer.Composer newWidget() {
    Widget widget = WidgetFixture.createDefaultWidget();
    widget.setTenant(new Tenant(tenant));
    return widgetComposer.forWidget(widget);
  }

  private <T> T inTenantScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenant), work);
  }

  private static JsonNode findById(JsonNode dashboards, String dashboardId) {
    return StreamSupport.stream(dashboards.spliterator(), false)
        .filter(node -> dashboardId.equals(node.get("custom_dashboard_id").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("dashboard " + dashboardId + " not returned"));
  }

  // -- CHECKS --

  private List<String> widgetsInDatabase(CustomDashboard dashboard) {
    return jdbc.queryForList(
        "SELECT widget_id FROM widgets WHERE widget_custom_dashboard = ?",
        String.class,
        dashboard.getId());
  }

  private List<String> widgetIds() {
    return widgetComposer.generatedItems.stream().map(Widget::getId).toList();
  }

  private static List<String> widgetIdsOf(JsonNode dashboard) {
    return StreamSupport.stream(dashboard.get("custom_dashboard_widgets").spliterator(), false)
        .map(widget -> widget.get("widget_id").asText())
        .toList();
  }
}
