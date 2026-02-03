package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Condition;
import io.openaev.database.model.STEP_ACTION_CLASS;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class StepServiceTest extends IntegrationTest {

  @Mock private ApplicationContext applicationContext;

  @Mock private StepRepository stepRepository;
  @Mock private InjectExecutionStep injectExecutionStep;

  @Mock private WorkflowService workflowService;
  @Mock private ConditionService conditionService;
  @Mock private QueueChainingService queueChainingService;

  @Spy @InjectMocks StepService stepService;

  private final String workflowId = UUID.randomUUID().toString();

  /* ============================================================
   * createStepsTemplate — ActionStep resolution
   * ============================================================ */
  @Nested
  class ActionStepResolution {

    @Test
    void shouldThrowWhenActionStepIsNull() {
      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, List.of());

      when(workflowService.getWorkflowById(workflowId)).thenReturn(mock(Workflow.class));
      when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(null);

      assertThrows(
          BadRequestException.class,
          () -> stepService.createStepsTemplate(workflowId, List.of(stepInput)));

      verify(conditionService, never()).saveCondition(any());
    }
  }

  /* ============================================================
   * stepCondition — no conditions
   * ============================================================ */
  @Nested
  class NoConditions {

    @Test
    void shouldSkipConditionCreationWhenEmpty() {
      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, Collections.emptyList());

      setupHappyPath(stepInput);

      stepService.createStepsTemplate(workflowId, List.of(stepInput));

      verify(conditionService, never()).saveCondition(any());
    }
  }

  /* ============================================================
   * stepCondition — parameterized condition trees
   * ============================================================ */
  @Nested
  class ConditionTrees {

    @ParameterizedTest(name = "{0}")
    @MethodSource("conditionTreeProvider")
    void shouldBuildConditionTreeCorrectly(
        String description,
        List<ConditionCreateInput> inputs,
        int expectedSaveCalls,
        Map<String, String> expectedParentMap,
        boolean withStepFrom) {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, inputs);

      Step step = setupHappyPath(stepInput);

      if (withStepFrom) {
        Step stepFrom = mock(Step.class);
        when(stepRepository.findById("FROM")).thenReturn(Optional.of(stepFrom));
      }

      stepService.createStepsTemplate(workflowId, List.of(stepInput));

      ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);

      verify(conditionService, times(expectedSaveCalls)).saveCondition(captor.capture());

      List<Condition> saved = captor.getAllValues();

      Map<String, Condition> byKey =
          saved.stream().collect(Collectors.toMap(Condition::getKey, c -> c));

      expectedParentMap.forEach(
          (childKey, parentKey) -> {
            Condition child = byKey.get(childKey);
            if (parentKey == null) {
              assertNull(child.getConditionParent());
            } else {
              assertEquals(byKey.get(parentKey), child.getConditionParent());
            }
          });

      if (withStepFrom) {
        assertNotNull(byKey.get("ROOT").getStepFrom());
      }
    }

    static Stream<Arguments> conditionTreeProvider() {

      return Stream.of(
          Arguments.of(
              "Single root condition",
              List.of(condition("ROOT", null, null)),
              1,
              Map.of("ROOT", null),
              false),
          Arguments.of(
              "Root with one child",
              List.of(condition("ROOT", null, null), condition("CHILD", "ROOT", null)),
              2,
              Map.of("ROOT", null, "CHILD", "ROOT"),
              false),
          Arguments.of(
              "Root with two-level tree",
              List.of(
                  condition("ROOT", null, null),
                  condition("A", "ROOT", null),
                  condition("B", "A", null)),
              3,
              Map.of(
                  "ROOT", null,
                  "A", "ROOT",
                  "B", "A"),
              false),
          Arguments.of(
              "Root with stepFrom",
              List.of(condition("ROOT", null, "FROM")),
              1,
              Map.of("ROOT", null),
              true));
    }
  }

  /* ============================================================
   * stepCondition — invalid trees
   * ============================================================ */
  @Nested
  class InvalidConditionTrees {

    @Test
    void shouldThrowWhenMultipleRootConditions() {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(
              STEP_ACTION_CLASS.INJECT_EXECUTION,
              List.of(condition("R1", null, null), condition("R2", null, null)));

      setupHappyPath(stepInput);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepsTemplate(workflowId, List.of(stepInput)));
    }

    @Test
    void shouldThrowWhenNoRootConditionExists() {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, List.of(condition("A", "X", null)));

      setupHappyPath(stepInput);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepsTemplate(workflowId, List.of(stepInput)));
    }
  }

  /* ============================================================
   * Helpers
   * ============================================================ */

  private Step setupHappyPath(StepsCreateInput.StepCreateInput stepInput) {

    Workflow workflow = mock(Workflow.class);
    Step step = mock(Step.class);
    ActionStep actionStep = mock(ActionStep.class);

    when(workflowService.getWorkflowById(workflowId)).thenReturn(workflow);

    when(stepService.factoryAction(any())).thenReturn(actionStep);

    when(actionStep.create(any(), eq(workflow))).thenReturn(step);

    when(stepService.saveStep(step)).thenReturn(step);

    when(conditionService.saveCondition(any())).thenAnswer(i -> i.getArgument(0));

    return step;
  }

  private StepsCreateInput.StepCreateInput mockStep(
      STEP_ACTION_CLASS actionClass, List<ConditionCreateInput> conditions) {

    StepsCreateInput.StepCreateInput step = mock(StepsCreateInput.StepCreateInput.class);

    when(step.getStepAction()).thenReturn(actionClass);
    when(step.getConditions()).thenReturn(conditions);

    return step;
  }

  private static ConditionCreateInput condition(String key, String parentTempId, String stepFrom) {

    ConditionCreateInput c = mock(ConditionCreateInput.class);

    when(c.getKey()).thenReturn(key);
    when(c.getTemporaryId()).thenReturn(key);
    when(c.getTemporaryIdConditionParent()).thenReturn(parentTempId);
    when(c.getStepFrom()).thenReturn(stepFrom);

    return c;
  }
}
