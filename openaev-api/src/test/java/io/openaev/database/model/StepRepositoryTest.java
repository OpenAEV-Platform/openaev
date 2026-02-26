package io.openaev.database.model;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(PER_CLASS)
@Transactional
class StepRepositoryTest extends IntegrationTest {

  @Autowired private StepRepository stepRepository;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private EndpointComposer endpointComposer;

  @Test
  void whenFindAllByStepTemplateIdIsNullAndWorkflowId_thenReturnsStepsTemplateForWorkflow() {
    // GIVEN
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .withStep(stepComposer.forStep(StepFixture.getDefaultStepExecution(StepStatus.RUN)))
            .persist()
            .get();

    // WHEN
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());

    // THEN
    Assertions.assertFalse(steps.isEmpty(), "Step list should not be empty");
    Assertions.assertNull(steps.getFirst().getStepTemplate(), "Step template should be null");
    Assertions.assertEquals(workflow.getId(), steps.getFirst().getWorkflow().getId());
  }

  @Test
  void whenFindStepIdByInjectId_thenReturnsCorrectStepId() {
    // GIVEN: a step with JSON data containing an inject_id
    String injectId = "inject-123";
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + injectId + "\"}")
            .build();

    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();

    // WHEN
    var optionalStepId = stepRepository.findStepIdByInjectId(injectId);

    // THEN
    Assertions.assertTrue(optionalStepId.isPresent(), "Step ID should be found");
    Assertions.assertEquals(step.getId(), optionalStepId.get());
  }

  @Test
  void whenFindStepIdsByExpectationIds_thenReturnsCorrectStepIds() {
    // GIVEN: an inject with an expectation, and a step referencing that inject
    Inject inject = InjectFixture.getDefaultInject();
    InjectExpectation expectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();

    injectComposer
        .forInject(inject)
        .withExercise(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation)
                .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
        .persist();

    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + inject.getId() + "\"}")
            .build();

    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();

    // WHEN
    Set<String> stepIds = stepRepository.findStepIdsByExpectationIds(Set.of(expectation.getId()));

    // THEN
    Assertions.assertFalse(stepIds.isEmpty(), "Step IDs should be found");
    Assertions.assertTrue(stepIds.contains(step.getId()), "Should contain the expected step ID");
  }

  @Test
  void
      whenFindStepIdsByExpectationIds_withMultipleExpectationsAndSteps_thenReturnsCorrectStepIds() {
    // GIVEN: 4 expectations across 2 injects, and 3 steps
    // - inject1 has 3 expectations, referenced by step1
    // - inject2 has 1 expectation, referenced by step2
    // - step3 references an inject with no expectations

    // Create inject1 with 3 expectations
    Inject inject1 = InjectFixture.getDefaultInject();
    InjectExpectation expectation1 =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    InjectExpectation expectation2 =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    InjectExpectation expectation3 =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();

    injectComposer
        .forInject(inject1)
        .withExercise(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation1)
                .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation2)
                .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation3)
                .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
        .persist();

    // Create inject2 with 1 expectation
    Inject inject2 = InjectFixture.getDefaultInject();
    InjectExpectation expectation4 =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();

    injectComposer
        .forInject(inject2)
        .withExercise(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withExpectation(
            injectExpectationComposer
                .forExpectation(expectation4)
                .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint())))
        .persist();

    // Create inject3 with no expectations
    Inject inject3 = InjectFixture.getDefaultInject();
    injectComposer
        .forInject(inject3)
        .withExercise(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .persist();

    // Create steps
    Step step1 =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + inject1.getId() + "\"}")
            .build();

    Step step2 =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + inject2.getId() + "\"}")
            .build();

    Step step3 =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + inject3.getId() + "\"}")
            .build();

    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step1))
        .withStep(stepComposer.forStep(step2))
        .withStep(stepComposer.forStep(step3))
        .persist();

    // WHEN: querying with all 4 expectation IDs
    Set<String> stepIds =
        stepRepository.findStepIdsByExpectationIds(
            Set.of(
                expectation1.getId(),
                expectation2.getId(),
                expectation3.getId(),
                expectation4.getId()));

    // THEN: should return step1 and step2, but not step3
    Assertions.assertEquals(2, stepIds.size(), "Should return exactly 2 step IDs");
    Assertions.assertTrue(stepIds.contains(step1.getId()), "Should contain step1");
    Assertions.assertTrue(stepIds.contains(step2.getId()), "Should contain step2");
    Assertions.assertFalse(stepIds.contains(step3.getId()), "Should not contain step3");

    // WHEN: querying with only expectations from inject1
    Set<String> stepIdsForInject1 =
        stepRepository.findStepIdsByExpectationIds(
            Set.of(expectation1.getId(), expectation2.getId(), expectation3.getId()));

    // THEN: should return only step1
    Assertions.assertEquals(1, stepIdsForInject1.size(), "Should return exactly 1 step ID");
    Assertions.assertTrue(stepIdsForInject1.contains(step1.getId()), "Should contain step1");

    // WHEN: querying with only expectation from inject2
    Set<String> stepIdsForInject2 =
        stepRepository.findStepIdsByExpectationIds(Set.of(expectation4.getId()));

    // THEN: should return only step2
    Assertions.assertEquals(1, stepIdsForInject2.size(), "Should return exactly 1 step ID");
    Assertions.assertTrue(stepIdsForInject2.contains(step2.getId()), "Should contain step2");
  }
}
