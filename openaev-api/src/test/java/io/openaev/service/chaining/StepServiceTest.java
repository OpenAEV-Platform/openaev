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
import io.openaev.database.model.ConditionKeyType;
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
public class StepServiceTest {

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
      StepsCreateInput.StepInput stepInput = mockStep(null, List.of());

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
      StepsCreateInput.StepInput stepInput =
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
        Map<ConditionKeyType, Optional<ConditionKeyType>> expectedParentMap)
        throws ChainingException {

      StepsCreateInput.StepInput stepInput = mockStep(StepActionClass.INJECT_EXECUTION, inputs);

      setupCreateStepTemplates(stepInput, false);

      // Capture the arguments passed to createConditionTree and invoke the real BFS logic
      // by delegating to ConditionService#findRootConditionInput so we can assert parent linkage.
      // We use thenAnswer to exercise the factories and collect produced Conditions.
      List<Condition> producedConditions = new ArrayList<>();

      doAnswer(
              invocation -> {
                // arg 0 : conditionInputs
                // arg 1 : rootFactory   Function<ConditionCreateInput, Condition>
                // arg 2 : childFactory  BiFunction<ConditionCreateInput, Condition, Condition>
                // arg 3 : linkCondition BiConsumer<Condition, Boolean>
                // arg 4 : afterRootSaved Consumer<Condition>
                @SuppressWarnings("unchecked")
                List<ConditionCreateInput> conditionInputs = invocation.getArgument(0);
                java.util.function.Function<ConditionCreateInput, Condition> rootFactory =
                    invocation.getArgument(1);
                java.util.function.BiFunction<ConditionCreateInput, Condition, Condition>
                    childFactory = invocation.getArgument(2);

                // Identify root (no parent)
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

      stepService.createStepTemplates(workflowId, List.of(stepInput));

      // Verify createConditionTree was called once with the right inputs
      verify(conditionService).createConditionTree(eq(inputs), any(), any(), any(), isNull());

      // Build a key -> Condition map from the produced conditions
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
              List.of(mockCondition("ROOT", ConditionKeyType.TEXT, null)),
              Map.of(ConditionKeyType.TEXT, Optional.empty())),
          Arguments.of(
              "Root with one child",
              List.of(
                  mockCondition("ROOT", ConditionKeyType.TEXT, null),
                  mockCondition("CHILD", ConditionKeyType.NUMBER, "ROOT")),
              Map.of(
                  ConditionKeyType.TEXT, Optional.empty(),
                  ConditionKeyType.NUMBER, Optional.of(ConditionKeyType.TEXT))),
          Arguments.of(
              "Root with two-level tree",
              List.of(
                  mockCondition("ROOT", ConditionKeyType.TEXT, null),
                  mockCondition("A", ConditionKeyType.PORT, "ROOT"),
                  mockCondition("B", ConditionKeyType.IPV4, "A")),
              Map.of(
                  ConditionKeyType.TEXT, Optional.empty(),
                  ConditionKeyType.PORT, Optional.of(ConditionKeyType.TEXT),
                  ConditionKeyType.IPV4, Optional.of(ConditionKeyType.PORT))));
    }
  }

  /* ============================================================
   * stepCondition — invalid trees
   * ============================================================ */
  @Nested
  class InvalidConditionTrees {

    @Test
    void shouldThrowWhenMultipleRootConditions() throws ChainingException {
      // Arrange
      StepsCreateInput.StepInput stepInput =
          mockStep(
              StepActionClass.INJECT_EXECUTION,
              List.of(
                  ConditionCreateInput.builder()
                      .keyType(ConditionKeyType.TEXT)
                      .temporaryIdConditionParent(null)
                      .build(),
                  ConditionCreateInput.builder()
                      .keyType(ConditionKeyType.NUMBER)
                      .temporaryIdConditionParent(null)
                      .build()));

      setupCreateStepTemplates(stepInput, false);

      // conditionService is mocked: configure it to throw the same exception as the real impl
      doThrow(
              new IllegalArgumentException(
                  "New step (TEMPLATE): Only 1 condition can be first parent"))
          .when(conditionService)
          .createConditionTree(any(), any(), any(), any(), isNull());

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> stepService.createStepTemplates(workflowId, List.of(stepInput)));
    }

    @Test
    void shouldThrowWhenNoRootConditionExists() throws ChainingException {
      // Arrange
      ConditionCreateInput conditionCreateInput =
          ConditionCreateInput.builder()
              .keyType(ConditionKeyType.TEXT)
              .temporaryIdConditionParent("X")
              .build();
      StepsCreateInput.StepInput stepInput =
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

      // conditionService is mocked: configure it to throw the same exception as the real impl
      doThrow(
              new IllegalArgumentException(
                  "New step (TEMPLATE): Only 1 condition can be first parent"))
          .when(conditionService)
          .createConditionTree(any(), any(), any(), any(), isNull());

      // Act + Assert
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

      when(workflowService.launchWorkflowSimulation(workflowTemplate)).thenReturn(workflowRun);
      when(workflowService.findWorkflowTemplateBySimulationId(simulationId))
          .thenReturn(Optional.of(workflowTemplate));

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(Collections.emptyList());

      // -------- Act --------
      stepService.startWorkflowBySimulationId(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateBySimulationId(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflowSimulation(workflowCaptor.capture());
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
      when(workflowService.launchWorkflowSimulation(workflowTemplate)).thenReturn(workflowRun);

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
      stepService.startWorkflowBySimulationId(simulationId);

      // -------- Assert --------
      verify(workflowService).findWorkflowTemplateBySimulationId(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());

      verify(stepRepository)
          .findAllByStepTemplateIdIsNullAndWorkflowId(workflowTemplateIdCaptor.capture());
      assertEquals(templateWorkflowId, workflowTemplateIdCaptor.getValue());

      verify(workflowService).launchWorkflowSimulation(workflowTemplate);
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

    @ParameterizedTest(name = "validStep={0}, delayedTemplate={1} -> endExpected={2}")
    @MethodSource("workflowStatusMatrixInputs")
    void shouldSetWorkflowStatusAccordingToValidStepAndDelayedTemplate(
        boolean hasValidStep, boolean hasDelayedTemplate, boolean shouldEndWorkflow)
        throws ChainingException {
      // -------- Prepare --------
      String simulationId = UUID.randomUUID().toString();
      String templateWorkflowId = UUID.randomUUID().toString();

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.getId()).thenReturn(templateWorkflowId);

      Workflow workflowRun = mock(Workflow.class);
      Step stepTemplate = mock(Step.class);

      when(workflowService.findWorkflowTemplateBySimulationId(simulationId))
          .thenReturn(Optional.of(workflowTemplate));
      when(workflowService.launchWorkflowSimulation(workflowTemplate)).thenReturn(workflowRun);
      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(templateWorkflowId))
          .thenReturn(List.of(stepTemplate));

      doReturn(hasValidStep ? Optional.of(mock(Step.class)) : Optional.empty())
          .when(stepService)
          .ready(stepTemplate, workflowRun, null);

      if (!hasValidStep) {
        when(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun))
            .thenReturn(hasDelayedTemplate ? List.of(mock(StepDelayQueue.class)) : List.of());
      }

      // -------- Act --------
      stepService.startWorkflowBySimulationId(simulationId);

      // -------- Assert --------
      if (shouldEndWorkflow) {
        verify(workflowRun).setStatus(WorkflowStatus.END);
      } else {
        verify(workflowRun, never()).setStatus(WorkflowStatus.END);
      }

      if (hasValidStep) {
        verify(stepDelayQueueRepository, never()).findAllByWorkflowRun(workflowRun);
      } else {
        verify(stepDelayQueueRepository).findAllByWorkflowRun(workflowRun);
      }
    }

    private static Stream<Arguments> workflowStatusMatrixInputs() {
      return Stream.of(
          // no step valid && no delayed template -> END
          Arguments.of(false, false, true),
          // no step valid && delayed template exists -> RUN
          Arguments.of(false, true, false),
          // step valid && no delayed template -> RUN
          Arguments.of(true, false, false),
          // step valid && delayed template exists -> RUN
          Arguments.of(true, true, false));
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
        // TODO:update this check
        //        verify(c1).linkToStep(stepReady, true);
        //        verify(c2).linkToStep(stepReady, true);

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

    StepsCreateInput.StepInput input = mock(StepsCreateInput.StepInput.class);

    Step step = new Step();

    when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
        .thenReturn(workflow);

    doReturn(actionStep).when(stepService).factoryAction(any(), any());

    when(actionStep.create(any(), eq(workflow))).thenReturn(Optional.of(step));

    when(stepRepository.save(step)).thenReturn(step);

    doThrow(new IllegalArgumentException())
        .when(stepService)
        .stepConditionTemplate(any(), any(), any());

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

  private void setupCreateStepTemplates(StepsCreateInput.StepInput stepInput, boolean saveCondition)
      throws ChainingException {

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

  private StepsCreateInput.StepInput mockStep(
      StepActionClass actionClass, List<ConditionCreateInput> conditions) {

    StepsCreateInput.StepInput step = mock(StepsCreateInput.StepInput.class);

    when(step.getStepAction()).thenReturn(actionClass);
    if (!conditions.isEmpty()) when(step.getConditions()).thenReturn(conditions);

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
    void shouldCreateStepTemplate_andLinkExistingConditions() throws ChainingException {
      StepsCreateInput.StepInput stepInput = mock(StepsCreateInput.StepInput.class);
      Workflow workflow = mock(Workflow.class);
      Step created = mock(Step.class);
      List<String> conditionIds = List.of("cond-1", "cond-2");

      when(stepInput.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
      when(stepInput.getConditions()).thenReturn(Collections.emptyList());
      when(stepInput.getConditionIds()).thenReturn(conditionIds);
      when(workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(workflow);
      doReturn(actionStep).when(stepService).factoryAction(StepActionClass.INJECT_EXECUTION, null);
      when(actionStep.create(stepInput, workflow)).thenReturn(Optional.of(created));
      when(stepRepository.save(created)).thenReturn(created);

      Step result = stepService.createStepTemplate(workflowId, stepInput);

      assertSame(created, result);
      verify(conditionService).linkExistingConditionsToStep(created, conditionIds);
      verify(stepRepository).save(created);
    }

    @Test
    void shouldUpdateStepTemplate_andRebuildConditions() throws ChainingException {
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

      Step updated = stepService.updateStepTemplate(stepId, stepInput);

      assertSame(existing, updated);
      assertEquals(5, updated.getLimitExecution());
      assertEquals("{\"updated\":true}", updated.getData());
      verify(conditionService).deleteAllConditionsByStepId(stepId);
      verify(conditionService).linkExistingConditionsToStep(existing, List.of("cond-x"));
      verify(stepRepository).save(existing);
    }

    @Test
    void shouldDeleteStepTemplate_andDeleteLinkedConditions() {
      String stepId = UUID.randomUUID().toString();
      Step template = new Step();

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.of(template));

      stepService.deleteStepTemplate(stepId);

      verify(conditionService).deleteAllConditionsByStepId(stepId);
      verify(stepRepository).delete(template);
    }

    @Test
    void shouldFindStepTemplateById() {
      String stepId = UUID.randomUUID().toString();
      Step template = new Step();

      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.of(template));

      Step result = stepService.findStepTemplateById(stepId);

      assertSame(template, result);
    }

    @Test
    void shouldThrowWhenFindStepTemplateByIdNotFound() {
      String stepId = UUID.randomUUID().toString();
      when(stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(stepId, StepStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      assertThrows(ElementNotFoundException.class, () -> stepService.findStepTemplateById(stepId));
    }

    @Test
    void shouldFindAllStepTemplateByWorkflow() {
      String wfId = UUID.randomUUID().toString();
      List<Step> expected = List.of(new Step(), new Step());

      when(stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(wfId)).thenReturn(expected);

      List<Step> result = stepService.findAllStepTemplateByWorkflow(wfId);

      assertSame(expected, result);
      verify(stepRepository).findAllByStepTemplateIdIsNullAndWorkflowId(wfId);
    }

    @Test
    void shouldFindAllStepTemplates_onlyTemplateRows() {
      Step templateA = new Step();
      Step templateB = new Step();
      Step executed = new Step();
      templateA.setId("tA");
      templateB.setId("tB");
      executed.setId("exec");
      executed.setStepTemplate(new Step());

      when(stepRepository.findAll()).thenReturn(List.of(templateA, executed, templateB));

      List<Step> result = stepService.findAllStepTemplates();

      assertEquals(2, result.size());
      assertTrue(result.contains(templateA));
      assertTrue(result.contains(templateB));
      assertFalse(result.contains(executed));
    }
  }
}
