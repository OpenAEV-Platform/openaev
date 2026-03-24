package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.scheduler.jobs.QueueChainingJob;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

@ExtendWith(MockitoExtension.class)
public class StepServiceTest {

  @Mock private StepRepository stepRepository;
  @Mock private InjectExecutionStep injectExecutionStep;
  @Mock private ActionStep actionStep;
  @Mock private WorkflowService workflowService;
  @Mock private ConditionService conditionService;
  @Mock private QueueChainingService queueChainingService;
  @Mock private StepDelayQueueService stepDelayQueueService;

  @Spy @InjectMocks StepService stepService;
  private QueueChainingJob queueChainingJob;

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
  @BeforeEach
  void setUp() {
    queueChainingJob = new QueueChainingJob(stepDelayQueueService, stepService);
  }

  @Nested
  class ActionStepResolution {

    @Test
    void shouldThrowWhenActionStepIsNull() {
      StepsCreateInput.StepCreateInput stepInput = mockStep(null, List.of());

      when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(mock(Workflow.class));

      assertThrows(
          ChainingException.class,
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
    void shouldSkipConditionCreationWhenEmpty() throws ChainingException {
      StepsCreateInput.StepCreateInput stepInput =
          mockStep(StepActionClass.INJECT_EXECUTION, Collections.emptyList());

      setupCreateStepTemplates(stepInput, false);

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
        boolean withStepFrom)
        throws ChainingException {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(StepActionClass.INJECT_EXECUTION, inputs);

      setupCreateStepTemplates(stepInput, true);

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
    void shouldThrowWhenMultipleRootConditions() throws ChainingException {

      StepsCreateInput.StepCreateInput stepInput =
          mockStep(
              StepActionClass.INJECT_EXECUTION,
              List.of(
                  ConditionCreateInput.builder()
                      .key("ROOT 1")
                      .temporaryIdConditionParent(null)
                      .stepFrom(null)
                      .build(),
                  ConditionCreateInput.builder()
                      .key("ROOT 2")
                      .temporaryIdConditionParent(null)
                      .stepFrom(null)
                      .build()));

      setupCreateStepTemplates(stepInput, false);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));
    }

    @Test
    void shouldThrowWhenNoRootConditionExists() throws ChainingException {
      ConditionCreateInput conditionCreateInput =
          ConditionCreateInput.builder().key("A").temporaryIdConditionParent("X").build();
      StepsCreateInput.StepCreateInput stepInput =
          mockStep(StepActionClass.INJECT_EXECUTION, List.of(conditionCreateInput));

      Workflow workflow = mock(Workflow.class);
      Step step = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(workflow);

      when(stepService.factoryAction(stepInput.getStepAction(), null)).thenReturn(actionStep);

      when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.ofNullable(step));

      assertNotNull(step);
      when(stepRepository.save(step)).thenReturn(step);

      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));
    }
  }

  /* ============================================================
   * startWorkflow — workflow + repository + ready filtering
   * ============================================================ */
  @Nested
  class StartWorkflow {

    @Test
    void shouldLaunchWorkflow_andNeverCallReady_whenNoStepTemplates() throws ChainingException {
      // -------- Prepare --------
      String simulationId = UUID.randomUUID().toString();
      String templateWorkflowId = UUID.randomUUID().toString();

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.getId()).thenReturn(templateWorkflowId);

      Workflow workflowRun = mock(Workflow.class);

      when(workflowService.launchWorkflow(workflowTemplate)).thenReturn(workflowRun);
      when(workflowService.findWorkflowTemplateBySimulationId(simulationId))
          .thenReturn(Optional.of(workflowTemplate));

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(Collections.emptyList());

      // -------- Act --------
      stepService.startWorkflow(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateBySimulationId(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflow(workflowCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepService, never()).ready(any(Step.class), any(Workflow.class), any());
    }

    @ParameterizedTest(name = "{index} => steps={0}, nonNullReadyIndices={1}")
    @MethodSource("startWorkflowTestInputs")
    void shouldCallReadyForEachStep_andHandleNullAndNonNullReturns(
        List<Step> stepTemplates, Set<Integer> nonNullReadyIndices) throws ChainingException {
      // -------- Prepare --------
      String simulationId = UUID.randomUUID().toString();
      String templateWorkflowId = UUID.randomUUID().toString();

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.getId()).thenReturn(templateWorkflowId);

      Workflow workflowRun = mock(Workflow.class);

      when(workflowService.findWorkflowTemplateBySimulationId(simulationId))
          .thenReturn(Optional.of(workflowTemplate));
      when(workflowService.launchWorkflow(workflowTemplate)).thenReturn(workflowRun);

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(stepTemplates);

      // For each template step:
      // - return a non-null Step for chosen indices
      // - return null otherwise
      Map<Step, Optional<Step>> readyReturnByInputStep = new HashMap<>();
      for (int i = 0; i < stepTemplates.size(); i++) {
        Step input = stepTemplates.get(i);
        Optional<Step> returned =
            (nonNullReadyIndices.contains(i)
                ? Optional.ofNullable(mock(Step.class))
                : Optional.empty());
        readyReturnByInputStep.put(input, returned);
      }

      doAnswer(
              invocation -> {
                Step inputStep = invocation.getArgument(0);
                return readyReturnByInputStep.get(inputStep);
              })
          .when(stepService)
          .ready(any(Step.class), eq(workflowRun), isNull());

      // -------- Act --------
      stepService.startWorkflow(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateBySimulationId(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflow(workflowTemplate);
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
          // All ready return null -> covers "if (stepReady != null)" false path
          Arguments.of(List.of(s1, s2, s3), Set.of()),

          // Some ready return non-null -> covers add(...) line
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

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(null);

        // -------- Act + Assert --------
        assertThrows(
            ChainingException.class,
            () -> stepService.ready(nextStepTemplateToExecute, workflowRun, "{\"a\":1}"));

        verify(stepRepository, never()).findById(anyString());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).checkCondition(any(), any(), any(), any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }

    /* ============================================================
     * ready — Condition checking and outcomes
     * ============================================================ */
    @Nested
    class ConditionOutcomes {
      @Test
      void shouldReturnNullAndStop_whenConditionExecutionIsNull() throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String input = "{\"hello\":\"world\"}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(null);
        doReturn(actionStep).when(stepService).factoryAction(null, stepId);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        when(conditionService.checkCondition(
                eq(persistedTemplate), eq(input), eq(workflowRun), eq(stepService)))
            .thenReturn(null);

        // -------- Act --------
        Optional<Step> resultOpt = stepService.ready(nextStepTemplateToExecute, workflowRun, input);

        // -------- Assert --------
        assertFalse(resultOpt.isPresent());

        verify(stepRepository).findByIdAndStatus(stepIdCaptor.capture(), any());
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(conditionService)
            .checkCondition(eq(persistedTemplate), eq(input), eq(workflowRun), eq(stepService));

        verify(actionStep, never()).ready(any(), any(), any());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).readyStep(any(), any());
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

        when(nextStepTemplateToExecute.getStepAction())
            .thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(actionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);

        Condition c1 = mock(Condition.class);
        Condition c2 = mock(Condition.class);
        List<Condition> conditionExecution = new ArrayList<>(List.of(c1, c2));

        when(conditionService.checkCondition(
                eq(persistedTemplate), eq(input), eq(workflowRun), eq(stepService)))
            .thenReturn(conditionExecution);

        Step stepReady = mock(Step.class);

        when(actionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);
        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        // queueChainingService.readyStep(...) does not throw here
        doNothing().when(queueChainingService).readyStep(stepReady, workflowRun);

        when(stepRepository.findByIdAndStatus(any(), eq(StepStatus.TEMPLATE)))
            .thenReturn(Optional.of(persistedTemplate));
        // -------- Act --------
        Optional<Step> resultOpt = stepService.ready(nextStepTemplateToExecute, workflowRun, input);
        assertTrue(resultOpt.isPresent());
        Step result = resultOpt.get();

        // -------- Assert --------
        assertSame(stepReady, result);

        // findByIdAndStatus(...) is private -> verify repository call instead
        verify(stepRepository).findByIdAndStatus(stepIdCaptor.capture(), eq(StepStatus.TEMPLATE));
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(conditionService)
            .checkCondition(eq(persistedTemplate), eq(input), eq(workflowRun), eq(stepService));

        verify(actionStep).ready(persistedTemplate, input, workflowRun);

        // saveStep(...) is private -> verify repository save instead
        verify(stepRepository).save(stepCaptor.capture());
        assertSame(stepReady, stepCaptor.getValue());

        // conditions should be linked to the final step (savedstepReady)
        verify(c1).setStep(stepReady);
        verify(c2).setStep(stepReady);

        verify(conditionService).saveAllConditions(conditionsCaptor.capture());
        assertEquals(2, conditionsCaptor.getValue().size());
        assertTrue(conditionsCaptor.getValue().containsAll(List.of(c1, c2)));

        verify(queueChainingService).readyStep(stepReady, workflowRun);
      }
    }

    /* ============================================================
     * ready — Queue chaining exception handling
     * ============================================================ */
    @Nested
    class QueueChainingIOException {

      @Test
      void shouldWrapIOExceptionIntoChainingException() throws Exception {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        String input = "{\"q\":true}";
        String stepId = UUID.randomUUID().toString();
        when(nextStepTemplateToExecute.getStepAction())
            .thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(actionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        Condition c1 = mock(Condition.class);
        List<Condition> conditionExecution = new ArrayList<>(List.of(c1));

        when(conditionService.checkCondition(
                eq(persistedTemplate), eq(input), eq(workflowRun), eq(stepService)))
            .thenReturn(conditionExecution);

        Step stepReady = mock(Step.class);

        when(actionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);

        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        IOException ioException = new IOException("boom");
        doThrow(ioException).when(queueChainingService).readyStep(stepReady, workflowRun);

        // -------- Act --------
        ChainingException ex =
            assertThrows(
                ChainingException.class,
                () -> stepService.ready(nextStepTemplateToExecute, workflowRun, input));

        // -------- Assert --------
        assertSame(ioException, ex.getCause());

        verify(actionStep).ready(persistedTemplate, input, workflowRun);
        verify(stepRepository, times(2)).save(stepReady);

        verify(c1).setStep(stepReady);
        verify(conditionService).saveAllConditions(anyList());

        verify(queueChainingService).readyStep(stepReady, workflowRun);
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
    void shouldMoveStateStepToEndWhenActionStepIsNull() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);

      // -------- Act + Assert --------
      stepService.run(stepReady);
      verify(stepService).factoryAction(stepReady.getStepAction(), stepReady.getId());
      verify(stepRepository, times(1)).save(any());
      verify(workflowService, never()).saveWorkflowRun(any());
    }

    /* ============================================================
     * run — actionStep.run returns null
     * ============================================================ */
    @Nested
    class WhenRunReturnsNull {

      @Test
      void shouldEndStepOnly_whenStepReadyExecutionFailed() throws ChainingException {
        // -------- Prepare --------
        Step stepReady = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep actionStep = mock(ActionStep.class);

        when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
            .thenReturn(actionStep);

        // -------- Act --------
        stepService.run(stepReady);

        // -------- Assert --------
        verify(stepReady).setStatus(StepStatus.END);
        verify(stepRepository).save(stepReady);

        verify(workflowRun, never()).setStatus(any());
        verify(workflowService, never()).saveWorkflowRun(any());
      }

      /*COMMENTED cause workflow end should be handle after implementation of delay by DB for step template delayed.
      @Test
        void shouldEndStepAndWorkflow_whenNoRunningStepsRemain() {
          // -------- Prepare --------
          Step stepReady = mock(Step.class);
          Workflow workflowRun = mock(Workflow.class);
          ActionStep actionStep = mock(ActionStep.class);

      String workflowRunId = UUID.randomUUID().toString();

      when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION)).thenReturn(actionStep);

      when(actionStep.run(stepReady)).thenReturn(null);

      when(stepReady.getWorkflow()).thenReturn(workflowRun);
      when(workflowRun.getId()).thenReturn(workflowRunId);

      when(stepRepository.countRunningStep(workflowRunId)).thenReturn(0);

      // -------- Act --------
      stepService.run(stepReady);

      // -------- Assert --------
      verify(stepReady).setStatus(StepStatus.END);
      verify(stepRepository).save(stepReady);

      verify(stepRepository).countRunningStep(workflowRunId);

          verify(workflowRun).setStatus(WorkflowStatus.END);
          verify(workflowService).saveWorkflowRun(workflowRun);
        }*/
    }

    /* ============================================================
     * run — actionStep.run returns a step
     * ============================================================ */
    @Test
    void shouldSetRunStatusAndSaveStep_whenRunReturnsStep() throws ChainingException {
      // -------- Prepare --------
      Step stepReady = mock(Step.class);
      Step stepRun = mock(Step.class);
      ActionStep actionStep = mock(ActionStep.class);

      when(stepReady.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
          .thenReturn(actionStep);

      when(actionStep.run(stepReady)).thenReturn(Optional.ofNullable(stepRun));

      // -------- Act --------
      stepService.run(stepReady);

      // -------- Assert --------
      verify(stepRun).setStatus(StepStatus.RUN);
      assertNotNull(stepRun);
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
    void shouldReturnInjectExecutionStep_whenActionIsInjectExecution() throws ChainingException {
      // -------- Act --------
      ActionStep result = stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null);

      // -------- Assert --------
      assertSame(injectExecutionStep, result);
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

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.ofNullable(step));

      // -------- Act --------
      Step result = stepService.findStepTemplateById(stepId);

      // -------- Assert --------
      assertSame(step, result);

      verify(stepRepository).findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE);
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

      when(stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, StepStatus.READY))
          .thenReturn(step);

      // -------- Act --------
      Step result = stepService.findStepReadyById(stepId);

      // -------- Assert --------
      assertSame(step, result);

      verify(stepRepository).findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, StepStatus.READY);
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
      Optional<Step> resultOpt = Optional.ofNullable(stepService.findById(stepId));

      // -------- Assert --------
      assertTrue(resultOpt.isPresent());
      assertSame(step, resultOpt.get());

      verify(stepRepository).findById(stepId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldThrow_whenFindByIdReturnsEmpty() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();

      when(stepRepository.findById(stepId)).thenReturn(Optional.empty());

      // -------- Act + Assert --------
      assertThrows(ElementNotFoundException.class, () -> stepService.findById(stepId));

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
      String result = stepService.findStepIdByInjectId(injectId);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(stepId, result);

      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void shouldThrowAnException_whenNoStepFoundForInjectId() {
      // -------- Prepare --------
      String injectId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.empty());

      // -------- Act --------
      // -------- Assert --------
      assertThrows(
          ElementNotFoundException.class, () -> stepService.findStepIdByInjectId(injectId));

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
        if (stepFound)
          // Avoid executing real run(...) logic
          doNothing().when(stepService).run(any(Step.class));

        // -------- Act --------
        stepService.handleReadyStepEvent(event);

        // -------- Assert --------
        verify(stepRepository).findById(stepId);

        if (stepFound) {
          verify(stepService).run(step);
        }
      }

      static Stream<Arguments> readyStepEventScenarios() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
      }
    }

    /* ============================================================
     * processDelayStep — find next queued step then ready(...)
     * ============================================================ */
    @Nested
    class ProcessDelayStep {

      @ParameterizedTest(name = "{index} => stepFound={0}, throwException={1}")
      @MethodSource("delayStepEventScenarios")
      void shouldReadyOnlyWhenStepExists(boolean stepFound, boolean throwException)
          throws ChainingException, JobExecutionException {
        // -------- Prepare --------
        StepDelayQueue stepDelayQueue = mock(StepDelayQueue.class);
        Step step = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);

        when(stepDelayQueueService.popNextToProcess())
            .thenReturn(stepFound ? List.of(stepDelayQueue) : new ArrayList<>());

        if (stepFound) {
          when(stepDelayQueue.getWorkflowRun()).thenReturn(workflowRun);
          when(stepDelayQueue.getStepTemplate()).thenReturn(step);

          if (throwException) {
            doThrow(new ChainingException("error"))
                .when(stepService)
                .ready(any(Step.class), any(Workflow.class), any());
          } else {
            doReturn(Optional.of(mock(Step.class)))
                .when(stepService)
                .ready(any(Step.class), any(Workflow.class), any());
          }
        }

        // -------- Act --------
        queueChainingJob.execute(null);

        // -------- Assert --------
        if (stepFound) {
          verify(stepService).ready(step, workflowRun, null);
        } else {
          verify(stepService, never()).ready(any(), any(), any());
        }
      }

      static Stream<Arguments> delayStepEventScenarios() {
        return Stream.of(
            Arguments.of(true, false), Arguments.of(true, true), Arguments.of(false, false));
      }
    }

    /* ============================================================
     * handleExternalUpdateEvent — update step then compute next steps to ready
     * ============================================================ */
    @Nested
    class HandleExternalUpdateEventSingle {

      @Test
      void shouldEndStepReadyWhenActionStepIsNull() throws ChainingException {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(null);

        doReturn(Optional.of(stepRun))
            .when(stepRepository)
            .findByIdAndStatus(stepRunId, StepStatus.RUN);
        // -------- Act + Assert --------
        stepService.handleExternalUpdateEvent(event);

        verify(stepRepository).findByIdAndStatus(stepRunId, StepStatus.RUN);
        verify(stepService, never()).ready(any(), any(), any());
        verify(stepRun).setStatus(StepStatus.END);
      }

      @Test
      void shouldDoNothing_whenUpdateReturnsOptionalEmpty() throws ChainingException {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);

        doReturn(Optional.of(stepRun))
            .when(stepRepository)
            .findByIdAndStatus(stepRunId, StepStatus.RUN);
        ActionStep actionStep = mock(ActionStep.class);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
            .thenReturn(actionStep);

        when(actionStep.update(stepRun)).thenReturn(Optional.empty());

        // -------- Act --------
        stepService.handleExternalUpdateEvent(event);

        // -------- Assert --------
        verify(stepRepository).findByIdAndStatus(stepRunId, StepStatus.RUN);
        verify(actionStep).update(stepRun);

        verify(stepRepository, never()).save(any());
        verify(stepService, never()).ready(any(), any(), any());
        verify(conditionService, never()).findAllConditionsByStepId(anyString());
      }

      @Test
      void shouldSaveUpdatedStep_andReadyNextStepsWhoseConditionsDependOnCurrentTemplate()
          throws ChainingException {
        // -------- Prepare --------
        ExternalUpdateEvent event = mock(ExternalUpdateEvent.class);
        String stepRunId = UUID.randomUUID().toString();
        when(event.getStepId()).thenReturn(stepRunId);

        // Current running step
        Step stepRun = mock(Step.class);
        when(stepRun.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);

        Workflow workflowRun = mock(Workflow.class);
        when(stepRun.getWorkflow()).thenReturn(workflowRun);

        // Its template
        Step stepTemplateOfRun = mock(Step.class);
        String currentTemplateId = UUID.randomUUID().toString();
        when(stepTemplateOfRun.getId()).thenReturn(currentTemplateId);
        when(stepRun.getStepTemplate()).thenReturn(stepTemplateOfRun);

        // findById(...) -> repo.findById(...).orElseThrow()
        when(stepRepository.findByIdAndStatus(stepRunId, StepStatus.RUN))
            .thenReturn(Optional.of(stepRun));

        // actionStep.update(...) returns non-null
        ActionStep actionStep = mock(ActionStep.class);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null))
            .thenReturn(actionStep);

        Step updated = mock(Step.class);
        when(actionStep.update(stepRun)).thenReturn(Optional.ofNullable(updated));

        // saveStep(updated) -> stepRepository.save(updated)
        assertNotNull(updated);
        when(stepRepository.save(updated)).thenReturn(updated);

        // GET STEP TEMPLATE CURRENT (repository delegation)
        Step stepTemplateCurrent = mock(Step.class);
        when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(
                currentTemplateId, StepStatus.TEMPLATE))
            .thenReturn(Optional.ofNullable(stepTemplateCurrent));

        // template workflow + templates list
        Workflow workflowTemplate = mock(Workflow.class);
        String workflowTemplateId = UUID.randomUUID().toString();
        assertNotNull(stepTemplateCurrent);
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

        when(conditionService.findAllConditionsByStepId(tpl1Id)).thenReturn(List.of(condMatch));
        when(conditionService.findAllConditionsByStepId(tpl2Id)).thenReturn(List.of(condNoMatch));

        // Avoid executing real ready(...) behavior; we just want to verify calls
        doReturn(Optional.of(mock(Step.class)))
            .when(stepService)
            .ready(any(Step.class), any(Workflow.class), isNull());

        // -------- Act --------
        stepService.handleExternalUpdateEvent(event);

        // -------- Assert --------
        // updated step saved
        verify(stepRepository).save(updated);

        // template lookup chain
        verify(stepRepository)
            .findByStepTemplateIdIsNullAndIdAndStatus(currentTemplateId, StepStatus.TEMPLATE);
        verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateId);

        // conditions checked
        verify(conditionService).findAllConditionsByStepId(tpl1Id);
        verify(conditionService).findAllConditionsByStepId(tpl2Id);

        // only tpl1 should be readied (because it depends on currentTemplateId)
        verify(stepService).ready(tpl1, workflowRun, null);
        verify(stepService, never()).ready(eq(tpl2), any(), any());
      }
    }
  }

  @Test
  void should_throw_when_stepConditionTemplate_fails() throws Exception {

    String workflowId = "wf-1";
    Workflow workflow = new Workflow();

    StepsCreateInput.StepCreateInput input = mock(StepsCreateInput.StepCreateInput.class);

    Step step = new Step();

    when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
        .thenReturn(workflow);

    doReturn(actionStep).when(stepService).factoryAction(any(), any());

    when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.of(step));

    doThrow(new IllegalArgumentException()).when(stepService).stepConditionTemplate(any(), any());

    assertThrows(
        IllegalArgumentException.class,
        () -> stepService.createStepTemplates(workflowId, List.of(input)));
  }

  @Test
  void should_run_step_successfully() throws Exception {
    Step stepReady = new Step();
    stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);

    Step stepRun = new Step();

    doReturn(actionStep)
        .when(stepService)
        .factoryAction(eq(StepActionClass.INJECT_EXECUTION), any());
    when(actionStep.run(stepReady)).thenReturn(Optional.of(stepRun));
    doReturn(stepRun).when(stepService).saveStep(stepRun);

    stepService.run(stepReady);

    assertEquals(StepStatus.RUN, stepRun.getStatus());
    verify(stepService).saveStep(stepRun);
  }

  @Test
  void should_throw_when_actionStep_is_null_andIdNotNull() {
    Step stepReady = new Step();
    stepReady.setId("Id");
    stepReady.setStepAction(null);

    ChainingException exception =
        assertThrows(
            ChainingException.class,
            () -> stepService.factoryAction(stepReady.getStepAction(), stepReady.getId()));
    assertEquals("Action step is null. Step ID:Id", exception.getMessage());
  }

  @Test
  void should_throw_when_actionStep_is_not_null_and_id_null() {
    Step stepReady = new Step();
    stepReady.setId(null);
    stepReady.setStepAction(null);

    ChainingException exception =
        assertThrows(
            ChainingException.class,
            () -> stepService.factoryAction(stepReady.getStepAction(), stepReady.getId()));
    assertEquals("Action step of new step (TEMPLATE) is null", exception.getMessage());
  }

  @Test
  void should_set_stepReady_to_end_when_run_returns_empty() throws Exception {
    Step stepReady = new Step();
    stepReady.setStepAction(StepActionClass.INJECT_EXECUTION);

    doReturn(actionStep).when(stepService).factoryAction(any(), any());
    doReturn(Optional.empty()).when(actionStep).run(stepReady);
    doReturn(stepReady).when(stepService).saveStep(stepReady);

    stepService.run(stepReady);

    assertEquals(StepStatus.END, stepReady.getStatus());
    verify(stepService).saveStep(stepReady);
  }

  /* ============================================================
   * Helpers
   * ============================================================ */

  private void setupCreateStepTemplates(
      StepsCreateInput.StepCreateInput stepInput, boolean saveCondition) throws ChainingException {

    Workflow workflow = mock(Workflow.class);
    Step step = mock(Step.class);
    ActionStep actionStep = mock(ActionStep.class);

    when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
        .thenReturn(workflow);

    when(stepService.factoryAction(stepInput.getStepAction(), null)).thenReturn(actionStep);

    when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.ofNullable(step));

    assertNotNull(step);
    when(stepRepository.save(step)).thenReturn(step);

    if (saveCondition)
      when(conditionService.saveCondition(any())).thenAnswer(i -> i.getArgument(0));
  }

  private StepsCreateInput.StepCreateInput mockStep(
      StepActionClass actionClass, List<ConditionCreateInput> conditions) {

    StepsCreateInput.StepCreateInput step = mock(StepsCreateInput.StepCreateInput.class);

    when(step.getStepAction()).thenReturn(actionClass);
    if (!conditions.isEmpty()) when(step.getConditions()).thenReturn(conditions);

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
