package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link WorkflowTimeoutJob} polls the expired runs of every tenant, then force-completes each run
 * inside its own tenant's scope, with the chaining tables tenant-active.
 *
 * <p>Not {@code @Transactional}, like the job: the scoped transactions it opens refuse to nest in a
 * test transaction. Rows are seeded through an auto-committing {@link JdbcTemplate}, so the job
 * reads committed rows as it does in production, and are swept explicitly.
 */
@WithMockUser(isAdmin = true)
@TestPropertySource(
    properties =
        "openaev.tenant.active-tables=workflows,steps,conditions,scope_variables,"
            + "steps_delay_queue,workflow_scope_rules,workflow_states")
@DisplayName("Workflow timeout job tenant scope")
class WorkflowTimeoutPollScopeTest extends IntegrationTest {

  @Autowired private WorkflowTimeoutJob workflowTimeoutJob;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private SeededRun runA;
  private SeededRun runB;

  private record SeededRun(String tenantId, String exerciseId, String workflowId, String stepId) {}

  @BeforeEach
  void seedAnExpiredRunInTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    runA = seedExpiredRun();
    runB = seedExpiredRun();
  }

  @AfterEach
  void sweep() {
    for (SeededRun run : new SeededRun[] {runA, runB}) {
      jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", run.exerciseId());
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", run.tenantId());
    }
  }

  @Nested
  @DisplayName("Expired runs in two tenants")
  class ExpiredRunsInTwoTenants {

    @Test
    @DisplayName("given an expired run in each of two tenants, the job ends both runs")
    void given_expiredRunInTwoTenants_should_endBothRuns() {
      // Arrange
      TenantContext.clearCurrentTenant();

      // Act
      workflowTimeoutJob.execute(null);

      // Assert
      assertThat(workflowStatus(runA)).isEqualTo("END");
      assertThat(workflowStatus(runB)).isEqualTo("END");
    }

    @Test
    @DisplayName("given an expired run in each of two tenants, each run's active step is ended")
    void given_expiredRunInTwoTenants_should_endEachRunsActiveStep() {
      // Arrange
      TenantContext.clearCurrentTenant();

      // Act
      workflowTimeoutJob.execute(null);

      // Assert
      assertThat(stepStatus(runA)).isEqualTo("END");
      assertThat(stepStatus(runB)).isEqualTo("END");
    }
  }

  private SeededRun seedExpiredRun() {
    String tenantId = UUID.randomUUID().toString();
    String exerciseId = UUID.randomUUID().toString();
    String workflowId = UUID.randomUUID().toString();
    String stepId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "timeout-poll-" + tenantId);
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " tenant_id) VALUES (?, 'chained simulation', 'sim@openaev.io', 'RUNNING', ?)",
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
            + " workflow_version, workflow_timeout_enabled, workflow_timeout_seconds,"
            + " workflow_created_at, workflow_updated_at, tenant_id)"
            + " VALUES (?, 'RUN', ?, 0, true, 60, now() - interval '2 hours', now(), ?)",
        workflowId,
        exerciseId,
        tenantId);
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_limit_execution, step_status,"
            + " step_workflow_id, tenant_id) VALUES (?, 'INJECT_EXECUTION', 1, 'READY', ?, ?)",
        stepId,
        workflowId,
        tenantId);
    return new SeededRun(tenantId, exerciseId, workflowId, stepId);
  }

  private String workflowStatus(SeededRun run) {
    return jdbc.queryForObject(
        "SELECT workflow_status FROM workflows WHERE workflow_id = ?",
        String.class,
        run.workflowId());
  }

  private String stepStatus(SeededRun run) {
    return jdbc.queryForObject(
        "SELECT step_status FROM steps WHERE step_id = ?", String.class, run.stepId());
  }
}
