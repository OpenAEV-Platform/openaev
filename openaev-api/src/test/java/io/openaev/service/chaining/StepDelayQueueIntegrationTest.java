package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class StepDelayQueueIntegrationTest {

  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ExerciseComposer simulationComposer;

  private StepDelayQueue buildEntry(Instant goal) {
    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withStep(stepComposer.forStep(stepTemplate))
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();

    return StepDelayQueue.builder()
        .goal(goal)
        .now(Instant.now())
        .delay(5000L)
        .input("test-input")
        .stepTemplate(stepTemplate)
        .workflowRun(workflow)
        .build();
  }

  @Test
  void popNextPerWorkflowRun_shouldReturnOnePerWorkflowRunWhenMultipleEligible() {
    // Two different workflow runs, both eligible
    StepDelayQueue run1Entry = buildEntry(Instant.now().minusSeconds(60));
    StepDelayQueue run2Entry = buildEntry(Instant.now().minusSeconds(30));
    stepDelayQueueRepository.saveAll(List.of(run1Entry, run2Entry));

    List<StepDelayQueue> results = stepDelayQueueRepository.popNextPerWorkflowRun();
    Assertions.assertEquals(2, results.size());
    Assertions.assertTrue(
        results.stream().map(s -> s.getWorkflowRun().getId()).distinct().count() == 2);
  }

  @Test
  void popNextPerWorkflowRun_shouldReturnOldestGoalPerWorkflowRun() {
    // Same workflow run, two eligible entries — only oldest should be returned
    // Workflow sharedRun = // build a single workflow run manually
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);

    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    workflowComposer
        .forWorkflow(workflowRun)
        .withStep(stepComposer.forStep(stepTemplate))
        .withWorkflowTemplate(
            workflowComposer
                .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
                .withSimulation(
                    exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise())))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .persist()
        .get();

    StepDelayQueue oldest =
        StepDelayQueue.builder()
            .goal(Instant.now().minusSeconds(120))
            .now(Instant.now())
            .delay(5000L)
            .input("oldest")
            .stepTemplate(stepTemplate)
            .workflowRun(workflowRun)
            .build();

    StepDelayQueue newer =
        StepDelayQueue.builder()
            .goal(Instant.now().minusSeconds(30))
            .now(Instant.now())
            .delay(5000L)
            .input("newer")
            .stepTemplate(stepTemplate)
            .workflowRun(workflowRun)
            .build();

    stepDelayQueueRepository.saveAll(List.of(oldest, newer));

    List<StepDelayQueue> results = stepDelayQueueRepository.popNextPerWorkflowRun();

    Assertions.assertEquals(1, results.size());
    Assertions.assertEquals("oldest", results.get(0).getInput());
  }

  @Test
  void popNextPerWorkflowRun_shouldDeleteReturnedEntries() {
    StepDelayQueue entry = buildEntry(Instant.now().minusSeconds(60));
    stepDelayQueueRepository.save(entry);

    List<StepDelayQueue> results = stepDelayQueueRepository.popNextPerWorkflowRun();

    Assertions.assertEquals(1, results.size());
    Assertions.assertTrue(stepDelayQueueRepository.findAll().isEmpty());
  }

  @Test
  void popNextPerWorkflowRun_shouldReturnEmptyWhenNoEligibleEntries() {
    StepDelayQueue future = buildEntry(Instant.now().plusSeconds(3600));
    stepDelayQueueRepository.save(future);

    List<StepDelayQueue> results = stepDelayQueueRepository.popNextPerWorkflowRun();

    Assertions.assertTrue(results.isEmpty());
  }
}
