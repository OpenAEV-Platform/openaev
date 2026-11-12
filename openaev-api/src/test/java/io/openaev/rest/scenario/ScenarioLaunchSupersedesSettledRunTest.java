package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A manual (operator-driven) launch of a chained scenario supersedes the settled autonomous run it
 * carries: once access to the scenario is granted, the run it owns is read and torn down in the
 * scenario's own tenant, like its workflow.
 *
 * <p>The scenario lives in the default tenant. The call is sent on the non-prefixed route, by a
 * member of the scenario's tenant, and by a caller whose scope excludes it (a platform admin who is
 * a member of another tenant only). A by-id load is resolved in the request scope, so that second
 * caller answers "not found" instead of the scenario.
 *
 * <p>The production active tables are armed, so the class follows what production enforces for
 * {@code autonomous_runs}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("A manual launch supersedes the scenario's settled autonomous run in its own tenant")
class ScenarioLaunchSupersedesSettledRunTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AutonomousRunRepository autonomousRunRepository;
  @Autowired private EntityManager manager;

  // A chained scenario launch is an Enterprise Edition feature.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void before() {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    scenarioComposer.reset();
    workflowComposer.reset();
  }

  private record ChainedScenarioWithSettledRun(Scenario scenario, AutonomousRun run) {}

  /**
   * A chained scenario of the default tenant carrying a settled (CANCELED) autonomous run: a launch
   * must find it and supersede it.
   */
  private ChainedScenarioWithSettledRun chainedScenarioWithASettledRun() {
    ScenarioComposer.Composer scenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withScenario(scenario)
        .persist();
    Scenario persisted = scenario.get();
    String tenantId = persisted.getTenant().getId();

    AutonomousRun run = new AutonomousRun();
    run.setTenant(new Tenant(tenantId));
    run.setObjective("a settled run a manual launch must supersede");
    run.setStatus(AutonomousRunStatus.CANCELED);
    run.setScenarioId(persisted.getId());
    autonomousRunRepository.save(run);

    manager.flush();
    manager.clear();
    return new ChainedScenarioWithSettledRun(persisted, run);
  }

  /** Makes the current caller a member of another tenant than the scenario's, and of no other. */
  private void joinOtherTenantOnly() throws Exception {
    Tenant other = tenantHelper.createTenant("launch-supersede-other");
    tenantHelper.attachCurrentUserToTenant(other.getId());
  }

  /**
   * The request thread of the non-prefixed route carries no ambient tenant, while the fixtures
   * leave one on the test thread: give the test thread the request's state before the call.
   */
  private static void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private void launch(String scenarioId) throws Exception {
    clearAmbientTenant();
    mvc.perform(post(SCENARIO_URI + "/" + scenarioId + "/exercise/running").with(csrf()))
        .andExpect(status().isOk());
  }

  /** Whether the scenario's settled run still exists, read in the default tenant. */
  private boolean settledRunStillExists(String scenarioId) {
    DefaultTenantScopeTestListener.setAmbientScope(manager, Tenant.DEFAULT_TENANT_UUID);
    return autonomousRunRepository.findByScenarioId(scenarioId).isPresent();
  }

  @Nested
  @DisplayName("Launch")
  class Launch {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should not reach the scenario")
    void given_scopeWithoutItsTenant_should_notReachTheScenario() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedScenarioWithSettledRun rows = chainedScenarioWithASettledRun();
      clearAmbientTenant();

      // Act & Assert
      mvc.perform(
              post(SCENARIO_URI + "/" + rows.scenario().getId() + "/exercise/running").with(csrf()))
          .andExpect(status().isNotFound());
      assertThat(settledRunStillExists(rows.scenario().getId())).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should supersede the settled run")
    void given_membershipOfItsTenant_should_supersedeTheSettledRun() throws Exception {
      // Arrange
      ChainedScenarioWithSettledRun rows = chainedScenarioWithASettledRun();

      // Act
      launch(rows.scenario().getId());

      // Assert
      assertThat(settledRunStillExists(rows.scenario().getId())).isFalse();
    }
  }
}
