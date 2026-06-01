package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepDelayQueueRepository;
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
class StepServiceTest {

  @Mock private StepRepository stepRepository;
  @Mock private InjectExecutionStep injectExecutionStep;
  @Mock private ActionStep actionStep;
  @Mock private WorkflowService workflowService;
  @Mock private ConditionService conditionService;
  @Mock private QueueChainingService queueChainingService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Mock private StepDelayQueueRepository stepDelayQueueRepository;

  @Spy @InjectMocks StepService stepService;
  private QueueChainingJob queueChainingJob;

  private Workflow workflow;

  @Captor private ArgumentCaptor<String> simulationIdCaptor;
  @Captor private ArgumentCaptor<String> workflowTemplateIdCaptor;
  @Captor private ArgumentCaptor<Step> stepCaptor;
  @Captor private ArgumentCaptor<Workflow> workflowCaptor;
  @Captor private ArgumentCaptor<List<Condition>> conditionsCaptor;
  @Captor private ArgumentCaptor<String> stepIdCaptor;

  @BeforeEach
  void setUp() {
    queueChainingJob = new QueueChainingJob(stepDelayQueueService, stepService);
    workflow = mock(Workflow.class);
  }

  /* ============================================================
   * createStepsTemplate — ActionStep resolution
   * ============================================================ */
  @Nested
  class ActionStepResolution {

    @Test
    void given_nullActionStep_should_throwChainingException() {
      // Arrange
      StepsCreateInput.StepInput stepInput = mockStep(null, List.of());

      // Act + Assert
      assertThrows(
          ChainingException.class,
          () -> stepService.createStepTemplates(workflow, List.of(stepInput)));

      verify(conditionService, never()).saveCondition(any());
    }
  }

  /* ============================================================
   * stepCondition — no conditions
   * ============================================================ */
  @Nested
  class NoConditions {

    @Test
    void given_emptyConditions_should_skipConditionCreation() throws ChainingException {
      // Arrange
      StepsCreateInput.StepInput stepInput =
          mockStep(StepActionClass.INJECT_EXECUTION, Collections.emptyList());

      setupCreateStepTemplates(stepInput);

      // Act
      stepService.createStepTemplates(workflow, List.of(stepInput));

      // Assert
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
    void given_conditionInputs_should_buildConditionTreeCorrectly(
        String description,
        List<ConditionCreateInput> inputs,
        Map<ConditionKeyType, Optional<ConditionKeyType>> expectedParentMap)
        throws ChainingException {

      // Arrange
      StepsCreateInput.StepInput stepInput = mockStep(StepActionClass.INJECT_EXECUTION, inputs);

      setupCreateStepTemplates(stepInput);

      List<Condition> producedConditions = new ArrayList<>();

      doAnswer(
              invocation -> {
                @SuppressWarnings("unchecked")
                List<ConditionCreateInput> conditionInputs = invocation.getArgument(0);
                java.util.function.Function<ConditionCreateInput, Condition> rootFactory =
                    invocation.getArgument(1);
                java.util.function.BiFunction<ConditionCreateInput, Condition, Condition>
                    childFactory = invocation.getArgument(2);

                ConditionCreateInput rootInput =
                    conditionInputs.stream()
                        .filter(c -> c.getTemporaryIdConditionParent() == null)
                        .findFirst()
                        .orElseThrow();

                Condition root = rootFactory.apply(rootInput);
                producedConditions.add(root);

                Map<String, Condition> byTmpId = new HashMap<>();
                byTmpId.put(rootInput.getTemporaryId(), root);

                Map<String, List<ConditionCreateInput>> childrenByParent =
                    conditionInputs.stream()
                        .filter(c -> c.getTemporaryIdConditionParent() != null)
                        .collect(
                            java.util.stream.Collectors.groupingBy(
                                ConditionCreateInput::getTemporaryIdConditionParent));

                java.util.Queue<String> queue = new java.util.LinkedList<>();
                queue.add(rootInput.getTemporaryId());

                while (!queue.isEmpty()) {
                  String cur = queue.poll();
                  for (ConditionCreateInput childInput :
                      childrenByParent.getOrDefault(cur, List.of())) {
                    Condition parent = byTmpId.get(childInput.getTemporaryIdConditionParent());
                    Condition child = childFactory.apply(childInput, parent);
                    producedConditions.add(child);
                    byTmpId.put(childInput.getTemporaryId(), child);
                    queue.add(childInput.getTemporaryId());
                  }
                }
                return null;
              })
          .when(conditionService)
          .createConditionTree(any(), any(), any(), any(), isNull());

      // Act
      stepService.createStepTemplates(workflow, List.of(stepInput));

      // Assert
      verify(conditionService).createConditionTree(eq(inputs), any(), any(), any(), isNull());

      Map<ConditionKeyType, Condition> byKey =
          producedConditions.stream().collect(Collectors.toMap(Condition::getKeyType, c -> c));

      expectedParentMap.forEach(
          (childKey, parentKey) -> {
            Condition child = byKey.get(childKey);
            assertNotNull(child, "Condition not found for key: " + childKey);
            if (parentKey.isEmpty()) {
              assertNull(child.getConditionParent(), "Expected no parent for: " + childKey);
            } else {
              assertEquals(
                  byKey.get(parentKey.get()),
                  child.getConditionParent(),
                  "Wrong parent for: " + childKey);
            }
          });
    }

    static Stream<Arguments> conditionTreeTestInputs() {
      return Stream.of(
          Arguments.of(
              "Single root condition",
              List.of(mockCondition("ROOT", ConditionKeyType.Text, null)),
              Map.of(ConditionKeyType.Text, Optional.empty())),
          Arguments.of(
              "Root with one child",
              List.of(
                  mockCondition("ROOT", ConditionKeyType.Text, null),
                  mockCondition("CHILD", ConditionKeyType.Number, "ROOT")),
              Map.of(
                  ConditionKeyType.Text, Optional.empty(),
                  ConditionKeyType.Number, Optional.of(ConditionKeyType.Text))),
          Arguments.of(
              "Root with two-level tree",
              List.of(
                  mockCondition("ROOT", ConditionKeyType.Text, null),
                  mockCondition("A", ConditionKeyType.Port, "ROOT"),
                  mockCondition("B", ConditionKeyType.IPv4, "A")),
              Map.of(
                  ConditionKeyType.Text, Optional.empty(),
                  ConditionKeyType.Port, Optional.of(ConditionKeyType.Text),
                  ConditionKeyType.IPv4, Optional.of(ConditionKeyType.Port))));
    }
  }

  /* ============================================================
   * stepCondition — invalid trees
   * ============================================================ */
  @Nested
  class InvalidConditionTrees {

    @Test
    void given_multipleRootConditions_should_throw() throws ChainingException {
      // Arrange
      StepsCreateInput.StepInput stepInput =
          mockStep(
              StepActionClass.INJECT_EXECUTION,
              List.of(
                  ConditionCreateInput.builder()
                      .keyType(ConditionKeyType.Text)
                      .temporaryIdConditionParent(null)
                      .build(),
                  ConditionCreateInput.builder()
                      .keyType(ConditionKeyType.Number)
                      .temporaryIdConditionParent(null)
                      .build()));

      setupCreateStepTemplates(stepInput);

      doThrow(
              new IllegalArgumentException(
                  "New step (TEMPLATE): Only 1 condition can be first parent"))
          .when(conditionService)
          .createConditionTree(any(), any(), any(), any(), isNull());

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflow, List.of(stepInput)));
    }

    @Test
    void given_noRootCondition_should_throw() throws ChainingException {
      // Arrange
      ConditionCreateInput conditionCreateInput =
          ConditionCreateInput.builder()
              .keyType(ConditionKeyType.Text)
              .temporaryIdConditionParent("X")
              .build();
      StepsCreateInput.StepInput stepInput =
          mockStep(StepActionClass.INJECT_EXECUTION, List.of(conditionCreateInput));

      Step step = mock(Step.class);

      when(stepService.factoryAction(stepInput.getStepAction(), null)).thenReturn(actionStep);

      when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.ofNullable(step));

      assertNotNull(step);
      when(stepRepository.save(step)).thenReturn(step);

      doThrow(
              new IllegalArgumentException(
                  "New step (TEMPLATE): Only 1 condition can be first parent"))
          .when(conditionService)
          .createConditionTree(any(), any(), any(), any(), isNull());

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflow, List.of(stepInput)));
    }
  }

  /* ============================================================
   * ready — Execution step creation and queue chaining
   * ============================================================ */
  @Nested
  class Ready {

    @Nested
    class ActionStepResolution {

      @Test
      void given_nullAction_should_throw() throws Exception {
        // Arrange
        Step nextStepTemplateToExecute = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);

        when(nextStepTemplateToExecute.getStepAction()).thenReturn(null);

        // Act + Assert
        assertThrows(
            ChainingException.class,
            () ->
                stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, "{\"a\":1}"));

        verify(stepRepository, never()).findById(anyString());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).checkCondition(any(), any(), any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }

    @Nested
    class ConditionOutcomes {

      @Test
      void given_nullConditionExecution_should_returnEmptyList() throws Exception {
        // Arrange
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep localActionStep = mock(ActionStep.class);

        String input = "{\"hello\":\"world\"}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction())
            .thenReturn(StepActionClass.INJECT_EXECUTION);
        doReturn(localActionStep)
            .when(stepService)
            .factoryAction(StepActionClass.INJECT_EXECUTION, stepId);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(null);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input);

        // Assert
        assertTrue(result.isEmpty());

        verify(stepRepository).findByIdAndStatus(stepIdCaptor.capture(), any());
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(conditionService).checkCondition(persistedTemplate, workflowRun, input);

        verify(localActionStep, never()).ready(any(), any(), any());
        verify(stepRepository, never()).save(any());
        verify(conditionService, never()).saveAllConditions(anyList());
        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }

    @Nested
    class ReadyNominalCase {

      @Test
      void given_validConditions_should_createReadyStep_andSaveConditions() throws Exception {
        // Arrange
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep localActionStep = mock(ActionStep.class);

        String input = "{\"x\":1}";
        String stepId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getStepAction())
            .thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(localActionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);

        Condition c1 = mock(Condition.class);
        Condition c2 = mock(Condition.class);
        List<Condition> usedMappers = new ArrayList<>(List.of(c1, c2));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(List.of(new ConditionService.ExecutionBatch(input, usedMappers)));

        Step stepReady = mock(Step.class);

        when(localActionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);
        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        when(stepRepository.findByIdAndStatus(any(), eq(StepStatus.TEMPLATE)))
            .thenReturn(Optional.of(persistedTemplate));

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input);

        // Assert
        assertEquals(1, result.size());
        assertSame(stepReady, result.getFirst());

        verify(stepRepository).findByIdAndStatus(stepIdCaptor.capture(), eq(StepStatus.TEMPLATE));
        assertEquals(stepId, stepIdCaptor.getValue());

        verify(localActionStep).ready(persistedTemplate, input, workflowRun);

        verify(stepRepository).save(stepCaptor.capture());
        assertSame(stepReady, stepCaptor.getValue());

        verify(conditionService).saveAllConditions(conditionsCaptor.capture());
        assertEquals(2, conditionsCaptor.getValue().size());
        assertTrue(conditionsCaptor.getValue().containsAll(List.of(c1, c2)));

        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }

    @Nested
    class QueueChainingDelegation {

      @Test
      void given_readyStep_should_notQueueInsideReady() throws Exception {
        // Arrange
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep localActionStep = mock(ActionStep.class);

        String input = "{\"q\":true}";
        String stepId = UUID.randomUUID().toString();
        when(nextStepTemplateToExecute.getStepAction())
            .thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(localActionStep);

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        Condition c1 = mock(Condition.class);
        List<Condition> usedMappers = new ArrayList<>(List.of(c1));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(List.of(new ConditionService.ExecutionBatch(input, usedMappers)));

        Step stepReady = mock(Step.class);

        when(localActionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);

        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input);

        // Assert
        assertEquals(1, result.size());
        assertSame(stepReady, result.getFirst());

        verify(localActionStep).ready(persistedTemplate, input, workflowRun);
        verify(stepRepository, times(1)).save(stepReady);

        verify(conditionService).saveAllConditions(anyList());

        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }
  }

  /* ============================================================
   * queueReadySteps — Queue pushing and exception handling
   * ============================================================ */
  @Nested
  class QueueReadySteps {

    @Test
    void given_readySteps_should_queueAll() throws Exception {
      // Arrange
      Step stepReady1 = mock(Step.class);
      Step stepReady2 = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      // Act
      stepService.enqueueReadySteps(List.of(stepReady1, stepReady2), workflowRun);

      // Assert
      verify(queueChainingService).readyStep(stepReady1, workflowRun);
      verify(queueChainingService).readyStep(stepReady2, workflowRun);
    }

    @Test
    void given_ioException_should_endStepAndWrapException() throws Exception {
      // Arrange
      Step stepReady = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      when(stepReady.getId()).thenReturn(UUID.randomUUID().toString());
      doThrow(new IOException("boom")).when(queueChainingService).readyStep(stepReady, workflowRun);

      // Act + Assert
      ChainingException ex =
          assertThrows(
              ChainingException.class,
              () -> stepService.enqueueReadySteps(List.of(stepReady), workflowRun));

      assertInstanceOf(IOException.class, ex.getCause());
      verify(stepReady).setStatus(StepStatus.END);
      verify(stepRepository).save(stepReady);
    }
  }

  /* ============================================================
   * countExecutedStep — Repository delegation
   * ============================================================ */
  @Nested
  class CountExecutedStep {

    @Test
    void given_workflowAndTemplate_should_returnRepositoryCount() {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      String stepTemplateId = UUID.randomUUID().toString();
      int expected = 42;

      when(stepRepository.countStepExecutedByStepTemplateIdAndWorkflowRunId(
              workflowRunId, stepTemplateId))
          .thenReturn(expected);

      // Act
      int result = stepService.countExecutedStep(workflowRunId, stepTemplateId);

      // Assert
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
    void given_injectExecution_should_returnInjectExecutionStep() throws ChainingException {
      // Act
      ActionStep result = stepService.factoryAction(StepActionClass.INJECT_EXECUTION, null);

      // Assert
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
    void given_steps_should_callSaveAll() {
      // Arrange
      Step s1 = mock(Step.class);
      Step s2 = mock(Step.class);
      List<Step> steps = List.of(s1, s2);

      // Act
      stepService.saveSteps(steps);

      // Assert
      verify(stepRepository).saveAll(stepsCaptor.capture());
      assertSame(steps, stepsCaptor.getValue());
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_step_should_saveAndReturnSavedInstance() {
      // Arrange
      Step step = mock(Step.class);
      Step saved = mock(Step.class);

      when(stepRepository.save(step)).thenReturn(saved);

      // Act
      Step result = stepService.saveStep(step);

      // Assert
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
    void given_validId_should_findStepTemplateById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.ofNullable(step));

      // Act
      Step result = stepService.findStepTemplateById(stepId);

      // Assert
      assertSame(step, result);
      verify(stepRepository).findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_workflowId_should_findAllStepTemplateByWorkflow() {
      // Arrange
      String wfId = UUID.randomUUID().toString();
      List<Step> steps = List.of(mock(Step.class), mock(Step.class));

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(wfId)).thenReturn(steps);

      // Act
      List<Step> result = stepService.findAllStepTemplateByWorkflow(wfId);

      // Assert
      assertSame(steps, result);
      verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(wfId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_validId_should_findStepReadyById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, StepStatus.READY))
          .thenReturn(step);

      // Act
      Step result = stepService.findStepReadyById(stepId);

      // Assert
      assertSame(step, result);
      verify(stepRepository).findByStepTemplateIdIsNotNullAndIdAndStatus(stepId, StepStatus.READY);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_templateAndWorkflow_should_findAllExecutedSteps() {
      // Arrange
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowRunId = UUID.randomUUID().toString();

      List<Step> steps = List.of(mock(Step.class), mock(Step.class));

      when(stepRepository.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
              stepTemplateId, workflowRunId))
          .thenReturn(steps);

      // Act
      List<Step> result =
          stepService.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
              stepTemplateId, workflowRunId);

      // Assert
      assertSame(steps, result);
      verify(stepRepository)
          .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(stepTemplateId, workflowRunId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_existingId_should_findById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      Step step = mock(Step.class);

      when(stepRepository.findById(stepId)).thenReturn(Optional.of(step));

      // Act
      Optional<Step> resultOpt = Optional.ofNullable(stepService.findById(stepId));

      // Assert
      assertTrue(resultOpt.isPresent());
      assertSame(step, resultOpt.get());
      verify(stepRepository).findById(stepId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_missingId_should_throwWhenFindById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();

      when(stepRepository.findById(stepId)).thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(ElementNotFoundException.class, () -> stepService.findById(stepId));
      verify(stepRepository).findById(stepId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_injectId_should_findStepId() {
      // Arrange
      String injectId = UUID.randomUUID().toString();
      String stepId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.of(stepId));

      // Act
      String result = stepService.findStepIdByInjectId(injectId);

      // Assert
      assertNotNull(result);
      assertEquals(stepId, result);
      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_missingInjectId_should_throw() {
      // Arrange
      String injectId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(
          ElementNotFoundException.class, () -> stepService.findStepIdByInjectId(injectId));
      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }
  }

  /* ============================================================
   * Queue events handling — processDelayStep
   * ============================================================ */
  @Nested
  class QueueEventsHandling {

    @Nested
    class ProcessDelayStep {

      @ParameterizedTest(name = "{index} => stepFound={0}, throwException={1}")
      @MethodSource("delayStepEventScenarios")
      void given_delayStepEvent_should_readyOnlyWhenStepExists(
          boolean stepFound, boolean throwException)
          throws ChainingException, JobExecutionException {
        // Arrange
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
                .createReadySteps(any(Step.class), any(Workflow.class), any());
          } else {
            doReturn(List.of(mock(Step.class)))
                .when(stepService)
                .createReadySteps(any(Step.class), any(Workflow.class), any());
          }
        }

        // Act
        queueChainingJob.execute(null);

        // Assert
        if (stepFound) {
          verify(stepService).createReadySteps(step, workflowRun, null);
        } else {
          verify(stepService, never()).createReadySteps(any(), any(), any());
        }
      }

      static Stream<Arguments> delayStepEventScenarios() {
        return Stream.of(
            Arguments.of(true, false), Arguments.of(true, true), Arguments.of(false, false));
      }
    }
  }

  @Test
  void given_stepConditionTemplateFailure_should_throw() throws Exception {
    // Arrange
    Workflow localWorkflow = new Workflow();

    StepsCreateInput.StepInput input = mock(StepsCreateInput.StepInput.class);

    Step step = new Step();

    doReturn(actionStep).when(stepService).factoryAction(any(), any());

    when(actionStep.create(any(), eq(localWorkflow))).thenReturn(Optional.of(step));

    when(stepRepository.save(step)).thenReturn(step);

    doThrow(new IllegalArgumentException())
        .when(stepService)
        .stepConditionTemplate(any(), any(), any());

    // Act + Assert
    assertThrows(
        IllegalArgumentException.class,
        () -> stepService.createStepTemplates(localWorkflow, List.of(input)));
  }

  /* ============================================================
   * Helpers
   * ============================================================ */

  private void setupCreateStepTemplates(StepsCreateInput.StepInput stepInput)
      throws ChainingException {

    Step step = mock(Step.class);

    when(stepService.factoryAction(stepInput.getStepAction(), null)).thenReturn(actionStep);

    when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.ofNullable(step));

    assertNotNull(step);
    when(stepRepository.save(step)).thenReturn(step);
  }

  private StepsCreateInput.StepInput mockStep(
      StepActionClass actionClass, List<ConditionCreateInput> conditions) {

    StepsCreateInput.StepInput step = mock(StepsCreateInput.StepInput.class);

    when(step.getStepAction()).thenReturn(actionClass);
    if (!conditions.isEmpty()) {
      when(step.getConditions()).thenReturn(conditions);
    }

    return step;
  }

  private static ConditionCreateInput mockCondition(
      String temporaryId, ConditionKeyType keyType, String parentTempId) {

    ConditionCreateInput c = mock(ConditionCreateInput.class);

    when(c.getKeyType()).thenReturn(keyType);
    when(c.getTemporaryId()).thenReturn(temporaryId);
    when(c.getTemporaryIdConditionParent()).thenReturn(parentTempId);

    return c;
  }

  /* ============================================================
   * StepTemplate CRUD
   * ============================================================ */
  @Nested
  class StepTemplateCrud {

    @Test
    void given_validInput_should_createStepTemplate_andLinkConditions() throws ChainingException {
      // Arrange
      StepsCreateInput.StepInput stepInput = mock(StepsCreateInput.StepInput.class);
      Workflow localWorkflow = mock(Workflow.class);
      Step created = mock(Step.class);
      List<String> conditionIds = List.of("cond-1", "cond-2");

      when(stepInput.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepInput.getConditions()).thenReturn(Collections.emptyList());
      when(stepInput.getConditionIds()).thenReturn(conditionIds);
      doReturn(actionStep).when(stepService).factoryAction(StepActionClass.INJECT_EXECUTION, null);
      when(actionStep.create(stepInput, localWorkflow)).thenReturn(Optional.of(created));
      when(stepRepository.save(created)).thenReturn(created);

      // Act
      Step result = stepService.createStepTemplate(localWorkflow, stepInput);

      // Assert
      assertSame(created, result);
      verify(conditionService).linkExistingConditionsToStep(created, conditionIds);
      verify(stepRepository).save(created);
    }

    @Test
    void given_existingStep_should_updateStepTemplate_andRebuildConditions()
        throws ChainingException {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      StepInput stepInput = mock(StepInput.class);
      Step existing = new Step();
      Workflow existingWorkflow = new Workflow();
      existing.setWorkflow(existingWorkflow);

      Step candidate = new Step();
      candidate.setStepAction(StepActionClass.INJECT_EXECUTION);
      candidate.setLimitExecution(5);
      candidate.setData("{\"updated\":true}");
      candidate.setInput("{}");
      candidate.setOutputParser("{}");

      when(stepInput.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepInput.getConditions()).thenReturn(Collections.emptyList());
      when(stepInput.getConditionIds()).thenReturn(List.of("cond-x"));
      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.of(existing));
      doReturn(actionStep)
          .when(stepService)
          .factoryAction(StepActionClass.INJECT_EXECUTION, stepId);
      when(actionStep.create(any(StepsCreateInput.StepInput.class), eq(existingWorkflow)))
          .thenReturn(Optional.of(candidate));
      when(stepRepository.save(existing)).thenReturn(existing);

      // Act
      Step updated = stepService.updateStepTemplate(stepId, stepInput);

      // Assert
      assertSame(existing, updated);
      assertEquals(5, updated.getLimitExecution());
      assertEquals("{\"updated\":true}", updated.getData());
      verify(conditionService).deleteAllConditionsByStepId(stepId, List.of("cond-x"));
      verify(conditionService).linkExistingConditionsToStep(existing, List.of("cond-x"));
      verify(stepRepository).save(existing);
    }

    @Test
    void given_stepId_should_deleteStepTemplate_andDeleteConditions() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      Step template = new Step();

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.of(template));

      // Act
      stepService.deleteStepTemplate(stepId);

      // Assert
      verify(conditionService).deleteAllConditionsByStepId(stepId);
      verify(stepRepository).delete(template);
    }

    @Test
    void given_stepId_should_findStepTemplateById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      Step template = new Step();

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.of(template));

      // Act
      Step result = stepService.findStepTemplateById(stepId);

      // Assert
      assertSame(template, result);
    }

    @Test
    void given_missingStepId_should_throwWhenFindStepTemplateById() {
      // Arrange
      String stepId = UUID.randomUUID().toString();
      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      // Act + Assert
      assertThrows(ElementNotFoundException.class, () -> stepService.findStepTemplateById(stepId));
    }

    @Test
    void given_workflowId_should_findAllStepTemplateByWorkflow() {
      // Arrange
      String wfId = UUID.randomUUID().toString();
      List<Step> expected = List.of(new Step(), new Step());

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(wfId)).thenReturn(expected);

      // Act
      List<Step> result = stepService.findAllStepTemplateByWorkflow(wfId);

      // Assert
      assertSame(expected, result);
      verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(wfId);
    }

    @Test
    void given_mixedSteps_should_findAllStepTemplates_onlyTemplateRows() {
      // Arrange
      Step templateA = new Step();
      Step templateB = new Step();
      Step executed = new Step();
      templateA.setId("tA");
      templateB.setId("tB");
      executed.setId("exec");
      executed.setStepTemplate(new Step());

      when(stepRepository.findAll()).thenReturn(List.of(templateA, executed, templateB));

      // Act
      List<Step> result = stepService.findAllStepTemplates();

      // Assert
      assertEquals(2, result.size());
      assertTrue(result.contains(templateA));
      assertTrue(result.contains(templateB));
      assertFalse(result.contains(executed));
    }
  }
}
