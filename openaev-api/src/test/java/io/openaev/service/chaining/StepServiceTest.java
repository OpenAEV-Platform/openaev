package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.transaction.support.TransactionTemplate;

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
  @Mock private SimulationRateLimitService simulationRateLimitService;

  @Spy @InjectMocks StepService stepService;
  private QueueChainingJob queueChainingJob;
  private TransactionTemplate transactionTemplate;

  private Workflow workflow;

  @Captor private ArgumentCaptor<String> simulationIdCaptor;
  @Captor private ArgumentCaptor<String> workflowTemplateIdCaptor;
  @Captor private ArgumentCaptor<Step> stepCaptor;
  @Captor private ArgumentCaptor<Workflow> workflowCaptor;
  @Captor private ArgumentCaptor<List<Condition>> conditionsCaptor;
  @Captor private ArgumentCaptor<String> stepIdCaptor;

  @BeforeEach
  void setUp() {
    transactionTemplate = mock(TransactionTemplate.class);
    lenient()
        .doAnswer(
            invocation -> {
              ((java.util.function.Consumer<Object>) invocation.getArgument(0)).accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
    queueChainingJob =
        new QueueChainingJob(
            stepDelayQueueService, stepService, workflowService, transactionTemplate);
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
        Map<PrimitiveType, Optional<PrimitiveType>> expectedParentMap)
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

      Map<PrimitiveType, Condition> byKey =
          producedConditions.stream()
              .collect(Collectors.toMap(c -> c.getKeyTypes().getFirst(), c -> c));

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
              List.of(mockCondition("ROOT", PrimitiveType.Text, null)),
              Map.of(PrimitiveType.Text, Optional.empty())),
          Arguments.of(
              "Root with one child",
              List.of(
                  mockCondition("ROOT", PrimitiveType.Text, null),
                  mockCondition("CHILD", PrimitiveType.Number, "ROOT")),
              Map.of(
                  PrimitiveType.Text, Optional.empty(),
                  PrimitiveType.Number, Optional.of(PrimitiveType.Text))),
          Arguments.of(
              "Root with two-level tree",
              List.of(
                  mockCondition("ROOT", PrimitiveType.Text, null),
                  mockCondition("A", PrimitiveType.Port, "ROOT"),
                  mockCondition("B", PrimitiveType.IPv4, "A")),
              Map.of(
                  PrimitiveType.Text, Optional.empty(),
                  PrimitiveType.Port, Optional.of(PrimitiveType.Text),
                  PrimitiveType.IPv4, Optional.of(PrimitiveType.Port))));
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
                      .keyTypes(List.of(PrimitiveType.Text))
                      .temporaryIdConditionParent(null)
                      .build(),
                  ConditionCreateInput.builder()
                      .keyTypes(List.of(PrimitiveType.Number))
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
              .keyTypes(List.of(PrimitiveType.Text))
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
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);

        String stepId = UUID.randomUUID().toString();
        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));
        when(persistedTemplate.getStepAction()).thenReturn(null);

        // Act + Assert
        assertThrows(
            ChainingException.class,
            () ->
                stepService.createReadySteps(
                    nextStepTemplateToExecute, workflowRun, "{\"a\":1}", 0));

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

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(persistedTemplate.getId()).thenReturn(stepId);
        when(persistedTemplate.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
        doReturn(localActionStep)
            .when(stepService)
            .factoryAction(StepActionClass.INJECT_EXECUTION, stepId);

        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(null);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input, 0);

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

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(persistedTemplate.getId()).thenReturn(stepId);
        when(persistedTemplate.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(localActionStep);

        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        Condition c1 = mock(Condition.class);
        Condition c2 = mock(Condition.class);
        List<Condition> usedMappers = new ArrayList<>(List.of(c1, c2));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(List.of(new ConditionService.ExecutionBatch(input, usedMappers, null)));
        when(injectExecutionStep.expandTargetBatches(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Step stepReady = mock(Step.class);

        when(localActionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);
        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input, 0);

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
        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(persistedTemplate.getId()).thenReturn(stepId);
        when(persistedTemplate.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(localActionStep);

        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        Condition c1 = mock(Condition.class);
        List<Condition> usedMappers = new ArrayList<>(List.of(c1));

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(List.of(new ConditionService.ExecutionBatch(input, usedMappers, null)));
        when(injectExecutionStep.expandTargetBatches(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Step stepReady = mock(Step.class);

        when(localActionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.ofNullable(stepReady));
        assertNotNull(stepReady);

        when(stepRepository.save(stepReady)).thenReturn(stepReady);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input, 0);

        // Assert
        assertEquals(1, result.size());
        assertSame(stepReady, result.getFirst());

        verify(localActionStep).ready(persistedTemplate, input, workflowRun);
        verify(stepRepository, times(1)).save(stepReady);

        verify(conditionService).saveAllConditions(anyList());

        verify(queueChainingService, never()).readyStep(any(), any());
      }
    }

    @Nested
    class PayloadStepMultiAssetExpansion {

      /**
       * Regression test for a bug where a payload-based step with no condition mapper (e.g. a root
       * step, or a step gated only by a non-mapper condition such as DEPEND_ON) would only ever
       * execute once — for a single scope asset — and be silently skipped for every other in-scope
       * asset on subsequent scheduling cycles. Injector-contract steps (hasPayload() == false) were
       * unaffected, which is exactly what was reported: contract steps correctly expanded per asset
       * while sibling payload steps did not.
       *
       * <p>Per-target deduplication is owned by expandTargetBatches/getCommittedHashes further down
       * in createReadySteps, so the fact that this step template already produced a READY step for
       * one asset must not prevent it from being expanded again for the remaining, not-yet-executed
       * assets.
       */
      @Test
      void
          given_payloadStepWithNoMapperAlreadyExecutedOnce_should_stillCreateReadySteps_forRemainingAssets()
              throws Exception {
        // Arrange
        Step nextStepTemplateToExecute = mock(Step.class);
        Step persistedTemplate = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        ActionStep localActionStep = mock(ActionStep.class);

        String input = "{\"x\":1}";
        String stepId = UUID.randomUUID().toString();
        String workflowId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getId()).thenReturn(stepId);
        when(persistedTemplate.getId()).thenReturn(stepId);
        when(persistedTemplate.getStepAction()).thenReturn(StepActionClass.INJECT_EXECUTION);
        lenient().when(workflowRun.getId()).thenReturn(workflowId);
        when(stepService.factoryAction(StepActionClass.INJECT_EXECUTION, stepId))
            .thenReturn(localActionStep);
        when(stepRepository.findByIdAndStatus(stepId, StepStatus.TEMPLATE))
            .thenReturn(Optional.of(persistedTemplate));

        // NOTE: this step is a payload step with no condition mapper that has already produced a
        // READY step for a previous asset in an earlier scheduling cycle (i.e. exactly the
        // combination that used to be permanently short-circuited by the removed
        // "!hasConditionMapper && isStepAlreadyExecutedOnce && hasPayload" guard). These are
        // stubbed leniently because the fixed createReadySteps no longer consults them at all —
        // reverting the fix would make this test start invoking them (and start failing, since the
        // old guard would then return an empty list instead of expanding the remaining assets).
        lenient().when(conditionService.hasConditionMapper(persistedTemplate)).thenReturn(false);
        lenient().when(injectExecutionStep.hasPayload(persistedTemplate)).thenReturn(true);
        lenient()
            .when(stepRepository.existsByStepTemplateIdAndWorkflowId(stepId, workflowId))
            .thenReturn(true);

        when(conditionService.checkCondition(persistedTemplate, workflowRun, input))
            .thenReturn(List.of(new ConditionService.ExecutionBatch(input, List.of(), null)));

        // expandTargetBatches fans the single combo out to the two remaining, not-yet-executed
        // assets in scope.
        ConditionService.ExecutionBatch assetTwoBatch =
            new ConditionService.ExecutionBatch(input, List.of(), "combo:asset-2");
        ConditionService.ExecutionBatch assetThreeBatch =
            new ConditionService.ExecutionBatch(input, List.of(), "combo:asset-3");
        when(injectExecutionStep.expandTargetBatches(any(), eq(workflowRun), eq(persistedTemplate)))
            .thenReturn(List.of(assetTwoBatch, assetThreeBatch));
        when(conditionService.getCommittedHashes(persistedTemplate, workflowRun))
            .thenReturn(Set.of());

        Step stepReadyTwo = mock(Step.class);
        Step stepReadyThree = mock(Step.class);
        when(localActionStep.ready(persistedTemplate, input, workflowRun))
            .thenReturn(Optional.of(stepReadyTwo), Optional.of(stepReadyThree));
        when(stepRepository.save(stepReadyTwo)).thenReturn(stepReadyTwo);
        when(stepRepository.save(stepReadyThree)).thenReturn(stepReadyThree);

        // Act
        List<Step> result =
            stepService.createReadySteps(nextStepTemplateToExecute, workflowRun, input, 0);

        // Assert: the step must not be silently skipped — the remaining assets still get a READY
        // step each.
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(stepReadyTwo, stepReadyThree)));

        verify(conditionService).checkCondition(persistedTemplate, workflowRun, input);
        verify(injectExecutionStep)
            .expandTargetBatches(any(), eq(workflowRun), eq(persistedTemplate));
        verify(conditionService)
            .commitHashes(
                eq(persistedTemplate),
                eq(workflowRun),
                eq(Set.of("combo:asset-2", "combo:asset-3")));
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
      Optional<String> result = stepService.findStepIdByInjectId(injectId);

      // Assert
      assertTrue(result.isPresent());
      assertEquals(stepId, result.get());
      verify(stepRepository).findStepIdByInjectId(injectId);
      verifyNoMoreInteractions(stepRepository);
    }

    @Test
    void given_missingInjectId_should_returnEmptyOptional() {
      // Arrange
      String injectId = UUID.randomUUID().toString();

      when(stepRepository.findStepIdByInjectId(injectId)).thenReturn(Optional.empty());

      // Act
      Optional<String> result = stepService.findStepIdByInjectId(injectId);

      // Assert
      assertTrue(result.isEmpty());
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
                .createReadySteps(any(Step.class), any(Workflow.class), any(), anyInt());
          } else {
            doReturn(List.of(mock(Step.class)))
                .when(stepService)
                .createReadySteps(any(Step.class), any(Workflow.class), any(), anyInt());
          }
        }

        // Act
        queueChainingJob.execute(null);

        // Assert
        if (stepFound) {
          verify(stepService).createReadySteps(step, workflowRun, null, 0);
        } else {
          verify(stepService, never()).createReadySteps(any(), any(), any(), anyInt());
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
      String temporaryId, PrimitiveType keyType, String parentTempId) {

    ConditionCreateInput c = mock(ConditionCreateInput.class);

    when(c.getKeyTypes()).thenReturn(List.of(keyType));
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
      existing.setConditionSteps(
          new ArrayList<>(List.of(new ConditionStep(), new ConditionStep())));

      Step candidate = new Step();
      candidate.setStepAction(StepActionClass.INJECT_EXECUTION);
      candidate.setLimitExecution(5);
      candidate.setData("{\"updated\":true}");
      candidate.setInput("{}");
      candidate.setOutputParser("{}");
      Step saved = new Step();

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
      when(stepRepository.save(existing)).thenReturn(saved);

      // Act
      Step updated = stepService.updateStepTemplate(stepId, stepInput);

      // Assert
      assertSame(saved, updated);
      assertEquals(5, existing.getLimitExecution());
      assertEquals("{\"updated\":true}", existing.getData());
      assertTrue(existing.getConditionSteps().isEmpty());
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
    void given_templateSteps_should_findAllStepTemplates_returnTemplateRowsFromRepository() {
      // Arrange
      Step templateA = new Step();
      Step templateB = new Step();
      templateA.setId("tA");
      templateB.setId("tB");
      List<Step> templates = List.of(templateA, templateB);

      when(stepRepository.findAllByStepTemplateIdIsNull()).thenReturn(templates);

      // Act
      List<Step> result = stepService.findAllStepTemplates();

      // Assert
      assertEquals(2, result.size());
      assertTrue(result.contains(templateA));
      assertTrue(result.contains(templateB));
      verify(stepRepository).findAllByStepTemplateIdIsNull();
    }
  }

  /* ============================================================
   * copyStepConditionTemplate — field preservation
   * ============================================================ */
  @Nested
  class CopyStepConditionTemplateFields {

    @Test
    void given_conditionWithAllFields_should_copyAllConfigFieldsToNewCondition() {
      // Arrange
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("source-workflow-id");

      Step sourceStep = new Step();
      sourceStep.setId("source-step-id");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow targetWorkflow = new Workflow();
      targetWorkflow.setId("target-workflow-id");

      Step targetStep = new Step();
      targetStep.setId("target-step-id");
      targetStep.setWorkflow(targetWorkflow);

      // Build a root AND condition with all config fields set to non-default values
      Condition rootCondition =
          Condition.builder()
              .type(ConditionType.AND)
              .key("test_key")
              .keyTypes(List.of(PrimitiveType.AssetGroupId))
              .value("test_value")
              .caseSensitive(false) // non-default (default is true)
              .mappingType(MappingType.GLOBAL)
              .name("root-event-name")
              .description("root-event-description")
              .build();
      rootCondition.setId("root-id");

      // Build a child EQ leaf condition
      Condition childCondition =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("child_key")
              .keyTypes(List.of(PrimitiveType.AssetGroupId))
              .value("child_value")
              .caseSensitive(false)
              .mappingType(MappingType.LOCAL)
              .name("child-name")
              .conditionParent(rootCondition)
              .build();
      childCondition.setId("child-id");

      when(conditionService.findAllConditionsByStepId("source-step-id"))
          .thenReturn(List.of(rootCondition, childCondition));

      when(conditionService.findAllNonMapperConditionsByWorkflowId("source-workflow-id"))
          .thenReturn(List.of(rootCondition, childCondition));

      // Capture saved conditions
      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      // Act
      stepService.copyStepConditionTemplate(sourceStep, targetStep);

      // Assert — root condition fields
      assertEquals(2, savedConditions.size());
      Condition copiedRoot = savedConditions.get(0);
      assertEquals(rootCondition.getKey(), copiedRoot.getKey());
      assertEquals(rootCondition.getKeyTypes(), copiedRoot.getKeyTypes());
      assertEquals(rootCondition.getType(), copiedRoot.getType());
      assertEquals(rootCondition.getValue(), copiedRoot.getValue());
      assertEquals(rootCondition.isCaseSensitive(), copiedRoot.isCaseSensitive());
      assertEquals(rootCondition.getMappingType(), copiedRoot.getMappingType());

      // Assert — child condition fields
      Condition copiedChild = savedConditions.get(1);
      assertEquals(childCondition.getKey(), copiedChild.getKey());
      assertEquals(childCondition.getKeyTypes(), copiedChild.getKeyTypes());
      assertEquals(childCondition.getType(), copiedChild.getType());
      assertEquals(childCondition.getValue(), copiedChild.getValue());
      assertEquals(childCondition.isCaseSensitive(), copiedChild.isCaseSensitive());
      assertEquals(childCondition.getMappingType(), copiedChild.getMappingType());

      // Assert — structural link: child's parent is the copied root
      assertSame(copiedRoot, copiedChild.getConditionParent());
    }

    @Test
    void given_caseSensitiveFalse_should_notRevertToDefaultTrue() {
      // This specifically catches the @Builder.Default=true regression
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("src-wf");

      Step sourceStep = new Step();
      sourceStep.setId("src");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow targetWorkflow = new Workflow();
      targetWorkflow.setId("tgt-wf");

      Step targetStep = new Step();
      targetStep.setId("tgt");
      targetStep.setWorkflow(targetWorkflow);

      Condition root =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("k")
              .value("v")
              .caseSensitive(false)
              .mappingType(MappingType.DEFAULT)
              .build();
      root.setId("r");

      when(conditionService.findAllConditionsByStepId("src")).thenReturn(List.of(root));
      when(conditionService.findAllNonMapperConditionsByWorkflowId("src-wf"))
          .thenReturn(List.of(root));
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                return c;
              });

      // Act
      stepService.copyStepConditionTemplate(sourceStep, targetStep);

      // Assert
      ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
      verify(conditionService).saveCondition(captor.capture());
      assertFalse(captor.getValue().isCaseSensitive());
    }

    @Test
    void given_scenarioEvent_when_copiedToSimulation_should_preserveWorkflowIdAndName() {
      // GIVEN a scenario template step with a named root condition (event)
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("old-scenario-workflow-id");

      Step sourceStep = new Step();
      sourceStep.setId("scenario-step");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow simulationWorkflow = new Workflow();
      simulationWorkflow.setId("simulation-workflow-id");

      Step stepCopied = new Step();
      stepCopied.setId("simulation-step");
      stepCopied.setWorkflow(simulationWorkflow);

      Condition sourceRoot =
          Condition.builder()
              .type(ConditionType.AND)
              .key("event_key")
              .name("My Event Name")
              .workflowId("old-scenario-workflow-id")
              .mappingType(MappingType.GLOBAL)
              .build();
      sourceRoot.setId("src-root");

      Condition sourceChild =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("child_key")
              .name("Child Label")
              .workflowId("old-scenario-workflow-id")
              .mappingType(MappingType.LOCAL)
              .conditionParent(sourceRoot)
              .build();
      sourceChild.setId("src-child");

      when(conditionService.findAllConditionsByStepId("scenario-step"))
          .thenReturn(List.of(sourceRoot, sourceChild));

      when(conditionService.findAllNonMapperConditionsByWorkflowId("old-scenario-workflow-id"))
          .thenReturn(List.of(sourceRoot, sourceChild));

      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      // WHEN copyStepConditionTemplate copies the step into the simulation workflow
      stepService.copyStepConditionTemplate(sourceStep, stepCopied);

      // THEN the copied root condition has:
      assertEquals(2, savedConditions.size());
      Condition copiedRoot = savedConditions.get(0);
      Condition copiedChild = savedConditions.get(1);

      // workflowId == stepCopied.getWorkflow().getId() (target workflow, NOT the source)
      assertEquals(simulationWorkflow.getId(), copiedRoot.getWorkflowId());
      assertEquals(simulationWorkflow.getId(), copiedChild.getWorkflowId());

      // name == source name (preserved from template)
      assertEquals(sourceRoot.getName(), copiedRoot.getName());
      assertEquals(sourceChild.getName(), copiedChild.getName());

      // Verify it's NOT the old source workflowId
      assertNotEquals("old-scenario-workflow-id", copiedRoot.getWorkflowId());
    }

    @Test
    void given_scenarioEventWithLeaf_when_copied_should_exposeLeafInConditionChildren() {
      // GIVEN a scenario template step with a named root AND condition (event "totoCond")
      //       having one leaf child (text equals "toto")
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("old-wf");

      Step sourceStep = new Step();
      sourceStep.setId("scenario-step");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow simulationWorkflow = new Workflow();
      simulationWorkflow.setId("sim-wf-id");

      Step stepCopied = new Step();
      stepCopied.setId("sim-step");
      stepCopied.setWorkflow(simulationWorkflow);

      Condition sourceRoot =
          Condition.builder()
              .type(ConditionType.AND)
              .key("event_key")
              .name("totoCond")
              .workflowId("old-wf")
              .mappingType(MappingType.GLOBAL)
              .build();
      sourceRoot.setId("root-id");

      Condition sourceLeaf =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("text")
              .value("toto")
              .name("leaf-label")
              .workflowId("old-wf")
              .mappingType(MappingType.LOCAL)
              .conditionParent(sourceRoot)
              .build();
      sourceLeaf.setId("leaf-id");

      when(conditionService.findAllConditionsByStepId("scenario-step"))
          .thenReturn(List.of(sourceRoot, sourceLeaf));

      when(conditionService.findAllNonMapperConditionsByWorkflowId("old-wf"))
          .thenReturn(List.of(sourceRoot, sourceLeaf));
      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      // WHEN copyStepConditionTemplate copies the step into the simulation workflow
      stepService.copyStepConditionTemplate(sourceStep, stepCopied);

      // THEN the copied root exposes its child in memory (inverse side populated)
      assertEquals(2, savedConditions.size());
      Condition copiedRoot = savedConditions.get(0);
      Condition copiedChild = savedConditions.get(1);

      assertNotNull(copiedRoot.getConditionChildren());
      assertEquals(1, copiedRoot.getConditionChildren().size());
      assertSame(copiedChild, copiedRoot.getConditionChildren().get(0));
      assertEquals(sourceLeaf.getValue(), copiedRoot.getConditionChildren().get(0).getValue());

      // AND the child's conditionParent points back to the copied root
      assertSame(copiedRoot, copiedChild.getConditionParent());
    }

    @Test
    void given_eventApiCreation_rootOnlyLinkedToStep_should_stillCopyChild() {
      // Reproduces the Event-API case: only the ROOT is linked to the step (via
      // conditions_steps), the child is NOT linked but belongs to the same workflow.
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("src-wf");

      Step sourceStep = new Step();
      sourceStep.setId("src-step");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow targetWorkflow = new Workflow();
      targetWorkflow.setId("tgt-wf");

      Step targetStep = new Step();
      targetStep.setId("tgt-step");
      targetStep.setWorkflow(targetWorkflow);

      Condition root =
          Condition.builder()
              .type(ConditionType.AND)
              .name("EventApiEvent")
              .workflowId("src-wf")
              .mappingType(MappingType.GLOBAL)
              .build();
      root.setId("root-1");

      Condition leaf =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("text")
              .value("toto")
              .workflowId("src-wf")
              .mappingType(MappingType.LOCAL)
              .conditionParent(root)
              .build();
      leaf.setId("leaf-1");

      // findAllConditionsByStepId returns ONLY the root (Event-API links only root)
      when(conditionService.findAllConditionsByStepId("src-step")).thenReturn(List.of(root));

      // findAllNonMapperConditionsByWorkflowId returns root + child
      when(conditionService.findAllNonMapperConditionsByWorkflowId("src-wf"))
          .thenReturn(List.of(root, leaf));

      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      // Act
      stepService.copyStepConditionTemplate(sourceStep, targetStep);

      // Assert — both root and child are copied
      assertEquals(2, savedConditions.size());
      Condition copiedRoot = savedConditions.get(0);
      Condition copiedChild = savedConditions.get(1);

      assertEquals("toto", copiedChild.getValue());
      assertSame(copiedRoot, copiedChild.getConditionParent());
      assertEquals(1, copiedRoot.getConditionChildren().size());
    }

    @Test
    void given_eventApiCreation_deepTree_should_copyAllLevels() {
      // Deep tree: root -> group (OR) -> leaf (EQ), all from workflow query
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("src-wf");

      Step sourceStep = new Step();
      sourceStep.setId("src-step");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow targetWorkflow = new Workflow();
      targetWorkflow.setId("tgt-wf");

      Step targetStep = new Step();
      targetStep.setId("tgt-step");
      targetStep.setWorkflow(targetWorkflow);

      Condition root =
          Condition.builder()
              .type(ConditionType.AND)
              .name("DeepEvent")
              .workflowId("src-wf")
              .build();
      root.setId("root-deep");

      Condition group =
          Condition.builder()
              .type(ConditionType.OR)
              .workflowId("src-wf")
              .conditionParent(root)
              .build();
      group.setId("group-deep");

      Condition leaf =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("field")
              .value("deep-value")
              .workflowId("src-wf")
              .mappingType(MappingType.LOCAL)
              .conditionParent(group)
              .build();
      leaf.setId("leaf-deep");

      // Only root linked to step
      when(conditionService.findAllConditionsByStepId("src-step")).thenReturn(List.of(root));

      // Full workflow tree returned
      when(conditionService.findAllNonMapperConditionsByWorkflowId("src-wf"))
          .thenReturn(List.of(root, group, leaf));

      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      // Act
      stepService.copyStepConditionTemplate(sourceStep, targetStep);

      // Assert — all 3 levels copied
      assertEquals(3, savedConditions.size());
      Condition copiedRoot = savedConditions.get(0);
      Condition copiedGroup = savedConditions.get(1);
      Condition copiedLeaf = savedConditions.get(2);

      // Structural links
      assertSame(copiedRoot, copiedGroup.getConditionParent());
      assertSame(copiedGroup, copiedLeaf.getConditionParent());

      // Leaf value preserved
      assertEquals("deep-value", copiedLeaf.getValue());

      // In-memory tree is navigable from root
      assertEquals(1, copiedRoot.getConditionChildren().size());
      assertEquals(1, copiedGroup.getConditionChildren().size());
      assertSame(copiedLeaf, copiedGroup.getConditionChildren().get(0));
    }

    @Test
    void given_mixedEventAndMapperRoots_should_allowSingleNonMapperRoot() {
      Workflow sourceWorkflow = new Workflow();
      sourceWorkflow.setId("src-wf");

      Step sourceStep = new Step();
      sourceStep.setId("src-step");
      sourceStep.setWorkflow(sourceWorkflow);

      Workflow targetWorkflow = new Workflow();
      targetWorkflow.setId("tgt-wf");

      Step targetStep = new Step();
      targetStep.setId("tgt-step");
      targetStep.setWorkflow(targetWorkflow);

      Condition eventRoot =
          Condition.builder()
              .type(ConditionType.AND)
              .name("event-root")
              .workflowId("src-wf")
              .build();
      eventRoot.setId("event-root-id");

      Condition eventLeaf =
          Condition.builder()
              .type(ConditionType.EQ)
              .key("text")
              .value("leaf")
              .workflowId("src-wf")
              .conditionParent(eventRoot)
              .build();
      eventLeaf.setId("event-leaf-id");

      Condition mapperRoot =
          Condition.builder()
              .type(ConditionType.MAPPER)
              .mappingType(MappingType.LOCAL)
              .workflowId("src-wf")
              .build();
      mapperRoot.setId("mapper-root-id");

      // Step has one event root and one mapper root linked.
      when(conditionService.findAllConditionsByStepId("src-step"))
          .thenReturn(List.of(eventRoot, mapperRoot));

      // Workflow non-mapper query must still provide the full event subtree.
      when(conditionService.findAllNonMapperConditionsByWorkflowId("src-wf"))
          .thenReturn(List.of(eventRoot, eventLeaf));

      List<Condition> savedConditions = new ArrayList<>();
      when(conditionService.saveCondition(any(Condition.class)))
          .thenAnswer(
              invocation -> {
                Condition c = invocation.getArgument(0);
                c.setId(UUID.randomUUID().toString());
                savedConditions.add(c);
                return c;
              });

      stepService.copyStepConditionTemplate(sourceStep, targetStep);

      // event root + mapper root + event leaf
      assertEquals(3, savedConditions.size());
      assertEquals(ConditionType.AND, savedConditions.get(0).getType());
      assertEquals(ConditionType.MAPPER, savedConditions.get(1).getType());
      assertEquals("leaf", savedConditions.get(2).getValue());
    }
  }
}
