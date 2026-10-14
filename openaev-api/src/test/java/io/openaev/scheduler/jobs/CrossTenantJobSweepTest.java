package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.helper.InjectHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
 * The scheduler's cross-tenant sweeps over {@code exercises}, {@code injects} and {@code scenarios}
 * reach another tenant's rows. Those tables are still on the v1 filter, and the sweeps stay
 * cross-tenant because nothing arms that filter on a Quartz worker: no transaction is open there,
 * so no {@code HibernateFilterTransactionAspect} runs (see {@code TenantFilterToggleReachTest}).
 *
 * <p>Each sweep runs on a bare thread, as a Quartz worker does: no transaction, no session, no
 * ambient tenant. A sweep narrowed to the default tenant, the way an armed filter would narrow it,
 * misses the second tenant's row and turns its test red. Not {@code @Transactional}: rows are
 * committed through JDBC in a tenant of their own and removed after each test. The active-tables
 * list is pinned so the statement inspector never scopes these tables here, whatever a shadow run
 * arms; once they are activated each sweep needs the primitive, and this class is where that shows.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@WithMockUser(isAdmin = true)
@DisplayName("The scheduler's cross-tenant sweeps reach a second tenant's rows")
class CrossTenantJobSweepTest extends IntegrationTest {

  @Autowired private InjectsExecutionJob injectsExecutionJob;
  @Autowired private InjectsFinalizationJob injectsFinalizationJob;
  @Autowired private ScenarioExecutionJob scenarioExecutionJob;
  @Autowired private InjectHelper injectHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;

  @BeforeEach
  void seedASecondTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "cross-tenant-sweep-" + tenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update(
        "DELETE FROM injects_statuses WHERE status_inject IN"
            + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
        tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  private static <T> T onBareThread(Supplier<T> work) throws InterruptedException {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                result.set(work.get());
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the sweep failed", failure.get());
    }
    return result.get();
  }

  private String seedExercise(String status, Instant start) {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, ?, ?, ?, ?)",
        exerciseId,
        "cross-tenant-sweep",
        "sweep@openaev.invalid",
        status,
        Timestamp.from(start),
        tenantId);
    return exerciseId;
  }

  private String seedInject(String exerciseId, String collectStatus) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_collect_status, tenant_id)"
            + " VALUES (?, ?, false, true, 0, ?, ?, ?)",
        injectId,
        "cross-tenant-sweep",
        exerciseId,
        collectStatus,
        tenantId);
    return injectId;
  }

  private void seedStatus(String injectId, String statusName, Instant trackingSentDate) {
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        injectId,
        statusName,
        Timestamp.from(trackingSentDate));
  }

  @Nested
  @DisplayName("InjectsExecutionJob")
  class InjectsExecution {

    @Test
    @DisplayName("given a due simulation in another tenant, should promote it to running")
    void given_dueSimulationInAnotherTenant_should_promoteItToRunning() throws Exception {
      // Arrange
      String exerciseId = seedExercise("SCHEDULED", Instant.now().minus(1, ChronoUnit.MINUTES));

      // Act
      onBareThread(injectsExecutionJob::autoStartDueExercises);

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT exercise_status FROM exercises WHERE exercise_id = ?",
                  String.class,
                  exerciseId))
          .isEqualTo("RUNNING");
    }
  }

  @Nested
  @DisplayName("InjectsFinalizationJob")
  class InjectsFinalization {

    @Test
    @DisplayName("given a collecting inject in another tenant, should complete its collection")
    void given_collectingInjectInAnotherTenant_should_completeItsCollection() throws Exception {
      // Arrange: no expectation left to collect, so the sweep completes it
      String exerciseId = seedExercise("RUNNING", Instant.now().minus(1, ChronoUnit.HOURS));
      String injectId = seedInject(exerciseId, "COLLECTING");
      seedStatus(injectId, "EXECUTED", Instant.now().minus(1, ChronoUnit.HOURS));

      // Act
      onBareThread(
          () -> {
            injectsFinalizationJob.handleInjectExpectationCollectStatus();
            return null;
          });

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT inject_collect_status FROM injects WHERE inject_id = ?",
                  String.class,
                  injectId))
          .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("given a pending inject past the threshold in another tenant, should return it")
    void given_pendingInjectPastThresholdInAnotherTenant_should_returnIt() throws Exception {
      // Arrange
      String exerciseId = seedExercise("RUNNING", Instant.now().minus(1, ChronoUnit.HOURS));
      String injectId = seedInject(exerciseId, null);
      seedStatus(injectId, "PENDING", Instant.now().minus(1, ChronoUnit.HOURS));

      // Act
      List<Inject> pending =
          onBareThread(() -> injectHelper.getAllPendingInjectsWithThresholdMinutes(10));

      // Assert
      assertThat(pending).extracting(Inject::getId).contains(injectId);
    }
  }

  @Nested
  @DisplayName("ScenarioExecutionJob")
  class ScenarioExecution {

    @Test
    @DisplayName(
        "given an outdated recurring scenario in another tenant, should clear its recurrence")
    void given_outdatedRecurringScenarioInAnotherTenant_should_clearItsRecurrence()
        throws Exception {
      // Arrange
      String scenarioId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from,"
              + " scenario_recurrence, scenario_recurrence_start, scenario_recurrence_end,"
              + " tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
          scenarioId,
          "cross-tenant-sweep",
          "sweep@openaev.invalid",
          "0 0 6 * * *",
          Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS)),
          Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
          tenantId);

      // Act
      onBareThread(
          () -> {
            try {
              scenarioExecutionJob.execute(null);
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
            return null;
          });

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT scenario_recurrence FROM scenarios WHERE scenario_id = ?",
                  String.class,
                  scenarioId))
          .isNull();
    }
  }
}
