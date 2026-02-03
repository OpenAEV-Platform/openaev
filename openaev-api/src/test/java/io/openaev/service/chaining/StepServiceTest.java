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
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
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

  @Captor private ArgumentCaptor<String> simulationIdCaptor;
  @Captor private ArgumentCaptor<String> workflowTemplateIdCaptor;
  @Captor private ArgumentCaptor<Step> stepCaptor;
  @Captor private ArgumentCaptor<Workflow> workflowCaptor;
  @Captor private ArgumentCaptor<List<Condition>> conditionsCaptor;
  @Captor private ArgumentCaptor<String> stepIdCaptor;

  /* ============================================================
   * createStepsTemplate — ActionStep resolution
   * ============================================================ */
  @Nested
  class ActionStepResolution {

    @Test
    void shouldThrowWhenActionStepIsNull() {
      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.UNSUPPORTED, List.of());

      when(workflowService.getWorkflowById(workflowId)).thenReturn(mock(Workflow.class));
      when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(null);

      assertThrows(
          BadRequestException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));

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

      setupCreateStepTemplates(stepInput);

      stepService.createStepTemplates(workflowId, List.of(stepInput));

      verify(conditionService, never()).saveCondition(any());
    }
  }

  /* ============================================================
   * stepCondition — parameterized condition trees
   * ============================================================ */
  @Nested
  class ConditionTrees {

    @ParameterizedTest(name = "{0}")
    @MethodSource("conditionTreeTestInputs")
    void shouldBuildConditionTreeCorrectly(
        String description,
        List<ConditionCreateInput> inputs,
        int expectedSaveCalls,
        Map<String, Optional<String>> expectedParentMap,
        boolean withStepFrom) {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, inputs);

      setupCreateStepTemplates(stepInput);

      if (withStepFrom) {
        Step stepFrom = mock(Step.class);
        when(stepRepository.findById("FROM")).thenReturn(Optional.of(stepFrom));
      }

      stepService.createStepTemplates(workflowId, List.of(stepInput));

      ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);

      verify(conditionService, times(expectedSaveCalls)).saveCondition(captor.capture());

      List<Condition> saved = captor.getAllValues();

      Map<String, Condition> byKey =
          saved.stream().collect(Collectors.toMap(Condition::getKey, c -> c));

      expectedParentMap.forEach(
          (childKey, parentKey) -> {
            Condition child = byKey.get(childKey);
            if (parentKey.isEmpty()) {
              assertNull(child.getConditionParent());
            } else {
              assertEquals(byKey.get(parentKey.get()), child.getConditionParent());
            }
          });

      if (withStepFrom) {
        assertNotNull(byKey.get("ROOT").getStepFrom());
      }
    }

    static Stream<Arguments> conditionTreeTestInputs() {

      return Stream.of(
          Arguments.of(
              "Single root condition",
              List.of(mockCondition("ROOT", null, null)),
              1,
              Map.of("ROOT", Optional.empty()),
              false),
          Arguments.of(
              "Root with one child",
              List.of(mockCondition("ROOT", null, null), mockCondition("CHILD", "ROOT", null)),
              2,
              Map.of("ROOT", Optional.empty(), "CHILD", Optional.of("ROOT")),
              false),
          Arguments.of(
              "Root with two-level tree",
              List.of(
                  mockCondition("ROOT", null, null),
                  mockCondition("A", "ROOT", null),
                  mockCondition("B", "A", null)),
              3,
              Map.of(
                  "ROOT", Optional.empty(),
                  "A", Optional.of("ROOT"),
                  "B", Optional.of("A")),
              false),
          Arguments.of(
              "Root with stepFrom",
              List.of(mockCondition("ROOT", null, "FROM")),
              1,
              Map.of("ROOT", Optional.empty()),
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
              List.of(mockCondition("ROOT 1", null, null), mockCondition("ROOT 2", null, null)));

      setupCreateStepTemplates(stepInput);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));
    }

    @Test
    void shouldThrowWhenNoRootConditionExists() {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(STEP_ACTION_CLASS.INJECT_EXECUTION, List.of(mockCondition("A", "X", null)));

      setupCreateStepTemplates(stepInput);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));
    }
  }

  /* ============================================================
   * startWorkflow — workflow + repository + wait filtering
   * ============================================================ */
  @Nested
  class StartWorkflow {

    @Test
    void shouldLaunchWorkflow_andNeverCallWait_whenNoStepTemplates() {
      // -------- Prepare --------
      String simulationId = UUID.randomUUID().toString();
      String templateWorkflowId = UUID.randomUUID().toString();

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.getId()).thenReturn(templateWorkflowId);

      Workflow workflowRun = mock(Workflow.class);

      when(workflowService.findWorkflowTemplateByIdExercise(simulationId))
          .thenReturn(workflowTemplate);
      when(workflowService.launchWorkflow(simulationId)).thenReturn(workflowRun);

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(Collections.emptyList());

      // -------- Act --------
      stepService.startWorkflow(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateByIdExercise(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflow(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepService, never()).ready(any(Step.class), any(Workflow.class), any());
      verifyNoMoreInteractions(workflowService);
    }

    @ParameterizedTest(name = "{index} => steps={0}, nonNullWaitIndices={1}")
    @MethodSource("startWorkflowTestInputs")
    void shouldCallWaitForEachStep_andHandleNullAndNonNullReturns(
        List<Step> stepTemplates, Set<Integer> nonNullWaitIndices) {
      // -------- Prepare --------
      String simulationId = UUID.randomUUID().toString();
      String templateWorkflowId = UUID.randomUUID().toString();

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.getId()).thenReturn(templateWorkflowId);

      Workflow workflowRun = mock(Workflow.class);

      when(workflowService.findWorkflowTemplateByIdExercise(simulationId))
          .thenReturn(workflowTemplate);
      when(workflowService.launchWorkflow(simulationId)).thenReturn(workflowRun);

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(stepTemplates);

      // For each template step:
      // - return a non-null Step for chosen indices
      // - return null otherwise
      Map<Step, Step> waitReturnByInputStep = new HashMap<>();
      for (int i = 0; i < stepTemplates.size(); i++) {
        Step input = stepTemplates.get(i);
        Step returned = nonNullWaitIndices.contains(i) ? mock(Step.class) : null;
        waitReturnByInputStep.put(input, returned);
      }

      doAnswer(
              invocation -> {
                Step inputStep = invocation.getArgument(0);
                return waitReturnByInputStep.get(inputStep);
              })
          .when(stepService)
          .ready(any(Step.class), eq(workflowRun), isNull());

      // -------- Act --------
      stepService.startWorkflow(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateByIdExercise(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflow(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepService, times(stepTemplates.size()))
          .ready(stepCaptor.capture(), workflowCaptor.capture(), isNull());

      assertEquals(stepTemplates.size(), stepCaptor.getAllValues().size());
      assertTrue(stepCaptor.getAllValues().containsAll(stepTemplates));

      for (Workflow wfArg : workflowCaptor.getAllValues()) {
        assertSame(workflowRun, wfArg);
      }

      verifyNoMoreInteractions(workflowService);
    }

    private static Stream<Arguments> startWorkflowTestInputs() {
      // NOTE: we use mocks for stable identity comparisons in captors/maps
      Step s1 = mock(Step.class);
      Step s2 = mock(Step.class);
      Step s3 = mock(Step.class);

      return Stream.of(
          // All waits return null -> covers "if (stepWait != null)" false path
          Arguments.of(List.of(s1, s2, s3), Set.of()),

          // Some waits return non-null -> covers add(...) line
          Arguments.of(List.of(s1, s2, s3), Set.of(0)),
          Arguments.of(List.of(s1, s2, s3), Set.of(1, 2)),

          // Boundary: 1 element
          Arguments.of(List.of(s1), Set.of()),
          Arguments.of(List.of(s1), Set.of(0)));
    }
  }

  /* ============================================================
   * ready — Execution step creation and queue chaining
   * ============================================================ */
  @Nested
  class Ready {

    /* ============================================================
     * ready — ActionStep resolution
     * ============================================================ */
    @Nested
    class ActionStepResolution {

      @Test
      void shouldThrowWhenActionStepIsNull() throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);

        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(null);

        // -------- Act + Assert --------
        assertThrows(
            BadRequestException.class,
            () -> stepService.ready(nextStepTemplateToExecute, workflowRun, "{\"a\":1}"));

        verify(stepRepository, never()).findById(anyString());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).checkCondition(any(), any(), any(), any(), any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).waitStep(any(), any());
      }
    }

    /* ============================================================
     * ready — Condition checking and outcomes
     * ============================================================ */
    @Nested
    class ConditionOutcomes {

      @ParameterizedTest(name = "{index} => conditionExecutionNull={0}")
      @MethodSource("conditionExecutionTestInputs")
      void shouldReturnNullAndStop_whenConditionExecutionIsNull(boolean conditionExecutionNull)
          throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String input = "{\"hello\":\"world\"}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findById(stepId)).thenReturn(Optional.of(persistedTemplate));

        // used by checkCondition call
        when(persistedTemplate.getData()).thenReturn("{\"data\":true}");

        when(conditionService.checkCondition(
                eq(persistedTemplate),
                eq(input),
                eq("{\"data\":true}"),
                eq(workflowRun),
                eq(stepService)))
            .thenReturn(conditionExecutionNull ? null : List.of(mock(Condition.class)));

        // If conditions are non-null, actionStep.wait will be called; to keep focus here,
        // we only validate the null-case behavior. We force conditionExecutionNull = true in source
        // below.
        assertTrue(
            conditionExecutionNull,
            "This scenario is intended for the null conditionExecution path.");

        // -------- Act --------
        Step result = stepService.ready(nextStepTemplateToExecute, workflowRun, input);

        // -------- Assert --------
        assertNull(result);

        verify(stepRepository).findById(stepIdCaptor.capture());
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(conditionService)
            .checkCondition(
                eq(persistedTemplate),
                eq(input),
                eq("{\"data\":true}"),
                eq(workflowRun),
                eq(stepService));

        verify(actionStep, never()).wait(any(), any(), any());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).waitStep(any(), any());
      }

      static Stream<Arguments> conditionExecutionTestInputs() {
        return Stream.of(
            Arguments.of(true) // conditionExecution == null
            );
      }
    }

    /* ============================================================
     * ready — Nominal case
     * ============================================================ */
    @Nested
    class ReadyNominalCase {

      @Test
      void shouldCreateReadyStep_saveConditions_andQueueStep_whenConditionsAreValid()
          throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String input = "{\"x\":1}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findById(stepId)).thenReturn(Optional.of(persistedTemplate));

        when(persistedTemplate.getData()).thenReturn("{\"tpl\":true}");

        Condition c1 = mock(Condition.class);
        Condition c2 = mock(Condition.class);
        List<Condition> conditionExecution = new ArrayList<>(List.of(c1, c2));

        when(conditionService.checkCondition(
                eq(persistedTemplate),
                eq(input),
                eq("{\"tpl\":true}"),
                eq(workflowRun),
                eq(stepService)))
            .thenReturn(conditionExecution);

        Step stepWait = mock(Step.class);

        when(actionStep.wait(persistedTemplate, input, workflowRun)).thenReturn(stepWait);
        when(stepRepository.save(stepWait)).thenReturn(stepWait);

        // queueChainingService.waitStep(...) does not throw here
        doNothing().when(queueChainingService).waitStep(stepWait, workflowRun);

        // -------- Act --------
        Step result = stepService.ready(nextStepTemplateToExecute, workflowRun, input);

        // -------- Assert --------
        assertSame(stepWait, result);

        // findById(...) is private -> verify repository call instead
        verify(stepRepository).findById(stepIdCaptor.capture());
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(conditionService)
            .checkCondition(
                eq(persistedTemplate),
                eq(input),
                eq("{\"tpl\":true}"),
                eq(workflowRun),
                eq(stepService));

        verify(actionStep).wait(persistedTemplate, input, workflowRun);

        // workflow set before save
        verify(stepWait).setWorkflow(workflowCaptor.capture());
        assertSame(workflowRun, workflowCaptor.getValue());

        // saveStep(...) is private -> verify repository save instead
        verify(stepRepository).save(stepCaptor.capture());
        assertSame(stepWait, stepCaptor.getValue());

        // conditions should be linked to the final step (savedStepWait)
        verify(c1).setStep(stepWait);
        verify(c2).setStep(stepWait);

        verify(conditionService).saveAllConditions(conditionsCaptor.capture());
        assertEquals(2, conditionsCaptor.getValue().size());
        assertTrue(conditionsCaptor.getValue().containsAll(List.of(c1, c2)));

        verify(queueChainingService).waitStep(stepWait, workflowRun);
      }
    }

    /* ============================================================
     * ready — Queue chaining exception handling
     * ============================================================ */
    @Nested
    class QueueChainingIOException {

      @Test
      void shouldWrapIOExceptionIntoRuntimeException() throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String input = "{\"q\":true}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findById(stepId)).thenReturn(Optional.of(persistedTemplate));

        when(persistedTemplate.getData()).thenReturn("{\"tpl\":true}");

        Condition c1 = mock(Condition.class);
        List<Condition> conditionExecution = new ArrayList<>(List.of(c1));

        when(conditionService.checkCondition(
                eq(persistedTemplate),
                eq(input),
                eq("{\"tpl\":true}"),
                eq(workflowRun),
                eq(stepService)))
            .thenReturn(conditionExecution);

        Step stepWait = mock(Step.class);
        when(actionStep.wait(persistedTemplate, input, workflowRun)).thenReturn(stepWait);
        when(stepRepository.save(stepWait)).thenReturn(stepWait);

        IOException ioException = new IOException("boom");
        doThrow(ioException).when(queueChainingService).waitStep(stepWait, workflowRun);

        // -------- Act --------
        RuntimeException ex =
            assertThrows(
                RuntimeException.class,
                () -> stepService.ready(nextStepTemplateToExecute, workflowRun, input));

        // -------- Assert --------
        assertSame(ioException, ex.getCause());

        verify(actionStep).wait(persistedTemplate, input, workflowRun);
        verify(stepWait).setWorkflow(workflowRun);
        verify(stepRepository).save(stepWait);

        verify(c1).setStep(stepWait);
        verify(conditionService).saveAllConditions(anyList());

        verify(queueChainingService).waitStep(stepWait, workflowRun);
      }
    }
  }

  /* ============================================================
   * run — Execute ready step
   * ============================================================ */
  @Nested
  class Run {

    /* ============================================================
     * run — ActionStep resolution
     * ============================================================ */
    @Test
    void shouldThrowWhenActionStepIsNull() {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);

      when(stepReady.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
      when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(null);

      // -------- Act + Assert --------
      assertThrows(BadRequestException.class, () -> stepService.run(stepReady));

      verify(stepRepository, never()).save(any());
      verify(workflowService, never()).saveWorkflowRun(any());
    }

    /* ============================================================
     * run — actionStep.run returns null
     * ============================================================ */
    @Nested
    class WhenRunReturnsNull {

      @Test
      void shouldEndStepOnly_whenOtherStepsStillRunning() {
        // -------- Prepare --------
        Step stepReady = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String workflowRunId = UUID.randomUUID().toString();

        when(stepReady.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

        when(actionStep.run(stepReady)).thenReturn(null);

        when(stepReady.getWorkflow()).thenReturn(workflowRun);
        when(workflowRun.getId()).thenReturn(workflowRunId);

        when(stepRepository.countRunningStep(workflowRunId)).thenReturn(2);

        // -------- Act --------
        stepService.run(stepReady);

        // -------- Assert --------
        verify(stepReady).setStatus(STEP_STATUS.END);
        verify(stepRepository).save(stepReady);

        verify(stepRepository).countRunningStep(workflowRunId);

        verify(workflowRun, never()).setStatus(any());
        verify(workflowService, never()).saveWorkflowRun(any());
      }

      @Test
      void shouldEndStepAndWorkflow_whenNoRunningStepsRemain() {
        // -------- Prepare --------
        Step stepReady = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String workflowRunId = UUID.randomUUID().toString();

        when(stepReady.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

        when(actionStep.run(stepReady)).thenReturn(null);

        when(stepReady.getWorkflow()).thenReturn(workflowRun);
        when(workflowRun.getId()).thenReturn(workflowRunId);

        when(stepRepository.countRunningStep(workflowRunId)).thenReturn(0);

        // -------- Act --------
        stepService.run(stepReady);

        // -------- Assert --------
        verify(stepReady).setStatus(STEP_STATUS.END);
        verify(stepRepository).save(stepReady);

        verify(stepRepository).countRunningStep(workflowRunId);

        verify(workflowRun).setStatus(WORKFLOW_STATUS.END);
        verify(workflowService).saveWorkflowRun(workflowRun);
      }
    }

    /* ============================================================
     * run — actionStep.run returns a step
     * ============================================================ */
    @Test
    void shouldSetRunStatusAndSaveStep_whenRunReturnsStep() {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);
      Step stepRun = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(stepReady.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);
      when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(actionStep);

      when(actionStep.run(stepReady)).thenReturn(stepRun);

      // -------- Act --------
      stepService.run(stepReady);

      // -------- Assert --------
      verify(stepRun).setStatus(STEP_STATUS.RUN);
      verify(stepRepository).save(stepRun);

      verify(stepReady, never()).setStatus(any());
      verify(stepRepository, never()).countRunningStep(any());
      verify(workflowService, never()).saveWorkflowRun(any());
    }
  }

  /* ============================================================
   * countExecutedStep — Repository delegation
   * ============================================================ */
  @Nested
  class CountExecutedStep {

    @Test
    void shouldReturnRepositoryCount() {
      // -------- Prepare --------
      String workflowRunId = UUID.randomUUID().toString();
      String stepTemplateId = UUID.randomUUID().toString();
      int expected = 42;

      when(stepRepository.countStepExecutedByStepTemplateIdAndWorkflowRunId(
              workflowRunId, stepTemplateId))
          .thenReturn(expected);

      // -------- Act --------
      int result = stepService.countExecutedStep(workflowRunId, stepTemplateId);

      // -------- Assert --------
      assertEquals(expected, result);

      verify(stepRepository)
          .countStepExecutedByStepTemplateIdAndWorkflowRunId(workflowRunId, stepTemplateId);
      verifyNoMoreInteractions(stepRepository);
    }
  }

  /* ============================================================
   * factoryAction — ActionStep resolution
   * ============================================================ */
  @Nested
  class FactoryAction {

    @Test
    void shouldReturnInjectExecutionStep_whenActionIsInjectExecution() {
      // -------- Prepare --------
      InjectExecutionStep injectExecutionStep = mock(InjectExecutionStep.class);

      when(applicationContext.getBean(InjectExecutionStep.class)).thenReturn(injectExecutionStep);

      // -------- Act --------
      ActionStep result = stepService.factoryAction(STEP_ACTION_CLASS.INJECT_EXECUTION);

      // -------- Assert --------
      assertSame(injectExecutionStep, result);

      verify(applicationContext).getBean(InjectExecutionStep.class);
      verifyNoMoreInteractions(applicationContext);
    }

    @Test
    void shouldReturnNull_whenActionClassIsNotSupported() {
      // -------- Prepare --------
      STEP_ACTION_CLASS unsupportedAction = STEP_ACTION_CLASS.UNSUPPORTED;

      // -------- Act --------
      ActionStep result = stepService.factoryAction(unsupportedAction);

      // -------- Assert --------
      assertNull(result);

      verifyNoInteractions(applicationContext);
    }
  }

  /* ============================================================
   * saveSteps / saveStep — Repository delegation
   * ============================================================ */
  @Nested
  class SaveStepsAndSaveStep {

    @Captor private ArgumentCaptor<List<Step>> stepsCaptor;
    @Captor private ArgumentCaptor<Step> stepCaptor;

    @Test
    void shouldCallSaveAll_withGivenSteps() {
      // -------- Prepare --------
      Step s1 = mock(Step.class);
      Step s2 = mock(Step.class);
      List<Step> steps = List.of(s1, s2);

      // -------- Act --------
      stepService.saveSteps(steps);

      // -------- Assert --------
      verify(stepRepository).saveAll(stepsCaptor.capture());
      assertSame(steps, stepsCaptor.getValue());

      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldSaveStep_andReturnSavedInstance() {
      // -------- Prepare --------
      Step step = mock(Step.class);
      Step saved = mock(Step.class);

      when(stepRepository.save(step)).thenReturn(saved);

      // -------- Act --------
      Step result = stepService.saveStep(step);

      // -------- Assert --------
      assertSame(saved, result);

      verify(stepRepository).save(stepCaptor.capture());
      assertSame(step, stepCaptor.getValue());

      verifyNoMoreInteractions(stepRepository);
    }
  }

  /* ============================================================
   * Find step(s) — Repository delegation
   * ============================================================ */
  @Nested
  class FindSteps {

    @Test
    void shouldFindStepTemplateById() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, STEP_STATUS.TEMPLATE))
          .thenReturn(step);

      // -------- Act --------
      Step result = stepService.findStepTemplateById(stepId);

      // -------- Assert --------
      assertSame(step, result);

      verify(stepRepository).findByStepTemplateIdIsNullAndIdAndStatus(stepId, STEP_STATUS.TEMPLATE);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindAllStepTemplateByWorkflow() {
      // -------- Prepare --------
      String workflowId = UUID.randomUUID().toString();
      List<Step> steps = List.of(mock(Step.class), mock(Step.class));

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflowId)).thenReturn(steps);

      // -------- Act --------
      List<Step> result = stepService.findAllStepTemplateByWorkflow(workflowId);

      // -------- Assert --------
      assertSame(steps, result);

      verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(workflowId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindStepReadyById() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, STEP_STATUS.WAIT))
          .thenReturn(step);

      // -------- Act --------
      Step result = stepService.findStepReadyById(stepId);

      // -------- Assert --------
      assertSame(step, result);

      verify(stepRepository).findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, STEP_STATUS.WAIT);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindAllRunningSteps() {
      // -------- Prepare --------
      List<Step> steps = List.of(mock(Step.class), mock(Step.class));

      when(stepRepository.findAllByStatus(STEP_STATUS.RUN)).thenReturn(steps);

      // -------- Act --------
      List<Step> result = stepService.findAllStepRun();

      // -------- Assert --------
      assertSame(steps, result);

      verify(stepRepository).findAllByStatus(STEP_STATUS.RUN);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindAllExecutedStepsByTemplateAndWorkflow() {
      // -------- Prepare --------
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowRunId = UUID.randomUUID().toString();

      List<Step> steps = List.of(mock(Step.class), mock(Step.class));

      when(stepRepository.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
              stepTemplateId, workflowRunId))
          .thenReturn(steps);

      // -------- Act --------
      List<Step> result =
          stepService.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
              stepTemplateId, workflowRunId);

      // -------- Assert --------
      assertSame(steps, result);

      verify(stepRepository)
          .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(stepTemplateId, workflowRunId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindById_whenStepExists() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));

      // -------- Act --------
      Step result = stepService.findById(stepId);

      // -------- Assert --------
      assertSame(step, result);

      verify(stepRepository).findById(stepId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldThrow_whenFindByIdReturnsEmpty() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();

      when(stepRepository.findById(stepId)).thenReturn(Optional.empty());

      // -------- Act + Assert --------
      assertThrows(NoSuchElementException.class, () -> stepService.findById(stepId));

      verify(stepRepository).findById(stepId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldFindStepIdByInjectId() {
      // -------- Prepare --------
      String injectId = UUID.randomUUID().toString();
      String stepId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.of(stepId));

      // -------- Act --------
      Optional<String> result = stepService.findStepIdByInjectId(injectId);

      // -------- Assert --------
      assertTrue(result.isPresent());
      assertEquals(stepId, result.get());

      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldReturnEmptyOptional_whenNoStepFoundForInjectId() {
      // -------- Prepare --------
      String injectId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.empty());

      // -------- Act --------
      Optional<String> result = stepService.findStepIdByInjectId(injectId);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }
  }

  /* ============================================================
   * Queue events handling — handleReadyEvent / handleDelayEvent / handleExternalUpdateEvent
   * ============================================================ */
  @Nested
  class QueueEventsHandling {

    @Captor private ArgumentCaptor<StepEvent> stepEventCaptor;
    @Captor private ArgumentCaptor<ExternalUpdateEvent> externalUpdateEventCaptor;

    /* ============================================================
     * handleReadyEvent / handleDelayEvent / handleExternalUpdateEvent (list)
     * ============================================================ */
    @Nested
    class BatchHandlers {

      @Test
      void shouldConsumeReadyEvents_andReturnSameList() {
        // -------- Prepare --------
        StepEvent e1 = mock(StepEvent.class);
        StepEvent e2 = mock(StepEvent.class);
        List<StepEvent> events = List.of(e1, e2);

        doNothing().when(stepService).handleReadyStepEvent(any(StepEvent.class));

        // -------- Act --------
        List<StepEvent> result = stepService.handleReadyEvent(events);

        // -------- Assert --------
        assertSame(events, result);

        verify(stepService, times(2)).handleReadyStepEvent(stepEventCaptor.capture());
        assertTrue(stepEventCaptor.getAllValues().containsAll(events));
      }

      @Test
      void shouldConsumeDelayEvents_andReturnSameList() {
        // -------- Prepare --------
        StepEvent e1 = mock(StepEvent.class);
        StepEvent e2 = mock(StepEvent.class);
        List<StepEvent> events = List.of(e1, e2);

        doNothing().when(stepService).handleDelayStepEvent(any(StepEvent.class));

        // -------- Act --------
        List<StepEvent> result = stepService.handleDelayEvent(events);

        // -------- Assert --------
        assertSame(events, result);

        verify(stepService, times(2)).handleDelayStepEvent(stepEventCaptor.capture());
        assertTrue(stepEventCaptor.getAllValues().containsAll(events));
      }

      @Test
      void shouldConsumeExternalUpdateEvents_andReturnSameList() {
        // -------- Prepare --------
        ExternalUpdateEvent e1 = mock(ExternalUpdateEvent.class);
        ExternalUpdateEvent e2 = mock(ExternalUpdateEvent.class);
        List<ExternalUpdateEvent> events = List.of(e1, e2);

        doNothing().when(stepService).handleExternalUpdateEvent(any(ExternalUpdateEvent.class));

        // -------- Act --------
        List<ExternalUpdateEvent> result = stepService.handleExternalUpdateEvent(events);

        // -------- Assert --------
        assertSame(events, result);

        verify(stepService, times(2))
            .handleExternalUpdateEvent(externalUpdateEventCaptor.capture());
        assertTrue(externalUpdateEventCaptor.getAllValues().containsAll(events));
      }
    }

    /* ============================================================
     * handleReadyStepEvent — repository lookup then run
     * ============================================================ */
    @Nested
    class HandleReadyStepEvent {

      @ParameterizedTest(name = "{index} => stepFound={0}")
      @MethodSource("readyStepEventScenarios")
      void shouldRunOnlyWhenStepExists(boolean stepFound) {
        // -------- Prepare --------
        StepEvent event = mock(StepEvent.class);
        String stepId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepId);

        Step step = mock(Step.class);

        when(stepRepository.findById(stepId))
            .thenReturn(stepFound ? Optional.of(step) : Optional.empty());

        // Avoid executing real run(...) logic
        doNothing().when(stepService).run(any(Step.class));

        // -------- Act --------
        stepService.handleReadyStepEvent(event);

        // -------- Assert --------
        verify(stepRepository).findById(stepId);

        if (stepFound) {
          verify(stepService).run(step);
        } else {
          verify(stepService, never()).run(any());
        }
      }

      static Stream<Arguments> readyStepEventScenarios() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
      }
    }

    /* ============================================================
     * handleDelayStepEvent — repository lookup then workflow lookup then ready(...)
     * ============================================================ */
    @Nested
    class HandleDelayStepEvent {

      @ParameterizedTest(name = "{index} => stepFound={0}")
      @MethodSource("delayStepEventScenarios")
      void shouldReadyOnlyWhenStepExists(boolean stepFound) {
        // -------- Prepare --------
        StepEvent event = mock(StepEvent.class);
        String stepId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepId);
        when(event.getWorkflowId()).thenReturn(workflowId);

        Step step = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);

        when(stepRepository.findById(stepId))
            .thenReturn(stepFound ? Optional.of(step) : Optional.empty());

        when(workflowService.getWorkflowById(workflowId)).thenReturn(workflowRun);

        // Avoid executing real ready(...) logic
        doReturn(mock(Step.class))
            .when(stepService)
            .ready(any(Step.class), any(Workflow.class), isNull());

        // -------- Act --------
        stepService.handleDelayStepEvent(event);

        // -------- Assert --------
        verify(stepRepository).findById(stepId);

        if (stepFound) {
          verify(workflowService).getWorkflowById(workflowId);
          verify(stepService).ready(step, workflowRun, null);
        } else {
          verify(workflowService, never()).getWorkflowById(anyString());
          verify(stepService, never()).ready(any(), any(), any());
        }
      }

      static Stream<Arguments> delayStepEventScenarios() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
      }
    }

    /* ============================================================
     * handleExternalUpdateEvent — update step then compute next steps to ready
     * ============================================================ */
    @Nested
    class HandleExternalUpdateEventSingle {

      @Test
      void shouldThrowWhenActionStepIsNull() {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(STEP_ACTION_CLASS.UNSUPPORTED);

        // this.findById(...) -> repository.findById(...).orElseThrow()
        when(stepRepository.findById(stepRunId)).thenReturn(Optional.of(stepRun));

        when(stepService.factoryAction(STEP_ACTION_CLASS.UNSUPPORTED)).thenReturn(null);

        // -------- Act + Assert --------
        assertThrows(BadRequestException.class, () -> stepService.handleExternalUpdateEvent(event));

        verify(stepRepository).findById(stepRunId);
        verify(stepRepository, never()).save(any());
        verify(stepService, never()).ready(any(), any(), any());
      }

      @Test
      void shouldDoNothing_whenUpdateReturnsNull() {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(STEP_ACTION_CLASS.INJECT_EXECUTION);

        when(stepRepository.findById(stepRunId)).thenReturn(Optional.of(stepRun));

        ActionStep actionStep = mock(ActionStep.class);
        when(stepService.factoryAction(STEP_ACTION_CLASS.INJECT_EXECUTION)).thenReturn(actionStep);

        when(actionStep.update(stepRun)).thenReturn(null);

        // -------- Act --------
        stepService.handleExternalUpdateEvent(event);

        // -------- Assert --------
        verify(stepRepository).findById(stepRunId);
        verify(actionStep).update(stepRun);

        verify(stepRepository, never()).save(any());
        verify(stepService, never()).ready(any(), any(), any());
        verify(conditionService, never()).findAllByStepId(anyString());
      }

      @Test
      void shouldSaveUpdatedStep_andReadyNextStepsWhoseConditionsDependOnCurrentTemplate() {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        // Current running step
        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(STEP_ACTION_CLASS.INJECT_EXECUTION);

        Workflow workflowRun = mock(Workflow.class);
        when(stepRun.getWorkflow()).thenReturn(workflowRun);

        // Its template
        Step stepTemplateOfRun = mock(Step.class);
        String currentTemplateId = UUID.randomUUID().toString();
        when(stepTemplateOfRun.getId()).thenReturn(currentTemplateId);
        when(stepRun.getStepTemplate()).thenReturn(stepTemplateOfRun);

        // findById(...) -> repo.findById(...).orElseThrow()
        when(stepRepository.findById(stepRunId)).thenReturn(Optional.of(stepRun));

        // actionStep.update(...) returns non-null => happy path
        ActionStep actionStep = mock(ActionStep.class);
        when(stepService.factoryAction(STEP_ACTION_CLASS.INJECT_EXECUTION)).thenReturn(actionStep);

        Step updated = mock(Step.class);
        when(actionStep.update(stepRun)).thenReturn(updated);

        // saveStep(updated) -> stepRepository.save(updated)
        when(stepRepository.save(updated)).thenReturn(updated);

        // GET STEP TEMPLATE CURRENT (repository delegation)
        Step stepTemplateCurrent = mock(Step.class);
        when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(
                currentTemplateId, STEP_STATUS.TEMPLATE))
            .thenReturn(stepTemplateCurrent);

        // template workflow + templates list
        Workflow workflowTemplate = mock(Workflow.class);
        String workflowTemplateId = UUID.randomUUID().toString();
        when(stepTemplateCurrent.getWorkflow()).thenReturn(workflowTemplate);
        when(workflowTemplate.getId()).thenReturn(workflowTemplateId);

        Step tpl1 = mock(Step.class);
        Step tpl2 = mock(Step.class);
        String tpl1Id = UUID.randomUUID().toString();
        String tpl2Id = UUID.randomUUID().toString();
        when(tpl1.getId()).thenReturn(tpl1Id);
        when(tpl2.getId()).thenReturn(tpl2Id);

        when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateId))
            .thenReturn(List.of(tpl1, tpl2));

        // Conditions: tpl1 depends on currentTemplateId, tpl2 does not
        Condition condMatch = mock(Condition.class);
        Step stepFrom = mock(Step.class);
        when(stepFrom.getId()).thenReturn(currentTemplateId);
        when(condMatch.getStepFrom()).thenReturn(stepFrom);

        Condition condNoMatch = mock(Condition.class);
        Step otherFrom = mock(Step.class);
        when(otherFrom.getId()).thenReturn(UUID.randomUUID().toString());
        when(condNoMatch.getStepFrom()).thenReturn(otherFrom);

        when(conditionService.findAllByStepId(tpl1Id)).thenReturn(List.of(condMatch));
        when(conditionService.findAllByStepId(tpl2Id)).thenReturn(List.of(condNoMatch));

        // Avoid executing real ready(...) behavior; we just want to verify calls
        doReturn(mock(Step.class))
            .when(stepService)
            .ready(any(Step.class), any(Workflow.class), isNull());

        // -------- Act --------
        stepService.handleExternalUpdateEvent(event);

        // -------- Assert --------
        // updated step saved
        verify(stepRepository).save(updated);

        // template lookup chain
        verify(stepRepository)
            .findByStepTemplateIdIsNullAndIdAndStatus(currentTemplateId, STEP_STATUS.TEMPLATE);
        verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateId);

        // conditions checked
        verify(conditionService).findAllByStepId(tpl1Id);
        verify(conditionService).findAllByStepId(tpl2Id);

        // only tpl1 should be readied (because it depends on currentTemplateId)
        verify(stepService).ready(tpl1, workflowRun, null);
        verify(stepService, never()).ready(eq(tpl2), any(), any());
      }
    }
  }

  /* ============================================================
   * Helpers
   * ============================================================ */

  private void setupCreateStepTemplates(StepsCreateInput.StepCreateInput stepInput) {

    Workflow workflow = mock(Workflow.class);
    Step step = mock(Step.class);
    ActionStep actionStep = mock(ActionStep.class);

    when(workflowService.getWorkflowById(workflowId)).thenReturn(workflow);

    when(stepService.factoryAction(stepInput.getStepAction())).thenReturn(actionStep);

    when(actionStep.create(any(), eq(workflow))).thenReturn(step);

    when(stepRepository.save(step)).thenReturn(step);

    when(conditionService.saveCondition(any())).thenAnswer(i -> i.getArgument(0));
  }

  private StepsCreateInput.StepCreateInput mockStep(
      STEP_ACTION_CLASS actionClass, List<ConditionCreateInput> conditions) {

    StepsCreateInput.StepCreateInput step = mock(StepsCreateInput.StepCreateInput.class);

    when(step.getStepAction()).thenReturn(actionClass);
    when(step.getConditions()).thenReturn(conditions);

    return step;
  }

  private static ConditionCreateInput mockCondition(
      String key, String parentTempId, String stepFrom) {

    ConditionCreateInput c = mock(ConditionCreateInput.class);

    when(c.getKey()).thenReturn(key);
    when(c.getTemporaryId()).thenReturn(key);
    when(c.getTemporaryIdConditionParent()).thenReturn(parentTempId);
    when(c.getStepFrom()).thenReturn(stepFrom);

    return c;
  }
}
