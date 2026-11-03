package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The auto-close phase of {@code InjectsFinalizationJob} leaves a simulation owned by the chaining
 * engine to that engine, and finishes every other simulation whose injects are all done. It
 * recognises a chained simulation by its workflow, which only reads under a scope once {@code
 * workflows} is tenant-active, so the check runs inside the simulation's own tenant scope.
 *
 * <p>The production active tables are armed together with the chaining tables. The job runs on a
 * bare thread, as a Quartz worker does: no transaction, no session, no ambient tenant. Rows are
 * committed through JDBC in a tenant of their own and removed after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The auto-close leaves a running chained simulation to the chaining engine")
class AutoCloseChainedSimulationScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionAndChainingActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            Stream.concat(
                    ProductionActiveTables.names().stream(),
                    Stream.of(
                        "workflows",
                        "steps",
                        "conditions",
                        "scope_variables",
                        "steps_delay_queue",
                        "workflow_scope_rules",
                        "workflow_states"))
                .distinct()
                .collect(Collectors.joining(",")));
  }

  @Autowired private InjectsFinalizationJob injectsFinalizationJob;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;

  @BeforeEach
  void seedATenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "auto-close-chained-" + tenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM workflows WHERE tenant_id = ?", tenantId);
    jdbc.update(
        "DELETE FROM injects_statuses WHERE status_inject IN"
            + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
        tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  private void runAutoCloseOnBareThread() throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                injectsFinalizationJob.handleAutoClosingSimulations();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the auto-close failed", failure.get());
    }
  }

  /**
   * Seeds a RUNNING simulation whose only inject is executed and done collecting, so the auto-close
   * candidate query returns it. With a workflow status, the simulation is a chained one.
   */
  private String seedRunningSimulationWithDoneInjects(String workflowStatus) {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " tenant_id) VALUES (?, 'auto close', 'close@openaev.invalid', 'RUNNING', ?)",
        exerciseId,
        tenantId);
    if (workflowStatus != null) {
      jdbc.update(
          "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
              + " workflow_version, tenant_id) VALUES (?, CAST(? AS workflow_status), ?, 0, ?)",
          UUID.randomUUID().toString(),
          workflowStatus,
          exerciseId,
          tenantId);
    }
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_collect_status, tenant_id)"
            + " VALUES (?, 'auto close', false, true, 0, ?, 'COMPLETED', ?)",
        injectId,
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'EXECUTED', ?)",
        UUID.randomUUID().toString(),
        injectId,
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
    return exerciseId;
  }

  private String simulationStatus(String exerciseId) {
    return jdbc.queryForObject(
        "SELECT exercise_status FROM exercises WHERE exercise_id = ?", String.class, exerciseId);
  }

  @Nested
  @DisplayName("Chained simulation")
  class ChainedSimulation {

    @Test
    @DisplayName("given a running chained simulation whose injects are all done, it stays running")
    void given_runningChainedSimulationWithDoneInjects_should_stayRunning() throws Exception {
      // Arrange
      String chainedId = seedRunningSimulationWithDoneInjects("RUN");
      String plainId = seedRunningSimulationWithDoneInjects(null);

      // Act
      runAutoCloseOnBareThread();

      // Assert: the chained simulation is left alone while the same run finishes its neighbour
      assertThat(simulationStatus(chainedId)).isEqualTo("RUNNING");
      assertThat(simulationStatus(plainId)).isEqualTo("FINISHED");
    }
  }

  @Nested
  @DisplayName("Simulation without chaining")
  class WithoutChaining {

    @Test
    @DisplayName("given a running simulation whose injects are all done, it is finished")
    void given_runningSimulationWithDoneInjects_should_beFinished() throws Exception {
      // Arrange
      String exerciseId = seedRunningSimulationWithDoneInjects(null);

      // Act
      runAutoCloseOnBareThread();

      // Assert
      assertThat(simulationStatus(exerciseId)).isEqualTo("FINISHED");
    }
  }
}
