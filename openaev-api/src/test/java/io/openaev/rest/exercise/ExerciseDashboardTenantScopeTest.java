package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createCustomDashboardWithDefaultParams;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
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
@DisplayName("exercise-linked dashboards keep the request tenant scope on v2")
class ExerciseDashboardTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  private String tenantA;
  private LinkedDashboardSeed exerciseA;
  private LinkedDashboardSeed exerciseB;

  @BeforeEach
  void seedTwoTenantsWithOneExerciseDashboardEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("exercise-dashboard-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("exercise-dashboard-b").getId();
    exerciseA = seedExerciseWithDashboard(tenantA, "exercise-a", "exercise-dashboard-a");
    exerciseB = seedExerciseWithDashboard(tenantB, "exercise-b", "exercise-dashboard-b");
  }

  @Test
  @DisplayName(
      "under tenant A's path: exercise A returns A's dashboard and exercise B stays hidden")
  void given_tenantAPath_should_scope_linkedExerciseDashboard() throws Exception {
    // -- ARRANGE --
    String ownUrl =
        TENANT_EXERCISE_URI.replace("{tenantId}", tenantA)
            + "/"
            + exerciseA.resourceId()
            + "/dashboard";
    String foreignUrl =
        TENANT_EXERCISE_URI.replace("{tenantId}", tenantA)
            + "/"
            + exerciseB.resourceId()
            + "/dashboard";

    // -- ACT --
    String body =
        mvc.perform(get(ownUrl))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertEquals(exerciseA.dashboardId(), JsonPath.read(body, "$.custom_dashboard_id"));
    List<String> widgetIds = JsonPath.read(body, "$.custom_dashboard_widgets");
    assertFalse(widgetIds.contains(exerciseB.widgetId()));
    assertFalse(body.contains(exerciseB.dashboardId()));
    mvc.perform(get(foreignUrl)).andExpect(status().isNotFound());
  }

  private LinkedDashboardSeed seedExerciseWithDashboard(
      String tenantId, String exerciseName, String dashboardName) {
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

    Exercise exercise = createDefaultExercise();
    exercise.setName(exerciseName);
    exercise.setTenant(new Tenant(tenantId));
    exercise.setCustomDashboard(persistedDashboard);

    Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();
    return new LinkedDashboardSeed(
        persistedExercise.getId(),
        persistedDashboard.getId(),
        persistedDashboard.getWidgets().getFirst().getId());
  }

  private record LinkedDashboardSeed(String resourceId, String dashboardId, String widgetId) {}
}
