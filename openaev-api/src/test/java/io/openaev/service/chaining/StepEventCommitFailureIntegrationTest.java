package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.FlushModeType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Non-regression for #8976 (#8315): {@link StepEventService#handleReadyStepEvent} evaluates the
 * workflow run handed back by a failed step run even when the event transaction throws after that
 * hand-back, i.e. when its commit reports a failure.
 *
 * <p>The outcome only changes when the step's END is durable but the commit still throws: Spring
 * propagates an {@code afterCommit} failure to the caller with the transaction committed, and
 * production registers such callbacks on this path ({@code Executor} publishes external injects
 * after commit). Before the fix the evaluation was skipped, the retry path found the step no longer
 * READY, and nothing evaluated the run again: it stayed RUN forever.
 *
 * <p>NOT {@code @Transactional}: {@link TenantScopedTransaction#execute} refuses an active
 * transaction. Rows are committed in a tenant of their own and read back through raw JDBC.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Step run: the workflow run is evaluated even when the event commit fails (#8976)")
class StepEventCommitFailureIntegrationTest extends IntegrationTest {

  // Fault injection: the READY step's run fails and breaks the event transaction's commit.
  @MockitoSpyBean private InjectExecutionStep injectExecutionStep;
  // Observation only: records the scope each evaluation of the run sees, then evaluates for real.
  @MockitoSpyBean private WorkflowService workflowService;
  // Keeps a retried event off the broker.
  @MockitoSpyBean private QueueChainingService queueChainingService;

  @Autowired private StepEventService stepEventService;
  @Autowired private StepService stepService;
  @Autowired private ChainingConfig chainingConfig;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  /** What an evaluation of the run saw: the v1 scope, the v2 scope and its transaction. */
  private record EvaluationScope(String tenantContext, String tenantGuc, long transactionId) {}

  private final List<EvaluationScope> evaluations = new CopyOnWriteArrayList<>();
  private final AtomicLong eventTransactionId = new AtomicLong(-1);

  private JdbcTemplate jdbc;
  private Tenant tenant;
  private String simulationId;
  private String workflowTemplateId;
  private String workflowRunId;
  private String readyStepId;

  @BeforeEach
  void launchASingleStepChain() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("step-run-commit-failure");
    TenantContext.setCurrentTenant(tenant.getId());
    try {
      Exercise simulation =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
      simulationId = simulation.getId();

      Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
      template.setSimulation(simulation);
      template = workflowRepository.save(template);
      workflowTemplateId = template.getId();
      String stepTemplateId =
          stepRepository
              .save(
                  Step.builder()
                      .stepAction(StepActionClass.INJECT_EXECUTION)
                      .status(StepStatus.TEMPLATE)
                      .workflow(template)
                      .limitExecution(1)
                      .build())
              .getId();

      Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
      run.setSimulation(simulation);
      run.setWorkflowTemplate(template);
      workflowRunId = workflowRepository.save(run).getId();

      readyStepId = readyTheStep(stepTemplateId);
    } finally {
      // The chaining worker carries no tenant: the event alone must bring it.
      TenantContext.clearCurrentTenant();
    }
    recordEvaluationsOfTheRun();
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clearCurrentTenant();
    // Steps, workflow states and delay entries cascade with their workflow.
    for (String workflowId : new String[] {workflowRunId, workflowTemplateId}) {
      if (workflowId != null) {
        jdbc.update("DELETE FROM workflows WHERE workflow_id = ?", workflowId);
      }
    }
    // The simulation goes with the tenant.
    tenantHelper.deleteCommittedTenants(tenant == null ? null : tenant.getId());
  }

  @Test
  @DisplayName(
      "given the last step's END is committed but the commit then reports a failure and no retry"
          + " is left, the run is still evaluated, in a transaction of its own under the event's"
          + " tenant, and ends")
  void given_commitFailsAfterStepEndCommitted_and_noRetryLeft_should_evaluateAndEndTheRun()
      throws Exception {
    // Arrange: an afterCommit callback fails, as Executor's publication does when the broker is
    // unreachable: the END is durable, yet the event transaction throws.
    stepRunFailsWith(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            throw new IllegalStateException("simulated failure reported after the commit");
          }
        });
    StepEvent event = StepEvent.builder().stepId(readyStepId).tenantId(tenant.getId()).build();
    event.setRetryCount(chainingConfig.getMaxRetryCount());

    // Act
    stepEventService.handleReadyStepEvent(event);

    // Assert: the END the event transaction committed is there...
    assertThat(stepStatus()).isEqualTo(StepStatus.END.name());
    // ...and the run was evaluated anyway; no later event would have done it (#8315).
    assertThat(workflowStatus()).isEqualTo(WorkflowStatus.END.name());
    assertThat(simulationStatus()).isEqualTo(ExerciseStatus.FINISHED.name());
    assertThat(evaluations)
        .as("the run must be evaluated after the event transaction, under the event's tenant")
        .isNotEmpty()
        .allSatisfy(
            scope -> {
              assertThat(scope.tenantContext()).isEqualTo(tenant.getId());
              assertThat(scope.tenantGuc()).isEqualTo(tenant.getId());
              assertThat(scope.transactionId()).isNotEqualTo(eventTransactionId.get());
            });
    assertThat(TenantContext.hasCurrentTenant())
        .as("TenantContext must not leak past the event")
        .isFalse();
    verify(queueChainingService, never()).republishReadyEvent(any());
  }

  @Test
  @DisplayName(
      "given the commit of the step's END rolls back, evaluating the run does not end it: its"
          + " step is still READY and the event is retried")
  void given_commitRollsBackAfterStepEnded_should_keepTheRunAndRetryTheEvent() throws Exception {
    assumeTrue(chainingConfig.getMaxRetryCount() > 0, "needs a retry left");
    // Arrange: the commit itself fails, so the END is rolled back with the rest of the event.
    stepRunFailsWith(
        new TransactionSynchronization() {
          @Override
          public void beforeCommit(boolean readOnly) {
            throw new IllegalStateException("simulated commit failure");
          }
        });
    doNothing().when(queueChainingService).republishReadyEvent(any(StepEvent.class));
    StepEvent event = StepEvent.builder().stepId(readyStepId).tenantId(tenant.getId()).build();

    // Act
    stepEventService.handleReadyStepEvent(event);

    // Assert: nothing of the failed attempt was committed, and the run goes on.
    assertThat(stepStatus()).isEqualTo(StepStatus.READY.name());
    assertThat(workflowStatus()).isEqualTo(WorkflowStatus.RUN.name());
    assertThat(event.getRetryCount()).isEqualTo(1);
    verify(queueChainingService).republishReadyEvent(same(event));
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  /**
   * Readies the step template as the run's evaluation does at launch, minus the queue publication
   * (the test delivers the event itself). createReadySteps persists the READY step and commits the
   * template's execution hash for the run, so the evaluation under test cannot ready it again and
   * ends the run only once it sees no READY or RUN step left.
   */
  private String readyTheStep(String stepTemplateId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenant.getId()),
        () -> {
          Workflow run = workflowRepository.findById(workflowRunId).orElseThrow();
          Step stepTemplate = stepRepository.findById(stepTemplateId).orElseThrow();
          try {
            List<Step> readySteps = stepService.createReadySteps(stepTemplate, run, null, 0);
            assertThat(readySteps).hasSize(1);
            return readySteps.get(0).getId();
          } catch (ChainingException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  /**
   * The step run fails as when no agent can execute its inject (#8180: an empty result; the step is
   * ended and its run handed back for evaluation), and {@code synchronization} joins the event
   * transaction to break its commit.
   */
  private void stepRunFailsWith(TransactionSynchronization synchronization)
      throws ChainingException {
    doAnswer(
            invocation -> {
              eventTransactionId.set(currentTransactionId());
              TransactionSynchronizationManager.registerSynchronization(synchronization);
              return Optional.empty();
            })
        .when(injectExecutionStep)
        .run(argThat(step -> step != null && readyStepId.equals(step.getId())));
  }

  private void recordEvaluationsOfTheRun() throws ChainingException {
    doAnswer(
            invocation -> {
              Workflow evaluated = invocation.getArgument(0);
              if (evaluated != null && workflowRunId.equals(evaluated.getId())) {
                evaluations.add(
                    new EvaluationScope(
                        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null,
                        currentSetting("app.current_tenants"),
                        currentTransactionId()));
              }
              return invocation.callRealMethod();
            })
        .when(workflowService)
        .evaluateWorkflowProgress(any());
  }

  private String currentSetting(String name) {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting(:name, true), '')")
            .setFlushMode(FlushModeType.COMMIT)
            .setParameter("name", name)
            .getSingleResult();
  }

  private long currentTransactionId() {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT txid_current()")
                .setFlushMode(FlushModeType.COMMIT)
                .getSingleResult())
        .longValue();
  }

  private String workflowStatus() {
    return jdbc.queryForObject(
        "SELECT workflow_status FROM workflows WHERE workflow_id = ?", String.class, workflowRunId);
  }

  private String stepStatus() {
    return jdbc.queryForObject(
        "SELECT step_status FROM steps WHERE step_id = ?", String.class, readyStepId);
  }

  private String simulationStatus() {
    return jdbc.queryForObject(
        "SELECT exercise_status FROM exercises WHERE exercise_id = ?", String.class, simulationId);
  }
}
