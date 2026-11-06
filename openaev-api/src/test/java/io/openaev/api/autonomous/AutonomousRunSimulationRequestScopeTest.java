package io.openaev.api.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.dto.ConvertToManualMode;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The operator actions of an autonomous run that touch the run's simulation, reached on the
 * non-prefixed route with {@code X-Tenant-Ids} naming a member tenant. The simulation is loaded by
 * its id, and the overloads that serve them read it under the ambient tenant, which this route
 * leaves on the default one while the run, its scenario and its simulation live in the member
 * tenant: the action is refused, or, on the best-effort paths, silently skipped.
 */
@Transactional
@WithMockUser(isAdmin = true)
@WithoutTenantScope
@DisplayName("autonomous run actions load the run's simulation in the request scope")
class AutonomousRunSimulationRequestScopeTest extends IntegrationTest {

  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI;

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables",
        () -> String.join(",", new TreeSet<>(ProductionActiveTables.names())));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioService scenarioService;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String memberTenant;

  @BeforeEach
  void seedMemberTenant() throws Exception {
    memberTenant = tenantHelper.createTenantWithCurrentUser("auto-sim-member").getId();
  }

  // Created through the endpoint so the run, its scenario and its simulation are all stamped with
  // the selected tenant, like an operator's own create.
  private MockHttpServletResponse createPlanRunInMemberTenant() throws Exception {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(memberTenant));
    String scenarioId = scenarioService.createScenarioChaining(scenario).getId();
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(
            post(PLAIN)
                .header("X-Tenant-Ids", memberTenant)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"objective\": \"Own the domain\", \"plan_mode\": true, \"scenario_id\": \""
                        + scenarioId
                        + "\"}"))
        .andReturn()
        .getResponse();
  }

  private int act(String runId, String action) throws Exception {
    return mvc.perform(
            post(PLAIN + "/" + runId + "/" + action)
                .header("X-Tenant-Ids", memberTenant)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"" + ConvertToManualMode.DUPLICATE + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private String raw(String table, String column, String idColumn, String id) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = :id")
            .setParameter("id", id)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
  }

  private long rawCount(String table, String idColumn, String id) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = :id")
                .setParameter("id", id)
                .getSingleResult())
        .longValue();
  }

  @Nested
  @DisplayName("Non-prefixed route, X-Tenant-Ids naming the member tenant")
  class NonPrefixedRoute {

    @Test
    @DisplayName("given_runWithSimulationOfMemberTenant_should_cancelItsSimulation")
    void given_runWithSimulationOfMemberTenant_should_cancelItsSimulation() throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      TenantContext.clearCurrentTenant();

      // Act
      int status = act(runId, "cancel");

      // Assert
      assertThat(status).isEqualTo(200);
      assertThat(raw("exercises", "exercise_status", "exercise_id", simulationId))
          .isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("given_runWithSimulationOfMemberTenant_should_pauseThenResumeItsSimulation")
    void given_runWithSimulationOfMemberTenant_should_pauseThenResumeItsSimulation()
        throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      TenantContext.clearCurrentTenant();

      // Act
      int pauseStatus = act(runId, "pause");
      String pausedSimulation = raw("exercises", "exercise_status", "exercise_id", simulationId);
      int resumeStatus = act(runId, "resume");

      // Assert
      assertThat(pauseStatus).isEqualTo(200);
      assertThat(pausedSimulation).isEqualTo("PAUSED");
      assertThat(resumeStatus).isEqualTo(200);
      assertThat(raw("exercises", "exercise_status", "exercise_id", simulationId))
          .isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("given_settledPlanRunOfMemberTenant_should_deleteItsScenarioAndSimulation")
    void given_settledPlanRunOfMemberTenant_should_deleteItsScenarioAndSimulation()
        throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      String scenarioId = JsonPath.read(response, "$.autonomous_run_scenario_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      entityManager
          .createNativeQuery(
              "UPDATE autonomous_runs SET autonomous_run_status = 'COMPLETED'"
                  + " WHERE autonomous_run_id = :id")
          .setParameter("id", runId)
          .executeUpdate();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // Act
      int status =
          mvc.perform(
                  delete("/api/scenarios/" + scenarioId)
                      .header("X-Tenant-Ids", memberTenant)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(status).isEqualTo(200);
      assertThat(rawCount("scenarios", "scenario_id", scenarioId)).isZero();
      assertThat(rawCount("exercises", "exercise_id", simulationId)).isZero();
    }

    @Test
    @DisplayName("given_runWithSimulationOfMemberTenant_should_restartAndDeleteTheOldSimulation")
    void given_runWithSimulationOfMemberTenant_should_restartAndDeleteTheOldSimulation()
        throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      entityManager
          .createNativeQuery(
              "UPDATE autonomous_runs SET autonomous_run_status = 'COMPLETED'"
                  + " WHERE autonomous_run_id = :id")
          .setParameter("id", runId)
          .executeUpdate();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // Act
      int status = act(runId, "restart");

      // Assert
      assertThat(status).isEqualTo(200);
      assertThat(rawCount("exercises", "exercise_id", simulationId)).isZero();
    }

    @Test
    @DisplayName("given_plannedRunWithSimulationOfMemberTenant_should_promoteItToARealRun")
    void given_plannedRunWithSimulationOfMemberTenant_should_promoteItToARealRun()
        throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      String simulationId = JsonPath.read(response, "$.autonomous_run_simulation_id");
      entityManager
          .createNativeQuery(
              "UPDATE autonomous_runs SET autonomous_run_status = 'PLANNED'"
                  + " WHERE autonomous_run_id = :id")
          .setParameter("id", runId)
          .executeUpdate();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // Act
      int status = act(runId, "promote");

      // Assert
      assertThat(status).isEqualTo(200);
      assertThat(rawCount("exercises", "exercise_id", simulationId)).isZero();
    }

    @Test
    @DisplayName("given_settledRunOfMemberTenant_should_convertItToManualAsADuplicate")
    void given_settledRunOfMemberTenant_should_convertItToManualAsADuplicate() throws Exception {
      // Arrange
      String response = createPlanRunInMemberTenant().getContentAsString();
      String runId = JsonPath.read(response, "$.autonomous_run_id");
      entityManager
          .createNativeQuery(
              "UPDATE autonomous_runs SET autonomous_run_status = 'COMPLETED',"
                  + " autonomous_run_plan_mode = false WHERE autonomous_run_id = :id")
          .setParameter("id", runId)
          .executeUpdate();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // Act
      int status = act(runId, "convert-to-manual");

      // Assert
      assertThat(status).isEqualTo(200);
    }
  }
}
