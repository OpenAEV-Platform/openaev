package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SimulationRateLimitService#isExecutionAllowed} against the database, for the READY inject
 * steps it counts. A step saved READY earlier in the same transaction (an earlier template of the
 * same evaluation cycle) is counted without an explicit flush, which is why the evaluation loop
 * passes no pending count for it; the READY steps of every run of the simulation count.
 */
@SpringBootTest
@Transactional
class SimulationRateLimitReadyStepsIntegrationTest extends IntegrationTest {

  private static final int MAX_ATTEMPTS = 2;

  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepRepository stepRepository;
  @Autowired private SimulationRateLimitService simulationRateLimitService;

  private Workflow rateLimitedRun(Exercise simulation) {
    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    run.setSimulation(simulation);
    run.setRateLimitEnabled(true);
    run.setMaxAttempts(MAX_ATTEMPTS);
    run.setMaxTemporalRateSeconds(60L);
    return workflowComposer.forWorkflow(run).persist().get();
  }

  private void injectStep(Workflow run, StepStatus status) {
    Step step = StepFixture.getDefaultStepExecution(status);
    step.setWorkflow(run);
    stepRepository.save(step);
  }

  private void readyInjectStep(Workflow run) {
    injectStep(run, StepStatus.READY);
  }

  private Exercise simulation() {
    return exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
  }

  @Test
  @DisplayName("a step saved READY earlier in the transaction is counted without a flush")
  void given_readyStepSavedInTransaction_should_countItOnce() {
    Workflow run = rateLimitedRun(simulation());

    // Cycle with two templates and a max of 2: the first template readies one step.
    readyInjectStep(run);
    // The second template starts its own call with no pending batch: one slot is left.
    assertThat(simulationRateLimitService.isExecutionAllowed(run, 0)).isTrue();

    // The second template readies its step: the limit is reached.
    readyInjectStep(run);
    assertThat(simulationRateLimitService.isExecutionAllowed(run, 0)).isFalse();
  }

  @Test
  @DisplayName("the READY inject steps of another run of the same simulation count")
  void given_twoRunsOfOneSimulation_should_countBoth() {
    Exercise simulation = simulation();
    Workflow run = rateLimitedRun(simulation);
    Workflow otherRun = rateLimitedRun(simulation);

    readyInjectStep(run);
    readyInjectStep(otherRun);

    assertThat(simulationRateLimitService.isExecutionAllowed(run, 0)).isFalse();
  }

  @Test
  @DisplayName("the READY inject steps of another simulation never count")
  void given_anotherSimulation_should_notCount() {
    Workflow run = rateLimitedRun(simulation());
    Workflow otherSimulationRun = rateLimitedRun(simulation());

    readyInjectStep(otherSimulationRun);
    readyInjectStep(otherSimulationRun);

    assertThat(simulationRateLimitService.isExecutionAllowed(run, 0)).isTrue();
  }

  @Test
  @DisplayName("a step that is not READY does not count")
  void given_stepNotReady_should_notCount() {
    Workflow run = rateLimitedRun(simulation());
    injectStep(run, StepStatus.RUN);
    injectStep(run, StepStatus.END);

    assertThat(simulationRateLimitService.isExecutionAllowed(run, 0)).isTrue();
  }
}
