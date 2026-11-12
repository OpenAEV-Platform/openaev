package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmone.XtmOneClient;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins that authorizing a read of an autonomous run in every tenant of the request scope leaves the
 * run as it is. The caller is not an admin and is a member of the default tenant and of a tenant B.
 * The run and its simulation live in the default tenant and are running.
 *
 * <p>Not transactional: a run's status is settled in a transaction of its own and the orchestrator
 * is stopped after the request commits, so the rows are committed and removed explicitly.
 */
@WithMockUser(isAdmin = false)
@DisplayName(
    "Reading an autonomous run by id leaves the run untouched in every tenant of the scope")
class AutonomousRunScopeAuthorizationTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String RUN_BY_ID_URI = AUTONOMOUS_URI + "/{runId}";
  private static final Set<Capability> SIMULATION_READ = Set.of(Capability.ACCESS_ASSESSMENT);

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExerciseComposer exerciseComposer;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private XtmOneClient xtmOneClient;

  private String tenantB;
  private String runId;
  private ExerciseComposer.Composer simulation;

  /** What the caller holds in the default tenant and in tenant B, and the read outcome. */
  enum Rights {
    SIMULATION_READ_IN_BOTH(SIMULATION_READ, SIMULATION_READ, HttpStatus.OK),
    SIMULATION_READ_IN_DEFAULT_ONLY(SIMULATION_READ, Set.of(), HttpStatus.FORBIDDEN),
    BYPASS_IN_DEFAULT_SIMULATION_READ_IN_B(
        Set.of(Capability.BYPASS), SIMULATION_READ, HttpStatus.OK);

    private final Set<Capability> inDefaultTenant;
    private final Set<Capability> inTenantB;
    private final HttpStatus expectedStatus;

    Rights(Set<Capability> inDefaultTenant, Set<Capability> inTenantB, HttpStatus expectedStatus) {
      this.inDefaultTenant = inDefaultTenant;
      this.inTenantB = inTenantB;
      this.expectedStatus = expectedStatus;
    }
  }

  @AfterEach
  void cleanup() {
    if (runId != null) {
      jdbcTemplate.update("DELETE FROM autonomous_events WHERE autonomous_event_run_id = ?", runId);
      jdbcTemplate.update("DELETE FROM autonomous_runs WHERE autonomous_run_id = ?", runId);
    }
    if (simulation != null) {
      inDefaultTenant(
          () -> {
            simulation.delete();
            return null;
          });
    }
    tenantHelper.deleteCommittedTenants(tenantB);
    exerciseComposer.reset();
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Run and simulation running in the default tenant")
  class RunningInDefaultTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Rights.class)
    @DisplayName("No selector: the run keeps its status and its orchestration")
    void given_noSelector_should_leaveTheRunUntouched(Rights rights) throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, rights.inDefaultTenant);
      tenantB =
          tenantHelper.createTenantWithCapabilities("auto-scope-authz-b", rights.inTenantB).getId();
      String simulationId = seedRunningSimulationInDefaultTenant();
      runId = seedRunningRunInDefaultTenant(simulationId);

      // Act
      int status = performWithoutAmbientTenant();

      // Assert
      assertAll(
          () -> assertEquals(rights.expectedStatus.value(), status, "the read outcome"),
          () ->
              assertEquals(
                  AutonomousRunStatus.RUNNING.name(), storedStatus(runId), "the run's status"),
          () -> verify(xtmOneClient, never()).cancelAutonomousRun(any(), any(), anyBoolean()));
    }
  }

  private int performWithoutAmbientTenant() throws Exception {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return mvc.perform(get(RUN_BY_ID_URI, runId)).andReturn().getResponse().getStatus();
  }

  private String seedRunningSimulationInDefaultTenant() {
    return inDefaultTenant(
        () -> {
          simulation =
              exerciseComposer.forExercise(ExerciseFixture.createRunningAttackExercise()).persist();
          return simulation.get().getId();
        });
  }

  private String seedRunningRunInDefaultTenant(String simulationId) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_simulation_id)"
            + " VALUES (?, ?, 'Own the domain', 'RUNNING', ?)",
        id,
        DEFAULT_TENANT,
        simulationId);
    return id;
  }

  /** Read through JDBC, past the statement inspector, so the row is seen whatever the scope. */
  private String storedStatus(String id) {
    return jdbcTemplate.queryForObject(
        "SELECT autonomous_run_status FROM autonomous_runs WHERE autonomous_run_id = ?",
        String.class,
        id);
  }

  private <T> T inDefaultTenant(Supplier<T> work) {
    TenantContext.setCurrentTenant(DEFAULT_TENANT);
    try {
      return tenantTx.execute(TxCtx.forTenant(DEFAULT_TENANT), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
