package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link StepRepository#existsUnfinishedByStepTemplateIdAndWorkflowRunId}, against the database: a
 * step template has not ended in a workflow run while one of its run steps is READY or RUN, or one
 * of its targets waits in the delay queue. Both are read in one statement, so that a delayed target
 * turning into a READY step between two reads is never missed.
 */
@SpringBootTest
@Transactional
class StepUnfinishedQueryIntegrationTest extends IntegrationTest {

  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;

  private Step template;
  private Workflow run;

  private void runWith(StepStatus... statuses) {
    template = StepFixture.getDefaultStepTemplate();
    StepComposer.Composer templateComposer = stepComposer.forStep(template);
    WorkflowComposer.Composer runComposer =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
            .withStep(templateComposer)
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()));
    for (StepStatus status : statuses) {
      runComposer.withStep(
          stepComposer
              .forStep(StepFixture.getDefaultStepExecution(status))
              .withStepTemplate(templateComposer));
    }
    run = runComposer.persist().get();
  }

  private void delayedTarget() {
    stepDelayQueueRepository.save(
        StepDelayQueue.builder()
            .input("{}")
            .now(Instant.now())
            .goal(Instant.now().plusSeconds(60))
            .delay(60L)
            .stepTemplate(template)
            .workflowRun(run)
            .build());
  }

  private boolean unfinished() {
    return stepRepository.existsUnfinishedByStepTemplateIdAndWorkflowRunId(
        template.getId(), run.getId());
  }

  @ParameterizedTest
  @EnumSource(
      value = StepStatus.class,
      names = {"READY", "RUN"})
  @DisplayName("a run step READY or RUN beside an ended one leaves the template unfinished")
  void given_aRunStepNotEnded_should_beUnfinished(StepStatus status) {
    runWith(StepStatus.END, status);

    assertThat(unfinished()).isTrue();
  }

  @Test
  @DisplayName("every run step ended and no target delayed: the template has ended")
  void given_everyRunStepEnded_should_beFinished() {
    runWith(StepStatus.END, StepStatus.END);

    assertThat(unfinished()).isFalse();
  }

  @Test
  @DisplayName("a target waiting in the delay queue leaves the template unfinished")
  void given_aTargetDelayed_should_beUnfinished() {
    runWith(StepStatus.END);
    delayedTarget();

    assertThat(unfinished()).isTrue();
  }

  @Test
  @DisplayName("the steps and delayed targets of another run never count")
  void given_anotherRun_should_notCount() {
    runWith(StepStatus.RUN);
    delayedTarget();
    Workflow otherRun =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .persist()
            .get();

    assertThat(
            stepRepository.existsUnfinishedByStepTemplateIdAndWorkflowRunId(
                template.getId(), otherRun.getId()))
        .isFalse();
  }
}
