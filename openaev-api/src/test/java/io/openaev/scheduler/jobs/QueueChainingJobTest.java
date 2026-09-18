package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Tests for {@link QueueChainingJob}, now opening its top-level transaction through {@link
 * io.openaev.context.TenantScopedTransaction} (with the {@code allTenants()} intention, since
 * {@code popNextPerWorkflowRun()} claims delay-queue entries across every tenant) instead of a raw
 * {@code TransactionTemplate}.
 *
 * <p>Deliberately NOT {@code @Transactional}: the job opens its own top-level transaction, whose
 * {@code execute} refuses to run inside an active one (same reasoning as {@link
 * AtomicTestingExecutionJobTest}). Everything is committed or rolled back by the job itself, so
 * each test sweeps its own rows in {@link #cleanup()}.
 */
@SpringBootTest
class QueueChainingJobTest extends IntegrationTest {

  @Autowired private QueueChainingJob job;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @MockitoSpyBean private StepService stepService;

  @AfterEach
  void cleanup() {
    stepDelayQueueRepository.deleteAll(stepDelayQueueRepository.findAll());
    // Workflow FKs (scenario_id) must be cleared before the scenario itself is deleted.
    workflowRepository.deleteAll(
        workflowRepository.findAllById(
            workflowComposer.generatedItems.stream().map(Workflow::getId).toList()));
    stepRepository.deleteAll(
        stepRepository.findAllById(stepComposer.generatedItems.stream().map(Step::getId).toList()));
    scenarioRepository.deleteAll(
        scenarioRepository.findAllById(
            scenarioComposer.generatedItems.stream().map(Scenario::getId).toList()));
    workflowComposer.reset();
    stepComposer.reset();
    scenarioComposer.reset();
  }

  private StepDelayQueue persistDueDelayQueueEntry(Workflow workflowRun) {
    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    // A workflow must reference a simulation OR a scenario (chk_workflow_simulation_or_scenario).
    // A scenario-backed run is the interesting case: it has no simulation, and the job resolves its
    // tenant from the scenario instead of dereferencing getSimulation().
    Workflow persistedWorkflowRun =
        workflowComposer
            .forWorkflow(workflowRun)
            .withStep(stepComposer.forStep(stepTemplate))
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .persist()
            .get();

    StepDelayQueue entry =
        StepDelayQueue.builder()
            .input("{}")
            .now(Instant.now())
            .goal(Instant.now().minusSeconds(60))
            .delay(0L)
            .stepTemplate(stepTemplate)
            .workflowRun(persistedWorkflowRun)
            .build();
    return stepDelayQueueRepository.save(entry);
  }

  @Test
  @DisplayName("an uncaught failure while processing a popped entry rolls back the whole pop batch")
  void given_processing_failure_should_roll_back_the_pop() throws Exception {
    // Arrange: the failure is injected rather than taken from a null simulation. Resolving the
    // tenant no longer throws on a scenario-backed run - the job reads the scenario's tenant - so
    // the rollback has to be provoked by a genuine processing failure instead.
    Workflow scenarioBackedRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    StepDelayQueue dueEntry = persistDueDelayQueueEntry(scenarioBackedRun);
    doThrow(new IllegalStateException("processing blew up"))
        .when(stepService)
        .createReadySteps(any(), any(), any(), anyInt());

    // Act & Assert: the exception escapes the job's top-level transaction uncaught ...
    assertThatThrownBy(() -> job.execute(null)).isInstanceOf(IllegalStateException.class);

    // ... which rolls back the DELETE...RETURNING pop: the entry is not lost, it stays queued for
    // the next tick, exactly as before the job was moved onto TenantScopedTransaction.
    assertThat(stepDelayQueueRepository.findById(dueEntry.getId())).isPresent();
  }

  @Test
  @DisplayName("an empty delay queue is a no-op under the allTenants() top-level transaction")
  void given_no_due_entry_should_not_fail() {
    // Arrange: nothing due in the queue.

    // Act & Assert: opening the top-level transaction with the allTenants() intention and finding
    // nothing to pop must not throw.
    assertThatCode(() -> job.execute(null)).doesNotThrowAnyException();
  }
}
