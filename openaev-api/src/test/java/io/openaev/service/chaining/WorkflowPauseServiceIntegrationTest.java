package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately NOT {@code @Transactional}: resume commits RUN in its own transaction ({@code
 * REQUIRES_NEW}, see {@link WorkflowResumeService#resumeWorkflowRunIsolated}), which cannot see
 * rows a test transaction never commits. Every call commits on its own, so each test sweeps its own
 * rows in {@link #cleanup()}. Each call runs in the simulation's tenant scope, as the simulation
 * status request that pauses and resumes the run does.
 */
@SpringBootTest
@DisplayName("Workflow pause/resume integration tests")
class WorkflowPauseServiceIntegrationTest extends IntegrationTest {

  @Autowired private WorkflowPauseService workflowPauseService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private StepDelayQueueService stepDelayQueueService;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private StepRepository stepRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TenantScopedTransaction tenantTx;

  @AfterEach
  void cleanup() {
    tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          stepDelayQueueRepository.deleteAll(stepDelayQueueRepository.findAll());
          workflowRepository.deleteAll(
              workflowRepository.findAllById(
                  workflowComposer.generatedItems.stream().map(Workflow::getId).toList()));
          stepRepository.deleteAll(
              stepRepository.findAllById(
                  stepComposer.generatedItems.stream().map(Step::getId).toList()));
        });
    exerciseRepository.deleteAll(
        exerciseRepository.findAllById(
            exerciseComposer.generatedItems.stream().map(Exercise::getId).toList()));
    workflowComposer.reset();
    stepComposer.reset();
    exerciseComposer.reset();
  }

  @Nested
  @DisplayName("US.1 Pause Scenario Chaining without timeout and rate limit")
  class Us1 {

    @Test
    void givenStoppedThenResumedWorkflow_shouldNullifyAndRecalculateDelayGoals()
        throws ChainingException {
      // Arrange
      Workflow run = createRunWorkflow(false, false);
      Step stepTemplate = run.getSteps().getFirst();
      Instant initialGoal = Instant.now().plusSeconds(600);
      inTenantOf(
          run,
          () ->
              stepDelayQueueRepository.save(
                  buildDelay(run, stepTemplate, "existing", initialGoal)));

      // Act - pause
      inTenantOf(
          run, () -> workflowPauseService.pauseSimulationWorkflowRuns(run.getSimulation().getId()));
      Workflow pausedRun =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());

      // Assert pause
      assertEquals(WorkflowStatus.STOP, pausedRun.getStatus());
      assertNotNull(pausedRun.getPauseAt());
      assertTrue(
          inTenantOf(run, () -> stepDelayQueueRepository.findAllByWorkflowRun(pausedRun)).stream()
              .allMatch(d -> d.getGoal() == null));

      // Act - create a delay during pause
      inTenantOf(
          run,
          () ->
              stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
                  stepTemplate,
                  Instant.now(),
                  "created-during-pause",
                  30_000L,
                  pausedRun,
                  Instant.now().plusSeconds(30)));

      // Assert created during pause
      StepDelayQueue createdDuringPause =
          inTenantOf(run, () -> stepDelayQueueRepository.findAllByWorkflowRun(pausedRun)).stream()
              .filter(d -> "created-during-pause".equals(d.getInput()))
              .findFirst()
              .orElseThrow();
      assertNull(createdDuringPause.getGoal());

      // Act - resume
      pausedRun.setPauseAt(Instant.now().minusSeconds(90));
      inTenantOf(run, () -> workflowRepository.save(pausedRun));
      inTenantOf(
          run,
          () -> workflowPauseService.resumeSimulationWorkflowRuns(run.getSimulation().getId()));

      // Assert resume
      Workflow resumedRun =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      assertEquals(WorkflowStatus.RUN, resumedRun.getStatus());
      assertNull(resumedRun.getPauseAt());
      assertTrue(resumedRun.getPauseSecond() >= 90);
      assertTrue(
          inTenantOf(run, () -> stepDelayQueueRepository.findAllByWorkflowRun(resumedRun)).stream()
              .allMatch(d -> d.getGoal() != null));
    }
  }

  @Nested
  @DisplayName("US.2 Pause Scenario Chaining with timeout and rate limit")
  class Us2 {

    @Test
    void givenTimeoutAndRateLimitConfigured_whenPauseResume_shouldKeepConfigAndAccumulatePause()
        throws ChainingException {
      // Arrange
      Workflow run = createRunWorkflow(true, true);
      Step stepTemplate = run.getSteps().getFirst();
      inTenantOf(
          run,
          () ->
              stepDelayQueueRepository.save(
                  buildDelay(
                      run, stepTemplate, "with-timeout-rate", Instant.now().plusSeconds(120))));

      // Act
      inTenantOf(
          run, () -> workflowPauseService.pauseSimulationWorkflowRuns(run.getSimulation().getId()));
      Workflow pausedRun =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      pausedRun.setPauseAt(Instant.now().minusSeconds(60));
      inTenantOf(run, () -> workflowRepository.save(pausedRun));
      inTenantOf(
          run,
          () -> workflowPauseService.resumeSimulationWorkflowRuns(run.getSimulation().getId()));

      // Assert
      Workflow resumedRun =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      assertTrue(resumedRun.isTimeoutEnabled());
      assertEquals(600L, resumedRun.getTimeoutSeconds());
      assertTrue(resumedRun.isRateLimitEnabled());
      assertEquals(3, resumedRun.getMaxAttempts());
      assertEquals(120L, resumedRun.getMaxTemporalRateSeconds());
      assertTrue(resumedRun.getPauseSecond() >= 60);
      assertTrue(
          inTenantOf(run, () -> stepDelayQueueRepository.findAllByWorkflowRun(resumedRun)).stream()
              .allMatch(d -> d.getGoal() != null));
    }
  }

  @Nested
  @DisplayName("US.3 Multi pause a Scenario Chaining without timeout and rate limit")
  class Us3 {

    @Test
    void givenMultiplePauseCycles_shouldIncrementPauseSecondAcrossResumes()
        throws ChainingException {
      // Arrange
      Workflow run = createRunWorkflow(false, false);
      String simulationId = run.getSimulation().getId();

      // Act - cycle 1
      inTenantOf(run, () -> workflowPauseService.pauseSimulationWorkflowRuns(simulationId));
      Workflow firstPaused =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      firstPaused.setPauseAt(Instant.now().minusSeconds(40));
      inTenantOf(run, () -> workflowRepository.save(firstPaused));
      inTenantOf(run, () -> workflowPauseService.resumeSimulationWorkflowRuns(simulationId));
      long afterFirstResume =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow())
              .getPauseSecond();

      // Act - cycle 2
      inTenantOf(run, () -> workflowPauseService.pauseSimulationWorkflowRuns(simulationId));
      Workflow secondPaused =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      secondPaused.setPauseAt(Instant.now().minusSeconds(55));
      inTenantOf(run, () -> workflowRepository.save(secondPaused));
      inTenantOf(run, () -> workflowPauseService.resumeSimulationWorkflowRuns(simulationId));

      // Assert
      Workflow resumed =
          inTenantOf(run, () -> workflowRepository.findById(run.getId()).orElseThrow());
      assertEquals(WorkflowStatus.RUN, resumed.getStatus());
      assertNull(resumed.getPauseAt());
      assertTrue(afterFirstResume >= 40);
      assertTrue(resumed.getPauseSecond() >= afterFirstResume + 55);
    }
  }

  /**
   * Runs {@code work} in its own transaction, in the tenant scope of the run's simulation, as the
   * simulation status request that pauses and resumes the run does.
   */
  private <T> T inTenantOf(Workflow run, Callable<T> work) {
    return tenantTx.execute(
        TxCtx.forTenant(run.getSimulation().getTenant().getId()),
        () -> {
          try {
            return work.call();
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private void inTenantOf(Workflow run, Runnable work) {
    tenantTx.execute(TxCtx.forTenant(run.getSimulation().getTenant().getId()), work);
  }

  private Workflow createRunWorkflow(boolean timeoutEnabled, boolean rateLimitEnabled) {
    Exercise simulation = ExerciseFixture.createRunningAttackExercise();
    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    run.setTimeoutEnabled(timeoutEnabled);
    run.setTimeoutSeconds(timeoutEnabled ? 600L : null);
    run.setRateLimitEnabled(rateLimitEnabled);
    run.setMaxAttempts(rateLimitEnabled ? 3 : null);
    run.setMaxTemporalRateSeconds(rateLimitEnabled ? 120L : null);
    run.setSafeModeEnabled(true);

    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    return workflowComposer
        .forWorkflow(run)
        .withSimulation(exerciseComposer.forExercise(simulation))
        .withStep(stepComposer.forStep(stepTemplate))
        .persist()
        .get();
  }

  private static StepDelayQueue buildDelay(
      Workflow workflowRun, Step stepTemplate, String input, Instant goal) {
    return StepDelayQueue.builder()
        .workflowRun(workflowRun)
        .stepTemplate(stepTemplate)
        .input(input)
        .delay(30_000L)
        .now(Instant.now())
        .goal(goal)
        .build();
  }
}
