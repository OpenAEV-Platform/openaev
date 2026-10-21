package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The scheduler sweeps that move a simulation or a scenario through its lifecycle poll every
 * tenant's rows: the scheduled start, the auto-close, the security coverage send, the comcheck
 * preparation and the clean-up of an outdated recurrence. Once {@code exercises} and {@code
 * scenarios} are tenant-active, a poll with no scope reads nothing, so each poll spans the tenants
 * explicitly and each write runs inside the row's own tenant.
 *
 * <p>The production active tables are armed with the chaining tables and the two tables these
 * sweeps read. Every sweep runs on a bare thread, as a Quartz worker does: no transaction, no
 * session, no ambient tenant. Rows are committed through JDBC in tenants of their own and removed
 * after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The simulation lifecycle sweeps see every tenant's rows")
class SimulationLifecycleSweepScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionChainingAndSimulationTables(DynamicPropertyRegistry registry) {
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
                        "workflow_states",
                        "exercises",
                        "scenarios"))
                .distinct()
                .collect(Collectors.joining(",")));
  }

  @Autowired private InjectsExecutionJob injectsExecutionJob;
  @Autowired private InjectsFinalizationJob injectsFinalizationJob;
  @Autowired private ScenarioExecutionJob scenarioExecutionJob;
  @Autowired private SecurityCoverageJob securityCoverageJob;
  @Autowired private ComchecksExecutionJob comchecksExecutionJob;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private DataSource dataSource;

  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private final List<String> seededExercises = new ArrayList<>();
  private final List<String> seededScenarios = new ArrayList<>();
  private final List<String> seededUsers = new ArrayList<>();
  private final List<String> seededComchecks = new ArrayList<>();

  @BeforeEach
  void openJdbc() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void sweep() {
    for (String comcheckId : seededComchecks) {
      jdbc.update("DELETE FROM comchecks_statuses WHERE status_comcheck = ?", comcheckId);
      jdbc.update("DELETE FROM comchecks WHERE comcheck_id = ?", comcheckId);
    }
    for (String exerciseId : seededExercises) {
      jdbc.update(
          "DELETE FROM security_coverage_send_job WHERE security_coverage_send_job_simulation = ?",
          exerciseId);
      jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", exerciseId);
    }
    for (String scenarioId : seededScenarios) {
      jdbc.update("DELETE FROM scenarios WHERE scenario_id = ?", scenarioId);
    }
    for (String userId : seededUsers) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededComchecks.clear();
    seededExercises.clear();
    seededScenarios.clear();
    seededUsers.clear();
    seededTenants.clear();
  }

  // -- SEEDING --

  private String seedTenant() {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "lifecycle-sweep-" + tenantId);
    seededTenants.add(tenantId);
    return tenantId;
  }

  /** A tenant deleted by an administrator: its rows stay, and no scope resolves to it any more. */
  private String seedDeletedTenant() {
    String tenantId = seedTenant();
    jdbc.update("UPDATE tenants SET tenant_deleted_at = now() WHERE tenant_id = ?", tenantId);
    return tenantId;
  }

  private String seedExercise(String tenantId, String status, String startOffset) {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, 'sim@openaev.io', ?,"
            + " now() + CAST(? AS interval), ?)",
        exerciseId,
        "lifecycle simulation " + exerciseId,
        status,
        startOffset,
        tenantId);
    seededExercises.add(exerciseId);
    return exerciseId;
  }

  private String exerciseStatus(String exerciseId) {
    return jdbc.queryForObject(
        "SELECT exercise_status FROM exercises WHERE exercise_id = ?", String.class, exerciseId);
  }

  private String seedUser() {
    String userId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        userId,
        "comcheck-" + userId + "@openaev.io");
    seededUsers.add(userId);
    return userId;
  }

  private String seedRunningComcheck(String exerciseId) {
    String comcheckId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO comchecks (comcheck_id, comcheck_exercise, comcheck_start_date,"
            + " comcheck_end_date, comcheck_state, comcheck_name, comcheck_subject,"
            + " comcheck_message) VALUES (?, ?, now() - interval '1 hour',"
            + " now() + interval '1 day', 'RUNNING', 'lifecycle comcheck', 'subject', 'message')",
        comcheckId,
        exerciseId);
    seededComchecks.add(comcheckId);
    jdbc.update(
        "INSERT INTO comchecks_statuses (status_id, status_user, status_comcheck,"
            + " status_sent_retry) VALUES (?, ?, ?, 0)",
        UUID.randomUUID().toString(),
        seedUser(),
        comcheckId);
    return comcheckId;
  }

  /**
   * Registers the built-in email injector and its contracts in the tenant, as onboarding does. The
   * contract ids are then shared by every tenant that has it, as on any platform with more than one
   * tenant. Idempotent, and removed with the tenant for a seeded one.
   */
  private void provisionEmailInjector(String tenantId) {
    tenantScopedJobRunner.runInTenant(
        tenantId,
        () -> {
          try {
            emailInjectorIntegrationFactory.registerConnectorForTenant(tenantId);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  /** Runs the sweep the way a Quartz worker does: a fresh thread, no transaction, no tenant. */
  private static <T> T onBareThread(Callable<T> sweep) throws InterruptedException {
    AtomicReference<T> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                result.set(sweep.call());
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    assertThat(failure.get()).as("the sweep must not throw").isNull();
    return result.get();
  }

  // -- TESTS --

  @Nested
  @DisplayName("Scheduled start")
  class ScheduledStart {

    @Test
    @DisplayName("given due scheduled simulations in two tenants, the job starts both")
    void given_dueScheduledSimulationsInTwoTenants_should_startBoth() throws Exception {
      // -- Arrange --
      String first = seedExercise(seedTenant(), "SCHEDULED", "-1 minute");
      String second = seedExercise(seedTenant(), "SCHEDULED", "-2 minutes");

      // -- Act --
      List<Exercise> started = onBareThread(injectsExecutionJob::autoStartDueExercises);

      // -- Assert --
      assertThat(started).extracting(Exercise::getId).contains(first, second);
      assertThat(exerciseStatus(first)).isEqualTo("RUNNING");
      assertThat(exerciseStatus(second)).isEqualTo("RUNNING");
    }
  }

  @Nested
  @DisplayName("Auto-close")
  class AutoClose {

    @Test
    @DisplayName("given a running simulation with nothing left to execute, the job finishes it")
    void given_runningSimulationWithNothingLeft_should_beFinishedByTheJob() throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(seedTenant(), "RUNNING", "-1 hour");

      // -- Act --
      onBareThread(
          () -> {
            injectsFinalizationJob.handleAutoClosingSimulations();
            return null;
          });

      // -- Assert --
      assertThat(exerciseStatus(exerciseId)).isEqualTo("FINISHED");
    }
  }

  @Nested
  @DisplayName("Security coverage send sweep")
  class SecurityCoverageSend {

    @Test
    @DisplayName(
        "given a pending send job, the sweep pushes its bundle to the simulation's tenant and"
            + " marks it sent")
    void given_pendingSendJob_should_pushToItsTenantAndBeConsumed() throws Exception {
      // -- Arrange --
      String tenantId = seedTenant();
      String exerciseId = seedExercise(tenantId, "FINISHED", "-2 hours");
      String sendJobId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO security_coverage_send_job (security_coverage_send_job_id,"
              + " security_coverage_send_job_status, security_coverage_send_job_updated_at,"
              + " security_coverage_send_job_simulation) VALUES (?, 'PENDING',"
              + " now() - interval '5 minutes', ?)",
          sendJobId,
          exerciseId);
      ConnectorBase connector = mock(ConnectorBase.class);
      when(connector.isRegistered()).thenReturn(true);
      when(openCTIConnectorService.getConnectorBase(tenantId)).thenReturn(Optional.of(connector));

      // -- Act --
      onBareThread(
          () -> {
            securityCoverageJob.execute(null);
            return null;
          });

      // -- Assert --
      verify(openCTIConnectorService).pushSecurityCoverageStixBundle(any(), eq(tenantId));
      assertThat(sendJobStatus(sendJobId)).isEqualTo("SENT");
    }

    @Test
    @DisplayName(
        "given pending send jobs of an active and a deleted tenant, the sweep still delivers the"
            + " active tenant's bundle and leaves the deleted tenant's job untouched")
    void given_pendingSendJobOfDeletedTenant_should_stillDeliverTheActiveTenantsBundle()
        throws Exception {
      // -- Arrange --
      String activeTenant = seedTenant();
      String deletedTenant = seedDeletedTenant();
      String activeJob = seedPendingSendJob(seedExercise(activeTenant, "FINISHED", "-2 hours"));
      String deletedJob = seedPendingSendJob(seedExercise(deletedTenant, "FINISHED", "-2 hours"));
      ConnectorBase connector = mock(ConnectorBase.class);
      when(connector.isRegistered()).thenReturn(true);
      when(openCTIConnectorService.getConnectorBase(any())).thenReturn(Optional.of(connector));

      // -- Act --
      onBareThread(
          () -> {
            securityCoverageJob.execute(null);
            return null;
          });

      // -- Assert --
      verify(openCTIConnectorService).pushSecurityCoverageStixBundle(any(), eq(activeTenant));
      assertThat(sendJobStatus(activeJob)).isEqualTo("SENT");
      verify(openCTIConnectorService, never()).getConnectorBase(deletedTenant);
      verify(openCTIConnectorService, never()).getConnectorBase(isNull());
      assertThat(sendJobStatus(deletedJob)).isEqualTo("PENDING");
    }

    private String seedPendingSendJob(String exerciseId) {
      String sendJobId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO security_coverage_send_job (security_coverage_send_job_id,"
              + " security_coverage_send_job_status, security_coverage_send_job_updated_at,"
              + " security_coverage_send_job_simulation) VALUES (?, 'PENDING',"
              + " now() - interval '5 minutes', ?)",
          sendJobId,
          exerciseId);
      return sendJobId;
    }

    private String sendJobStatus(String sendJobId) {
      return jdbc.queryForObject(
          "SELECT security_coverage_send_job_status FROM security_coverage_send_job"
              + " WHERE security_coverage_send_job_id = ?",
          String.class,
          sendJobId);
    }
  }

  @Nested
  @DisplayName("Comcheck preparation")
  class ComcheckPreparation {

    @Test
    @DisplayName("given a running comcheck, its send task is built for its simulation's tenant")
    void given_runningComcheck_should_buildItsTaskInItsTenant() throws Exception {
      // -- Arrange --
      provisionEmailInjector(Tenant.DEFAULT_TENANT_UUID);
      String exerciseId = seedExercise(Tenant.DEFAULT_TENANT_UUID, "RUNNING", "-1 hour");
      String comcheckId = seedRunningComcheck(exerciseId);

      // -- Act --
      List<ComchecksExecutionJob.ComcheckSendTask> tasks =
          onBareThread(() -> comchecksExecutionJob.prepareSendTasks(Instant.now()));

      // -- Assert --
      List<ComchecksExecutionJob.ComcheckSendTask> ours =
          tasks.stream()
              .filter(task -> task.statuses().getFirst().getComcheck().getId().equals(comcheckId))
              .toList();
      assertThat(ours).hasSize(1);
      assertThat(ours.getFirst().tenantId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(ours.getFirst().injection().getInjection().getExercise().getName())
          .isEqualTo("lifecycle simulation " + exerciseId);
    }

    @Test
    @DisplayName(
        "given running comchecks in two tenants, each task is built in its own tenant, never in"
            + " the previous one's")
    void given_runningComchecksInTwoTenants_should_buildEachInItsOwnTenant() throws Exception {
      // -- Arrange --
      String otherTenant = seedTenant();
      provisionEmailInjector(Tenant.DEFAULT_TENANT_UUID);
      provisionEmailInjector(otherTenant);
      String defaultExercise = seedExercise(Tenant.DEFAULT_TENANT_UUID, "RUNNING", "-1 hour");
      String otherExercise = seedExercise(otherTenant, "RUNNING", "-1 hour");
      String defaultComcheck = seedRunningComcheck(defaultExercise);
      String otherComcheck = seedRunningComcheck(otherExercise);

      // -- Act --
      List<ComchecksExecutionJob.ComcheckSendTask> tasks =
          onBareThread(() -> comchecksExecutionJob.prepareSendTasks(Instant.now()));

      // -- Assert --
      assertThat(tenantOfTask(tasks, defaultComcheck)).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(tenantOfTask(tasks, otherComcheck)).isEqualTo(otherTenant);
      assertThat(exerciseOfTask(tasks, otherComcheck).getName())
          .isEqualTo("lifecycle simulation " + otherExercise);
      assertThat(
              exerciseOfTask(tasks, otherComcheck).getId().equals(otherExercise)
                  && exerciseOfTask(tasks, defaultComcheck).getId().equals(defaultExercise))
          .isTrue();
    }

    @Test
    @DisplayName(
        "given running comchecks of an active and a deleted tenant, the active tenant's task is"
            + " still built and the deleted tenant's comcheck is left out")
    void given_runningComcheckOfDeletedTenant_should_stillBuildTheActiveTenantsTask()
        throws Exception {
      // -- Arrange --
      String deletedTenant = seedDeletedTenant();
      provisionEmailInjector(Tenant.DEFAULT_TENANT_UUID);
      String activeExercise = seedExercise(Tenant.DEFAULT_TENANT_UUID, "RUNNING", "-1 hour");
      String deletedExercise = seedExercise(deletedTenant, "RUNNING", "-1 hour");
      String activeComcheck = seedRunningComcheck(activeExercise);
      String deletedComcheck = seedRunningComcheck(deletedExercise);

      // -- Act --
      List<ComchecksExecutionJob.ComcheckSendTask> tasks =
          onBareThread(() -> comchecksExecutionJob.prepareSendTasks(Instant.now()));

      // -- Assert --
      assertThat(tenantOfTask(tasks, activeComcheck)).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
      assertThat(tasks)
          .noneMatch(
              task -> task.statuses().getFirst().getComcheck().getId().equals(deletedComcheck));
      assertThat(tasks)
          .extracting(ComchecksExecutionJob.ComcheckSendTask::tenantId)
          .doesNotContain(deletedTenant);
    }

    private static ComchecksExecutionJob.ComcheckSendTask taskOf(
        List<ComchecksExecutionJob.ComcheckSendTask> tasks, String comcheckId) {
      List<ComchecksExecutionJob.ComcheckSendTask> matching =
          tasks.stream()
              .filter(task -> task.statuses().getFirst().getComcheck().getId().equals(comcheckId))
              .toList();
      assertThat(matching).hasSize(1);
      return matching.getFirst();
    }

    private static String tenantOfTask(
        List<ComchecksExecutionJob.ComcheckSendTask> tasks, String comcheckId) {
      return taskOf(tasks, comcheckId).tenantId();
    }

    private static Exercise exerciseOfTask(
        List<ComchecksExecutionJob.ComcheckSendTask> tasks, String comcheckId) {
      return taskOf(tasks, comcheckId).injection().getInjection().getExercise();
    }
  }

  @Nested
  @DisplayName("Outdated recurrence clean-up")
  class OutdatedRecurrence {

    @Test
    @DisplayName("given a recurring scenario whose recurrence has ended, the job clears it")
    void given_recurrenceEnded_should_beClearedByTheJob() throws Exception {
      // -- Arrange --
      String scenarioId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from,"
              + " scenario_recurrence, scenario_recurrence_start, scenario_recurrence_end,"
              + " tenant_id) VALUES (?, 'recurring scenario', 'sim@openaev.io', '0 0 8 * * *',"
              + " now() - interval '10 days', now() - interval '1 day', ?)",
          scenarioId,
          seedTenant());
      seededScenarios.add(scenarioId);

      // -- Act --
      onBareThread(
          () -> {
            scenarioExecutionJob.execute(null);
            return null;
          });

      // -- Assert --
      String recurrence =
          jdbc.queryForObject(
              "SELECT scenario_recurrence FROM scenarios WHERE scenario_id = ?",
              String.class,
              scenarioId);
      assertThat(recurrence).isNull();
    }
  }
}
