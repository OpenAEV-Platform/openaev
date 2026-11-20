package io.openaev.api.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.dto.ConvertToManualMode;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AutonomousRunService#restart}, {@code #promoteToRealRun} and {@code #convertToManual} read
 * the run's scenario with {@code scenarioService.scenario(scenarioId)} - the ambient-tenant
 * overload - although each is reached from an {@code AutonomousRunApi} endpoint that already takes
 * a {@code TxCtx}. The run itself resolves fine ({@code autonomous_runs} is tenant-active, so the
 * v2 scope finds it), but the scenario lookup falls back to {@link TenantContext#getCurrentTenant}
 * only, which the non-prefixed route leaves on the default tenant. A caller naming a member tenant
 * through {@code X-Tenant-Ids} - the orchestrator's own route - therefore gets a 404 on a run whose
 * scenario lives in that member tenant, even though the run itself was just resolved in that exact
 * scope one line above.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("autonomous run operator actions read their scenario in the request scope")
class AutonomousRunScenarioRequestScopeTest extends IntegrationTest {

  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioService scenarioService;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String memberTenant;

  @BeforeEach
  void seedMemberTenant() throws Exception {
    memberTenant = tenantHelper.createTenantWithCurrentUser("auto-scope-member").getId();
  }

  private String seedChainingScenario() throws Exception {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(memberTenant));
    return scenarioService.createScenarioChaining(scenario).getId();
  }

  // Native seed: the run's scenario is the only thing under test here, so the row is written
  // directly rather than through the create endpoint.
  private String seedRun(String scenarioId, String status, boolean planMode) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status, autonomous_run_scenario_id,"
                + " autonomous_run_plan_mode)"
                + " VALUES (:id, :tenant, 'Own the domain', :status, :scenarioId, :planMode)")
        .setParameter("id", id)
        .setParameter("tenant", memberTenant)
        .setParameter("status", status)
        .setParameter("scenarioId", scenarioId)
        .setParameter("planMode", planMode)
        .executeUpdate();
    return id;
  }

  private MockHttpServletRequestBuilder headerRoute(String path) {
    return post(PLAIN + path).header("X-Tenant-Ids", memberTenant).with(csrf());
  }

  @Nested
  @DisplayName("Non-prefixed route, X-Tenant-Ids naming the member tenant")
  class NonPrefixedRoute {

    @Test
    @DisplayName("given_runOfMemberTenantAndSelectorNamingIt_should_restartIt")
    void given_runOfMemberTenantAndSelectorNamingIt_should_restartIt() throws Exception {
      // Arrange: ambient tenant cleared to the default, like the orchestrator's own route leaves
      // it; only the X-Tenant-Ids selector carries the member tenant.
      String scenarioId = seedChainingScenario();
      String runId = seedRun(scenarioId, "COMPLETED", true);
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

      // Act
      int status =
          mvc.perform(headerRoute("/" + runId + "/restart")).andReturn().getResponse().getStatus();

      // Assert
      assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("given_plannedRunOfMemberTenantAndSelectorNamingIt_should_promoteItToARealRun")
    void given_plannedRunOfMemberTenantAndSelectorNamingIt_should_promoteItToARealRun()
        throws Exception {
      // Arrange
      String scenarioId = seedChainingScenario();
      String runId = seedRun(scenarioId, "PLANNED", true);
      TenantContext.clearCurrentTenant();

      // Act
      int status =
          mvc.perform(headerRoute("/" + runId + "/promote")).andReturn().getResponse().getStatus();

      // Assert
      assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName(
        "given_settledRunOfMemberTenantAndSelectorNamingIt_should_convertItToManualInPlace")
    void given_settledRunOfMemberTenantAndSelectorNamingIt_should_convertItToManualInPlace()
        throws Exception {
      // Arrange
      String scenarioId = seedChainingScenario();
      String runId = seedRun(scenarioId, "COMPLETED", false);
      TenantContext.clearCurrentTenant();

      // Act
      int status =
          mvc.perform(
                  headerRoute("/" + runId + "/convert-to-manual")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"mode\": \"" + ConvertToManualMode.IN_PLACE + "\"}"))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(status).isEqualTo(200);
    }
  }
}
