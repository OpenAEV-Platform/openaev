package io.openaev.rest.custom_dashboard;

import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read isolation for {@code widgets}, which has none anywhere else in the test tree: the widget
 * endpoints all address a widget through its parent dashboard, so {@code
 * CustomDashboardWidgetHttpIsolationTest} gets its 404 from the dashboard id not matching, and the
 * four cross-tenant assertions of {@code CustomDashboardHttpIsolationTest} come from {@code
 * custom_dashboards} alone (measured: the class is fully green with {@code custom_dashboards} armed
 * by itself).
 *
 * <p>The read under test is {@link WidgetService#widget(String)}, which resolves a widget by its
 * own id with no dashboard and no tenant in the query. Its production caller is {@code
 * DashboardService#getWidgetContext}, behind {@code POST /api/dashboards/count|average|series/
 * {widgetId}}: an authenticated route addressed by widget id alone, where the tenant scope is the
 * only thing standing between a caller and another tenant's widget.
 *
 * <p>{@code widgets} is armed alone on purpose. Arming {@code custom_dashboards} with it would make
 * the parent dashboard load fail closed first, so the assertion would no longer be attributable to
 * this table.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=widgets")
@WithMockUser(isAdmin = true)
@DisplayName("widgets read isolation under a tenant scope")
class WidgetTenantScopeTest extends IntegrationTest {

  @Autowired private WidgetService widgetService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;

  private String tenantA;
  private String tenantB;
  private String widgetIdA;
  private String widgetIdB;
  private String dashboardIdA;
  private String dashboardIdB;

  @BeforeEach
  void seedTwoTenantsWithOneWidgetEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("widget-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("widget-scope-b").getId();
    Widget widgetA = seedWidget(tenantA, "widget-scope-dashboard-a");
    Widget widgetB = seedWidget(tenantB, "widget-scope-dashboard-b");
    widgetIdA = widgetA.getId();
    widgetIdB = widgetB.getId();
    dashboardIdA = widgetA.getCustomDashboard().getId();
    dashboardIdB = widgetB.getCustomDashboard().getId();
    TenantContext.clearCurrentTenant();
    // The seeded rows must leave the persistence context, or findById answers from the first-level
    // cache and never issues the SELECT the scope is supposed to rewrite.
    entityManager.flush();
    entityManager.clear();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Lookup by widget id, the dashboard count route's read")
  class ByWidgetId {

    @Test
    @DisplayName("under tenant A's scope: A's widget resolves and B's does not")
    void given_tenantAScope_should_notResolveTenantBWidgetById() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act & Assert - the positive case first, so an empty table cannot pass for a filtered one.
      assertEquals(
          widgetIdA,
          widgetService.widget(widgetIdA).getId(),
          "A's own widget must resolve by id under A's scope");
      entityManager.clear();
      assertThrows(
          EntityNotFoundException.class,
          () -> widgetService.widget(widgetIdB),
          "tenant B's widget must not resolve by id under tenant A's scope: the lookup carries no"
              + " dashboard and no tenant, so the scope is the only thing hiding it");
    }

    @Test
    @DisplayName("with no scope at all: even the caller's own widget fails closed")
    void given_noScopeSet_should_failClosedEvenForItsOwnWidget() {
      // Act & Assert - the control for the test above, on a different line than the active-tables
      // property: the lookup succeeds BECAUSE a scope is set.
      assertThrows(
          EntityNotFoundException.class,
          () -> widgetService.widget(widgetIdA),
          "an active-table lookup by id with no tenant scope must fail closed");
    }
  }

  @Nested
  @DisplayName("Listing a dashboard's widgets")
  class ByDashboardId {

    @Test
    @DisplayName("under tenant A's scope: tenant B's dashboard lists no widget")
    void given_tenantAScope_should_notListTenantBDashboardWidgets() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act & Assert
      assertEquals(
          1,
          widgetService.widgets(dashboardIdA).size(),
          "A's own dashboard must list its widget under A's scope");
      entityManager.clear();
      assertTrue(
          widgetService.widgets(dashboardIdB).isEmpty(),
          "tenant B's dashboard must list no widget under tenant A's scope");
    }
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
}
