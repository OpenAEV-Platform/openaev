package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("Workflow pause/resume integration tests")
class WorkflowPauseServiceIntegrationTest extends IntegrationTest {

  @Autowired private WorkflowPauseService workflowPauseService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private StepDelayQueueService stepDelayQueueService;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private EntityManager entityManager;

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
      stepDelayQueueRepository.save(buildDelay(run, stepTemplate, "existing", initialGoal));

      // Act - pause
      workflowPauseService.pauseSimulationWorkflowRuns(run.getSimulation().getId());
      entityManager.flush();
      entityManager.clear();
      Workflow pausedRun = workflowRepository.findById(run.getId()).orElseThrow();

      // Assert pause
      assertEquals(WorkflowStatus.STOP, pausedRun.getStatus());
      assertNotNull(pausedRun.getPauseAt());
      assertTrue(
          stepDelayQueueRepository.findAllByWorkflowRun(pausedRun).stream()
              .allMatch(d -> d.getGoal() == null));

      // Act - create a delay during pause
      stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
          stepTemplate,
          Instant.now(),
          "created-during-pause",
          30_000L,
          pausedRun,
          Instant.now().plusSeconds(30));

      // Assert created during pause
      StepDelayQueue createdDuringPause =
          stepDelayQueueRepository.findAllByWorkflowRun(pausedRun).stream()
              .filter(d -> "created-during-pause".equals(d.getInput()))
              .findFirst()
              .orElseThrow();
      assertNull(createdDuringPause.getGoal());

      // Act - resume
      pausedRun.setPauseAt(Instant.now().minusSeconds(90));
      workflowRepository.save(pausedRun);
      workflowPauseService.resumeSimulationWorkflowRuns(run.getSimulation().getId());
      entityManager.flush();
      entityManager.clear();

      // Assert resume
      Workflow resumedRun = workflowRepository.findById(run.getId()).orElseThrow();
      assertEquals(WorkflowStatus.RUN, resumedRun.getStatus());
      assertNull(resumedRun.getPauseAt());
      assertTrue(resumedRun.getPauseSecond() >= 90);
      assertTrue(
          stepDelayQueueRepository.findAllByWorkflowRun(resumedRun).stream()
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
      stepDelayQueueRepository.save(
          buildDelay(run, stepTemplate, "with-timeout-rate", Instant.now().plusSeconds(120)));

      // Act
      workflowPauseService.pauseSimulationWorkflowRuns(run.getSimulation().getId());
      Workflow pausedRun = workflowRepository.findById(run.getId()).orElseThrow();
      pausedRun.setPauseAt(Instant.now().minusSeconds(60));
      workflowRepository.save(pausedRun);
      workflowPauseService.resumeSimulationWorkflowRuns(run.getSimulation().getId());
      entityManager.flush();
      entityManager.clear();

      // Assert
      Workflow resumedRun = workflowRepository.findById(run.getId()).orElseThrow();
      assertTrue(resumedRun.isTimeoutEnabled());
      assertEquals(600L, resumedRun.getTimeoutSeconds());
      assertTrue(resumedRun.isRateLimitEnabled());
      assertEquals(3, resumedRun.getMaxAttempts());
      assertEquals(120L, resumedRun.getMaxTemporalRateSeconds());
      assertTrue(resumedRun.getPauseSecond() >= 60);
      assertTrue(
          stepDelayQueueRepository.findAllByWorkflowRun(resumedRun).stream()
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
      workflowPauseService.pauseSimulationWorkflowRuns(simulationId);
      Workflow firstPaused = workflowRepository.findById(run.getId()).orElseThrow();
      firstPaused.setPauseAt(Instant.now().minusSeconds(40));
      workflowRepository.save(firstPaused);
      workflowPauseService.resumeSimulationWorkflowRuns(simulationId);
      entityManager.flush();
      entityManager.clear();
      long afterFirstResume =
          workflowRepository.findById(run.getId()).orElseThrow().getPauseSecond();

      // Act - cycle 2
      workflowPauseService.pauseSimulationWorkflowRuns(simulationId);
      Workflow secondPaused = workflowRepository.findById(run.getId()).orElseThrow();
      secondPaused.setPauseAt(Instant.now().minusSeconds(55));
      workflowRepository.save(secondPaused);
      workflowPauseService.resumeSimulationWorkflowRuns(simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert
      Workflow resumed = workflowRepository.findById(run.getId()).orElseThrow();
      assertEquals(WorkflowStatus.RUN, resumed.getStatus());
      assertNull(resumed.getPauseAt());
      assertTrue(afterFirstResume >= 40);
      assertTrue(resumed.getPauseSecond() >= afterFirstResume + 55);
    }
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
