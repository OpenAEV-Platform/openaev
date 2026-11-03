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
 * The pending-inject sweep of {@code InjectsFinalizationJob} leaves the injects of a running
 * chaining workflow to the chaining engine, and finalizes every other pending inject past the
 * threshold. It excludes the chained ones with a sub-query on {@code workflows}, which only sees
 * rows under a scope once that table is tenant-active, so the sweep runs under {@code
 * allTenants()}.
 *
 * <p>The production active tables are armed together with the chaining tables. Each sweep runs on a
 * bare thread, as a Quartz worker does: no transaction, no session, no ambient tenant. Rows are
 * committed through JDBC in a tenant of their own and removed after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The pending-inject sweep leaves a running chained simulation to the chaining engine")
class PendingInjectChainingExclusionScopeTest extends IntegrationTest {

  private static final int OVERDUE_DAYS = 1;

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
        "pending-chained-" + tenantId);
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

  private void runSweepOnBareThread() throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                injectsFinalizationJob.handlePendingInject();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the sweep failed", failure.get());
    }
  }

  private String seedRunningSimulation(String workflowStatus) {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " tenant_id) VALUES (?, 'pending sweep', 'sweep@openaev.invalid', 'RUNNING', ?)",
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
    return exerciseId;
  }

  /** Seeds an overdue PENDING inject in the simulation and returns its status id. */
  private String seedOverduePendingInject(String exerciseId) {
    String injectId = UUID.randomUUID().toString();
    String statusId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, tenant_id)"
            + " VALUES (?, 'pending sweep', false, true, 0, ?, ?)",
        injectId,
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'PENDING', ?)",
        statusId,
        injectId,
        Timestamp.from(Instant.now().minus(OVERDUE_DAYS, ChronoUnit.DAYS)));
    return statusId;
  }

  private String statusName(String statusId) {
    return jdbc.queryForObject(
        "SELECT status_name FROM injects_statuses WHERE status_id = ?", String.class, statusId);
  }

  private int traceCount(String statusId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM execution_traces WHERE execution_inject_status_id = ?",
        Integer.class,
        statusId);
  }

  @Nested
  @DisplayName("Chained simulation")
  class ChainedSimulation {

    @Test
    @DisplayName("given a pending inject of a running chained simulation, it stays pending")
    void given_pendingInjectOfRunningChainedSimulation_should_stayPending() throws Exception {
      // Arrange
      String chainedStatusId = seedOverduePendingInject(seedRunningSimulation("RUN"));
      String plainStatusId = seedOverduePendingInject(seedRunningSimulation(null));

      // Act
      runSweepOnBareThread();

      // Assert: the chained inject is left alone while the same sweep finalizes its neighbour
      assertThat(statusName(chainedStatusId)).isEqualTo("PENDING");
      assertThat(traceCount(chainedStatusId)).isZero();
      assertThat(statusName(plainStatusId)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("given a pending inject of a simulation whose workflow ended, it is finalized")
    void given_pendingInjectOfEndedChainedSimulation_should_beFinalized() throws Exception {
      // Arrange
      String statusId = seedOverduePendingInject(seedRunningSimulation("END"));

      // Act
      runSweepOnBareThread();

      // Assert
      assertThat(statusName(statusId)).isEqualTo("ERROR");
      assertThat(traceCount(statusId)).isPositive();
    }
  }

  @Nested
  @DisplayName("Simulation without chaining")
  class WithoutChaining {

    @Test
    @DisplayName("given a pending inject in a non-default tenant, it is finalized with a trace")
    void given_pendingInjectInNonDefaultTenant_should_beFinalized() throws Exception {
      // Arrange
      String statusId = seedOverduePendingInject(seedRunningSimulation(null));

      // Act
      runSweepOnBareThread();

      // Assert
      assertThat(statusName(statusId)).isEqualTo("ERROR");
      assertThat(traceCount(statusId)).isPositive();
    }
  }
}
