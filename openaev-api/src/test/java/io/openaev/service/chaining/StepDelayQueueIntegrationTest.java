package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepsDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class StepDelayQueueIntegrationTest {

  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ExerciseComposer simulationComposer;

  @AfterEach
  void tearDown() {
    stepDelayQueueRepository.deleteAll();
  }

  private StepsDelayQueue buildEntry(Instant goal) {
    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withStep(stepComposer.forStep(stepTemplate))
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();

    return StepsDelayQueue.builder()
        .goal(goal)
        .now(Instant.now())
        .delay(5000L)
        .input("test-input")
        .stepTemplate(stepTemplate)
        .workflowRun(workflow)
        .build();
  }

  @Test
  void findFirstByGoalLessThanEqualOrderByGoalAsc_shouldReturnEntryWhenGoalReached() {
    StepsDelayQueue entry = buildEntry(Instant.now().minusSeconds(60));
    stepDelayQueueRepository.save(entry);

    Optional<StepsDelayQueue> result =
        stepDelayQueueRepository.findFirstByGoalLessThanEqualOrderByGoalAsc(Instant.now());

    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals("test-input", result.get().getInput());
  }

  @Test
  void findFirstByGoalLessThanEqualOrderByGoalAsc_shouldReturnEmptyWhenGoalNotReached() {
    StepsDelayQueue entry = buildEntry(Instant.now().plusSeconds(3600));
    stepDelayQueueRepository.save(entry);

    Optional<StepsDelayQueue> result =
        stepDelayQueueRepository.findFirstByGoalLessThanEqualOrderByGoalAsc(Instant.now());

    Assertions.assertTrue(result.isEmpty());
  }

  @Test
  void findFirstByGoalLessThanEqualOrderByGoalAsc_shouldReturnOldestWhenMultipleGoalsReached() {
    StepsDelayQueue oldest = buildEntry(Instant.now().minusSeconds(120));
    oldest.setInput("oldest");
    StepsDelayQueue newer = buildEntry(Instant.now().minusSeconds(30));
    newer.setInput("newer");
    stepDelayQueueRepository.saveAll(List.of(oldest, newer));

    Optional<StepsDelayQueue> result =
        stepDelayQueueRepository.findFirstByGoalLessThanEqualOrderByGoalAsc(Instant.now());

    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals("oldest", result.get().getInput());
  }

  @Test
  void findFirstByGoalLessThanEqualOrderByGoalAsc_shouldReturnEmptyWhenQueueIsEmpty() {
    Optional<StepsDelayQueue> result =
        stepDelayQueueRepository.findFirstByGoalLessThanEqualOrderByGoalAsc(Instant.now());

    Assertions.assertTrue(result.isEmpty());
  }
}
