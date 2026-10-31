package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exercise.form.ExerciseUpdateStatusInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A status change or a deletion of a chained simulation reaches its workflow, whatever the caller's
 * scope: the workflow is read and written in the simulation's own tenant. A cancelled simulation
 * has no run left active, a started one has a run, a paused one has its run stopped, a reset one
 * has no executed run or chained inject left, and a deleted one leaves no attack path behind.
 *
 * <p>The simulation lives in the default tenant. Each call is sent on the non-prefixed route by a
 * caller whose scope excludes that tenant (a platform admin who is a member of another tenant only,
 * or of no tenant at all) and by a member of the simulation's tenant: the outcome is the same.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * rows are committed, because the reset clears the persistence context, and removed after each
 * test. Quartz is off so that no live trigger runs the chaining engine on them.
 */
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@TestInstance(PER_CLASS)
@DisplayName("A status change or a deletion of a chained simulation reaches its workflow")
class ChainedSimulationLifecycleReachesItsWorkflowTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private String otherTenantId;

  @AfterAll
  void deleteOtherTenant() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  private record ChainedSimulation(String simulationId, String injectId) {}

  /**
   * Commits a chained simulation in the given status: its workflow template and, when {@code
   * runStatus} is set, a run with a step template and a due delayed entry, the state a running
   * chained simulation is in between two engine ticks.
   */
  private ChainedSimulation commitChainedSimulation(
      Exercise simulation, WorkflowStatus runStatus, boolean withInject) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              simulation.setName("chained-status-" + UUID.randomUUID());
              Exercise savedSimulation = exerciseRepository.save(simulation);

              Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
              template.setSimulation(savedSimulation);
              Workflow savedTemplate = workflowRepository.save(template);

              if (runStatus != null) {
                Workflow run = WorkflowFixture.getDefaultWorkflowExecution(runStatus);
                run.setSimulation(savedSimulation);
                run.setWorkflowTemplate(savedTemplate);
                Workflow savedRun = workflowRepository.save(run);
                Step stepTemplate = StepFixture.getDefaultStepTemplate();
                stepTemplate.setWorkflow(savedRun);
                Step savedStepTemplate = stepRepository.save(stepTemplate);
                stepDelayQueueRepository.save(
                    StepDelayQueue.builder()
                        .input("{}")
                        .now(Instant.now())
                        .goal(Instant.now().minusSeconds(60))
                        .delay(0L)
                        .stepTemplate(savedStepTemplate)
                        .workflowRun(savedRun)
                        .build());
              }

              String injectId = null;
              if (withInject) {
                Inject inject = InjectFixture.getDefaultInject();
                inject.setExercise(savedSimulation);
                injectId = injectRepository.save(inject).getId();
              }
              return new ChainedSimulation(savedSimulation.getId(), injectId);
            });
  }

  /** Sends the transition with no tenant prefix, from the request thread's empty ambient tenant. */
  private int changeStatus(String simulationId, ExerciseStatus target) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    ExerciseUpdateStatusInput input = new ExerciseUpdateStatusInput();
    input.setStatus(target);
    return mvc.perform(
            put(EXERCISE_URI + "/" + simulationId + "/status")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  // Read outside the tenant inspector: the assertions are about the stored rows, not about what a
  // scope lets the test see.
  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private ExerciseStatus simulationStatus(String simulationId) {
    return ExerciseStatus.valueOf(
        jdbc()
            .queryForObject(
                "SELECT exercise_status FROM exercises WHERE exercise_id = ?",
                String.class,
                simulationId));
  }

  private List<WorkflowStatus> runStatuses(String simulationId) {
    return jdbc()
        .queryForList(
            "SELECT workflow_status FROM workflows WHERE workflow_simulation_id = ?"
                + " AND workflow_status <> 'TEMPLATE'",
            String.class,
            simulationId)
        .stream()
        .map(WorkflowStatus::valueOf)
        .toList();
  }

  private int injectCount(String injectId) {
    return jdbc()
        .queryForObject(
            "SELECT count(*) FROM injects WHERE inject_id = ?", Integer.class, injectId);
  }

  private void remove(ChainedSimulation rows) {
    jdbc()
        .update(
            "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
            rows.simulationId());
    jdbc().update("DELETE FROM exercises WHERE exercise_id = ?", rows.simulationId());
  }

  /** Records one attack path execution of the simulation, a row with no key to the simulation. */
  private void commitAttackPathExecution(String simulationId) {
    jdbc()
        .update(
            "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
                + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
                + " attackpath_execution_target_kind, attackpath_execution_target_key,"
                + " attackpath_execution_executed_at)"
                + " VALUES (?, ?, ?, 'INJECTOR', 'ASSET', 'target', now())",
            UUID.randomUUID().toString(),
            Tenant.DEFAULT_TENANT_UUID,
            simulationId);
  }

  private int attackPathExecutionCount(String simulationId) {
    return jdbc()
        .queryForObject(
            "SELECT count(*) FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
            Integer.class,
            simulationId);
  }

  private int deleteSimulation(String simulationId) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(delete(EXERCISE_URI + "/" + simulationId).with(csrf()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  /**
   * Makes the current caller a member of another tenant than the simulation's. The tenant is
   * created once, by the first caller that needs it: onboarding a tenant attaches the user who
   * creates it.
   */
  private void joinOtherTenantOnly() {
    if (otherTenantId == null) {
      otherTenantId =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    try {
                      return tenantHelper.createTenant("chained-status").getId();
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  });
    }
    tenantHelper.attachCurrentUserToTenant(otherTenantId);
  }

  // -- Assertions shared by every caller --

  private void assertCancelReachedTheWorkflow(int httpStatus, ChainedSimulation rows) {
    assertThat(httpStatus).isEqualTo(200);
    assertThat(simulationStatus(rows.simulationId())).isEqualTo(ExerciseStatus.CANCELED);
    assertThat(runStatuses(rows.simulationId()))
        .as("a cancelled simulation leaves no run active")
        .containsOnly(WorkflowStatus.END);
  }

  private void assertStartReachedTheWorkflow(int httpStatus, ChainedSimulation rows) {
    assertThat(httpStatus).isEqualTo(200);
    assertThat(simulationStatus(rows.simulationId())).isNotEqualTo(ExerciseStatus.SCHEDULED);
    assertThat(runStatuses(rows.simulationId()))
        .as("a started chained simulation has a workflow run")
        .isNotEmpty();
  }

  private void assertPauseReachedTheWorkflow(int httpStatus, ChainedSimulation rows) {
    assertThat(httpStatus).isEqualTo(200);
    assertThat(simulationStatus(rows.simulationId())).isEqualTo(ExerciseStatus.PAUSED);
    assertThat(runStatuses(rows.simulationId()))
        .as("a paused simulation has its run stopped")
        .containsExactly(WorkflowStatus.STOP);
  }

  private void assertResetReachedTheWorkflow(int httpStatus, ChainedSimulation rows) {
    assertThat(httpStatus).isEqualTo(200);
    assertThat(simulationStatus(rows.simulationId())).isEqualTo(ExerciseStatus.SCHEDULED);
    assertThat(runStatuses(rows.simulationId()))
        .as("a reset simulation keeps no executed run")
        .isEmpty();
    assertThat(injectCount(rows.injectId()))
        .as("a reset chained simulation keeps no executed inject")
        .isZero();
  }

  @Nested
  @DisplayName("Cancel")
  class Cancel {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should cancel the run")
    void given_scopeWithoutItsTenant_should_cancelTheRun() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createRunningAttackExercise(), WorkflowStatus.RUN, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.CANCELED);

        // Assert
        assertCancelReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should cancel the run")
    void given_membershipOfItsTenant_should_cancelTheRun() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createRunningAttackExercise(), WorkflowStatus.RUN, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.CANCELED);

        // Assert
        assertCancelReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }

  @Nested
  @DisplayName("Start")
  class Start {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should start a run")
    void given_scopeWithoutItsTenant_should_startARun() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createDefaultAttackExercise(Instant.now()), null, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.RUNNING);

        // Assert
        assertStartReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should start a run")
    void given_membershipOfItsTenant_should_startARun() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createDefaultAttackExercise(Instant.now()), null, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.RUNNING);

        // Assert
        assertStartReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }

  @Nested
  @DisplayName("Pause")
  class Pause {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should stop the run")
    void given_scopeWithoutItsTenant_should_stopTheRun() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createRunningAttackExercise(), WorkflowStatus.RUN, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.PAUSED);

        // Assert
        assertPauseReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should stop the run")
    void given_membershipOfItsTenant_should_stopTheRun() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createRunningAttackExercise(), WorkflowStatus.RUN, false);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.PAUSED);

        // Assert
        assertPauseReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }

  @Nested
  @DisplayName("Reset")
  class Reset {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should clear the run")
    void given_scopeWithoutItsTenant_should_clearTheRun() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createCanceledAttackExercise(Instant.now()),
              WorkflowStatus.END,
              true);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.SCHEDULED);

        // Assert
        assertResetReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should clear the run")
    void given_noTenantMembership_should_clearTheRun() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createCanceledAttackExercise(Instant.now()),
              WorkflowStatus.END,
              true);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.SCHEDULED);

        // Assert
        assertResetReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should clear the run")
    void given_membershipOfItsTenant_should_clearTheRun() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createCanceledAttackExercise(Instant.now()),
              WorkflowStatus.END,
              true);
      try {
        // Act
        int httpStatus = changeStatus(rows.simulationId(), ExerciseStatus.SCHEDULED);

        // Assert
        assertResetReachedTheWorkflow(httpStatus, rows);
      } finally {
        remove(rows);
      }
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given a scope without its tenant should delete its attack path")
    void given_scopeWithoutItsTenant_should_deleteItsAttackPath() throws Exception {
      // Arrange
      joinOtherTenantOnly();
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createCanceledAttackExercise(Instant.now()),
              WorkflowStatus.END,
              false);
      commitAttackPathExecution(rows.simulationId());
      try {
        // Act
        int httpStatus = deleteSimulation(rows.simulationId());

        // Assert
        assertThat(httpStatus).isEqualTo(200);
        assertThat(attackPathExecutionCount(rows.simulationId())).isZero();
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should delete its attack path")
    void given_membershipOfItsTenant_should_deleteItsAttackPath() throws Exception {
      // Arrange
      ChainedSimulation rows =
          commitChainedSimulation(
              ExerciseFixture.createCanceledAttackExercise(Instant.now()),
              WorkflowStatus.END,
              false);
      commitAttackPathExecution(rows.simulationId());
      try {
        // Act
        int httpStatus = deleteSimulation(rows.simulationId());

        // Assert
        assertThat(httpStatus).isEqualTo(200);
        assertThat(attackPathExecutionCount(rows.simulationId())).isZero();
      } finally {
        remove(rows);
      }
    }
  }
}
