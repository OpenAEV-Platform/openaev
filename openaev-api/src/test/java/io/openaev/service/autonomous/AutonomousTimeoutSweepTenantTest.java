package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * The autonomous run watchdog sweeps the runs of every tenant on a bare thread, as a Quartz worker
 * does: no request, no transaction, no ambient tenant. Each run is settled in a transaction scoped
 * to the run's tenant, and its simulation is stopped in that tenant, so a run past its deadline and
 * a run gone silent are both settled with their simulation, whatever their tenant.
 *
 * <p>Not {@code @Transactional}: the sweep opens one transaction per tenant and one per run, which
 * do not see rows left uncommitted in a test-managed one. The rows are committed through JDBC in
 * two tenants of their own and removed after each test.
 */
@WithMockUser(isAdmin = true)
@RecordApplicationEvents
@DisplayName("the autonomous run watchdog settles each run in its own tenant")
class AutonomousTimeoutSweepTenantTest extends IntegrationTest {

  @Autowired private AutonomousTimeoutService autonomousTimeoutService;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationEvents applicationEvents;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();

  @BeforeEach
  void openJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void removeTheTenantsAndWhatTheyOwn() {
    TenantContext.clearCurrentTenant();
    // Every foreign key to tenants cascades: the delete removes the runs, their timeline and
    // their simulations.
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Nested
  @DisplayName("runs of two tenants")
  class TwoTenants {

    @Test
    @DisplayName("given_runsPastTheirDeadline_should_cancelEachRunAndItsSimulationInItsTenant")
    void given_runsPastTheirDeadline_should_cancelEachRunAndItsSimulationInItsTenant() {
      // Arrange
      String tenantA = seedTenant();
      String tenantB = seedTenant();
      String simulationOfA = seedRunningSimulation(tenantA);
      String simulationOfB = seedRunningSimulation(tenantB);
      String runOfA = seedRunPastItsDeadline(tenantA, simulationOfA);
      String runOfB = seedRunPastItsDeadline(tenantB, simulationOfB);
      clearAmbientTenant();

      // Act
      autonomousTimeoutService.sweep();

      // Assert
      assertSettled(runOfA, tenantA, simulationOfA, "CANCELED");
      assertSettled(runOfB, tenantB, simulationOfB, "CANCELED");
    }

    @Test
    @DisplayName("given_runsGoneSilent_should_failEachRunAndCancelItsSimulationInItsTenant")
    void given_runsGoneSilent_should_failEachRunAndCancelItsSimulationInItsTenant() {
      // Arrange
      String tenantA = seedTenant();
      String tenantB = seedTenant();
      String simulationOfA = seedRunningSimulation(tenantA);
      String simulationOfB = seedRunningSimulation(tenantB);
      String runOfA = seedSilentRun(tenantA, simulationOfA);
      String runOfB = seedSilentRun(tenantB, simulationOfB);
      clearAmbientTenant();

      // Act
      autonomousTimeoutService.sweep();

      // Assert
      assertSettled(runOfA, tenantA, simulationOfA, "FAILED");
      assertSettled(runOfB, tenantB, simulationOfB, "FAILED");
    }
  }

  // -- assertions --

  private void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private void assertSettled(String runId, String tenantId, String simulationId, String runStatus) {
    assertThat(
            jdbc.queryForObject(
                "SELECT autonomous_run_status FROM autonomous_runs WHERE autonomous_run_id = ?",
                String.class,
                runId))
        .isEqualTo(runStatus);
    assertThat(
            jdbc.queryForObject(
                "SELECT exercise_status FROM exercises WHERE exercise_id = ?",
                String.class,
                simulationId))
        .isEqualTo("CANCELED");
    assertThat(
            jdbc.queryForList(
                "SELECT tenant_id FROM autonomous_events WHERE autonomous_event_run_id = ?",
                String.class,
                runId))
        .isNotEmpty()
        .containsOnly(tenantId);
    assertThat(
            applicationEvents.stream(BaseEvent.class)
                .filter(event -> event.getInstance() instanceof Exercise)
                .filter(event -> simulationId.equals(event.getInstance().getId()))
                .map(BaseEvent::getTenantId))
        .isNotEmpty()
        .containsOnly(tenantId);
  }

  // -- seeding --

  private String seedTenant() {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "autonomous-timeout-sweep-" + tenantId);
    seededTenants.add(tenantId);
    return tenantId;
  }

  private String seedRunningSimulation(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, exercise_start_date,"
            + " tenant_id) VALUES (?, 'autonomous-timeout-sweep', 'RUNNING', now(), ?)",
        simulationId,
        tenantId);
    return simulationId;
  }

  private String seedRunPastItsDeadline(String tenantId, String simulationId) {
    String runId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_simulation_id, autonomous_run_started_at,"
            + " autonomous_run_deadline_at) VALUES (?, ?, 'timeout sweep', 'RUNNING', ?,"
            + " now() - interval '2 hours', now() - interval '1 minute')",
        runId,
        tenantId,
        simulationId);
    return runId;
  }

  private String seedSilentRun(String tenantId, String simulationId) {
    String runId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status, autonomous_run_simulation_id, autonomous_run_started_at)"
            + " VALUES (?, ?, 'stall sweep', 'RUNNING', ?, now() - interval '2 hours')",
        runId,
        tenantId,
        simulationId);
    return runId;
  }
}
