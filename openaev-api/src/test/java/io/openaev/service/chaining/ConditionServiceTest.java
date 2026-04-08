package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConditionServiceTest {

  @Spy @InjectMocks private ConditionService conditionService;
  @Captor private ArgumentCaptor<Condition> conditionCaptor;
  @Captor private ArgumentCaptor<List<Condition>> conditionsCaptor;
  @Mock private ConditionRepository conditionRepository;
  @Mock private StepRepository stepRepository;
  @Mock private QueueChainingService queueChainingService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Captor private ArgumentCaptor<Long> delayCaptor;

  /* ============================================================
   * isTimeCondition
   * ============================================================ */
  @Nested
  class IsTimeCondition {
    static Stream<ConditionType> allConditionTypes() {
      return Stream.of(ConditionType.values());
    }

    @ParameterizedTest(name = "{index} => type={0}")
    @MethodSource("allConditionTypes")
    void shouldReturnExpected_forGivenConditionType(ConditionType type) {
      // -------- Prepare --------
      Condition condition = mock(Condition.class);
      when(condition.getType()).thenReturn(type);

      assertEquals(type, condition.getType());

      boolean expected = (type == ConditionType.AFTER || type == ConditionType.BEFORE);
      // -------- Act --------
      boolean result = conditionService.isTimeCondition(condition);

      // -------- Assert --------
      assertEquals(result, expected);
    }
  }

  /* ============================================================
   * isMapperCondition
   * ============================================================ */
  @Nested
  class IsMapperCondition {
    static Stream<ConditionType> allConditionTypes() {
      return Stream.of(ConditionType.values());
    }

    @ParameterizedTest(name = "{index} => type={0}")
    @MethodSource("allConditionTypes")
    void shouldReturnExpected_forGivenConditionType(ConditionType type) {
      // -------- Prepare --------
      Condition condition = mock(Condition.class);
      when(condition.getType()).thenReturn(type);

      assertEquals(type, condition.getType());

      boolean expected = type == ConditionType.MAPPER;

      // -------- Act --------
      boolean actual = conditionService.isMapperCondition(condition);

      // -------- Assert --------
      assertEquals(expected, actual);
    }
  }

  /* ============================================================
   * isFilterCondition
   * ============================================================ */
  @Nested
  class IsFilterCondition {
    static Stream<ConditionType> allConditionTypes() {
      return Stream.of(ConditionType.values());
    }

    @ParameterizedTest(name = "{index} => type={0}")
    @MethodSource("allConditionTypes")
    void shouldReturnExpected_forGivenConditionType(ConditionType type) {
      // -------- Prepare --------
      Condition condition = mock(Condition.class);
      when(condition.getType()).thenReturn(type);

      assertEquals(type, condition.getType());

      boolean expected =
          !(type == ConditionType.AFTER
              || type == ConditionType.BEFORE
              || type == ConditionType.MAPPER);
      // -------- Act --------
      boolean result = conditionService.isFilterCondition(condition);

      // -------- Assert --------
      assertEquals(expected, result);
    }
  }

  /* ============================================================
   * isTimeConditionValid
   * ============================================================ */
  @Nested
  class IsTimeConditionValid {

    @ParameterizedTest(name = "{index} => type={0}, nowVsGoal={1}, shouldCreate={2}")
    @MethodSource("timeConditionValidScenarios")
    void shouldReturnExpectedConditionOrNull(
        ConditionType type, NowGoalRelation relation, boolean shouldCreate) {
      // -------- Prepare --------
      Condition template = mock(Condition.class);
      when(template.getType()).thenReturn(type);

      Instant now = Instant.parse("2026-02-04T10:15:30Z");
      Instant goal =
          switch (relation) {
            case NOW_AFTER_GOAL -> Instant.parse("2026-02-04T10:15:00Z");
            case NOW_BEFORE_GOAL -> Instant.parse("2026-02-04T10:16:00Z");
            case NOW_EQUAL_GOAL -> now;
          };

      // -------- Act --------
      boolean result = conditionService.isTimeConditionValid(template, now, goal);

      // -------- Assert --------
      if (shouldCreate) {
        assertTrue(result);
      } else {
        assertFalse(result);
      }
    }

    private static Stream<Arguments> timeConditionValidScenarios() {
      return Stream.of(
          // AFTER: only valid if now.isAfter(goal)
          Arguments.of(ConditionType.AFTER, NowGoalRelation.NOW_AFTER_GOAL, true),
          Arguments.of(ConditionType.AFTER, NowGoalRelation.NOW_BEFORE_GOAL, false),
          Arguments.of(ConditionType.AFTER, NowGoalRelation.NOW_EQUAL_GOAL, false),

          // BEFORE: always returns a Condition
          Arguments.of(ConditionType.BEFORE, NowGoalRelation.NOW_AFTER_GOAL, false),
          Arguments.of(ConditionType.BEFORE, NowGoalRelation.NOW_BEFORE_GOAL, true),
          Arguments.of(ConditionType.BEFORE, NowGoalRelation.NOW_EQUAL_GOAL, false),

          // Other types: returns null
          Arguments.of(ConditionType.MAPPER, NowGoalRelation.NOW_AFTER_GOAL, false),
          Arguments.of(ConditionType.EQ, NowGoalRelation.NOW_AFTER_GOAL, false),
          Arguments.of(ConditionType.DEPEND_ON, NowGoalRelation.NOW_AFTER_GOAL, false));
    }

    private enum NowGoalRelation {
      NOW_AFTER_GOAL,
      NOW_BEFORE_GOAL,
      NOW_EQUAL_GOAL
    }
  }

  /* ============================================================
   * saveCondition / saveAllConditions / findAllConditionsByStepId
   * ============================================================ */
  @Nested
  class RepositoryDelegation {

    @Test
    void shouldSaveCondition_andReturnSavedInstance() {
      // -------- Prepare --------
      Condition condition = mock(Condition.class);
      Condition saved = mock(Condition.class);

      when(conditionRepository.save(condition)).thenReturn(saved);

      // -------- Act --------
      Condition result = conditionService.saveCondition(condition);

      // -------- Assert --------
      assertSame(saved, result);

      verify(conditionRepository).save(conditionCaptor.capture());
      assertSame(condition, conditionCaptor.getValue());

      verifyNoMoreInteractions(conditionRepository);
    }

    @Test
    void shouldSaveAllConditions_andReturnSavedList() {
      // -------- Prepare --------
      List<Condition> conditions = List.of(mock(Condition.class), mock(Condition.class));
      List<Condition> saved = List.of(mock(Condition.class));

      when(conditionRepository.saveAll(conditions)).thenReturn(saved);

      // -------- Act --------
      List<Condition> result = conditionService.saveAllConditions(conditions);

      // -------- Assert --------
      assertSame(saved, result);

      verify(conditionRepository).saveAll(conditionsCaptor.capture());
      assertSame(conditions, conditionsCaptor.getValue());

      verifyNoMoreInteractions(conditionRepository);
    }

    @Test
    void shouldFindAllConditionsByStepId() {
      // -------- Prepare --------
      String stepId = UUID.randomUUID().toString();
      List<Condition> expected = List.of(mock(Condition.class), mock(Condition.class));

      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(expected);

      // -------- Act --------
      List<Condition> result = conditionService.findAllConditionsByStepId(stepId);

      // -------- Assert --------
      assertSame(expected, result);

      verify(conditionRepository).findAllLinkedToStepId(stepId);
      verifyNoMoreInteractions(conditionRepository);
    }
  }

  /* ============================================================
   * isDependOn
   * ============================================================ */
  @Nested
  class IsDependOn {

    @Test
    void shouldCreateDependOnCondition_withGivenStepTemplateId() {
      // -------- Prepare --------
      String stepTemplateId = UUID.randomUUID().toString();

      // -------- Act --------
      Condition result = conditionService.isDependOn(stepTemplateId);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(ConditionKeyType.STEP_TEMPLATE_ID, result.getKeyType());
      assertEquals(ConditionType.DEPEND_ON, result.getType());
      assertEquals(stepTemplateId, result.getValue());
    }
  }

  /* ============================================================
   * checkCondition
   * ============================================================ */
  @Nested
  class CheckCondition {

    @ParameterizedTest(name = "{index} => templates={0}")
    @MethodSource("noConditionTemplates")
    void shouldReturnEmptyList_whenNoConditionTemplates(List<Condition> templates)
        throws ChainingException {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      StepService stepService = mock(StepService.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(templates);

      // -------- Act --------
      List<Condition> result =
          conditionService.checkCondition(stepTemplate, "{\"in\":1}", workflowRun, stepService);

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());

      verify(conditionRepository).findAllLinkedToStepId(stepId);
    }

    static Stream<Arguments> noConditionTemplates() {
      return Stream.of(Arguments.of((List<Condition>) null), Arguments.of(Collections.emptyList()));
    }

    @Test
    void shouldAddValidTimeCondition_whenAfterAndGoalAlreadyReached() throws ChainingException {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      StepService stepService = mock(StepService.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      // time condition template: AFTER with "0ms" from workflowCreatedAt
      Condition timeTemplate = mock(Condition.class);
      when(timeTemplate.getType()).thenReturn(ConditionType.AFTER);
      when(timeTemplate.getValue()).thenReturn("0");

      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(List.of(timeTemplate));

      // Make goal safely in the past => now.isAfter(goal) will be true
      when(workflowRun.getWorkflowCreatedAt()).thenReturn(Instant.EPOCH);

      // We stub isTimeConditionValid to return a concrete Condition (so we don't depend on
      // builder/getters)

      doReturn(true)
          .when(conditionService)
          .isTimeConditionValid(eq(timeTemplate), any(Instant.class), any(Instant.class));
      // -------- Act --------
      List<Condition> result =
          conditionService.checkCondition(stepTemplate, "{\"in\":1}", workflowRun, stepService);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());

      verify(conditionRepository).findAllLinkedToStepId(stepId);
      verify(conditionService)
          .isTimeConditionValid(eq(timeTemplate), any(Instant.class), any(Instant.class));
      verifyNoInteractions(queueChainingService);
      verifyNoInteractions(stepService);
    }

    @Test
    void shouldDelayAndReturnNull_whenTimeConditionNotYetValid() throws Exception {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      StepService stepService = mock(StepService.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      Condition timeTemplate = mock(Condition.class);
      when(timeTemplate.getType()).thenReturn(ConditionType.AFTER);
      when(timeTemplate.getValue()).thenReturn("60000"); // +60s from start

      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(List.of(timeTemplate));

      // Start in the future => goal in the future => AFTER should be invalid now
      Instant futureStart = Instant.now().plus(2, ChronoUnit.MINUTES);
      when(workflowRun.getWorkflowCreatedAt()).thenReturn(futureStart);

      // Force helper to return null => triggers delay branch
      doReturn(false)
          .when(conditionService)
          .isTimeConditionValid(eq(timeTemplate), any(Instant.class), any(Instant.class));

      // -------- Act --------
      List<Condition> result =
          conditionService.checkCondition(stepTemplate, "{\"in\":1}", workflowRun, stepService);

      // -------- Assert --------
      assertNull(result);

      verify(stepDelayQueueService)
          .pushStepTemplateIntoStepDelayQueue(
              eq(stepTemplate),
              any(Instant.class),
              anyString(),
              delayCaptor.capture(),
              eq(workflowRun),
              any(Instant.class));
      assertTrue(delayCaptor.getValue() > 0, "delay should be > 0 when goal is in the future");
    }

    @Test
    void shouldReturnEmptyList_whenAtLeastOneConditionsInvalid() throws ChainingException {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      StepService stepService = mock(StepService.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);
      when(stepTemplate.getData()).thenReturn("{\"data\" : \"data\"}");

      // One filter condition (non time, non mapper) + one mapper condition
      Condition filterTemplate = mock(Condition.class);
      when(filterTemplate.getType()).thenReturn(ConditionType.EQ);
      Condition mapperTemplate = mock(Condition.class);
      when(mapperTemplate.getType()).thenReturn(ConditionType.MAPPER);
      List<Condition> conditions = new ArrayList<>();
      conditions.add(filterTemplate);
      conditions.add(mapperTemplate);
      when(conditionService.findAllConditionsByStepId(stepId)).thenReturn(conditions);

      Condition filterExec = mock(Condition.class);
      doReturn(filterExec)
          .when(conditionService)
          .isFilterConditionValid(eq(filterTemplate), anyString(), anyString());
      doReturn(null)
          .when(conditionService)
          .isMapperConditionValid(eq(mapperTemplate), anyString(), anyString());

      // -------- Act --------
      List<Condition> result =
          conditionService.checkCondition(stepTemplate, "{\"in\":1}", workflowRun, stepService);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());

      verify(conditionService).isFilterConditionValid(eq(filterTemplate), eq("{\"in\":1}"), any());
      verify(conditionService).isMapperConditionValid(eq(mapperTemplate), eq("{\"in\":1}"), any());
      verifyNoInteractions(queueChainingService);
      verifyNoInteractions(stepService);
    }

    @Nested
    class StepDependency {

      @ParameterizedTest(name = "{index} => hasOutput={0}, underLimit={1}, expectedNull={2}")
      @MethodSource("dependencyScenarios")
      void shouldHandleStepFromDependency(
          boolean hasOutput, boolean underLimit, boolean expectedNull) throws ChainingException {
        // -------- Prepare --------
        Step nextStepTemplateToExecute = mock(Step.class);
        Workflow workflowRun = mock(Workflow.class);
        StepService stepService = mock(StepService.class);

        String nextId = UUID.randomUUID().toString();
        String workflowRunId = UUID.randomUUID().toString();
        String stepFromTemplateId = UUID.randomUUID().toString();

        when(nextStepTemplateToExecute.getId()).thenReturn(nextId);
        when(workflowRun.getId()).thenReturn(workflowRunId);

        // Evaluated only when at least one dependent step has output.
        if (hasOutput) {
          when(nextStepTemplateToExecute.getLimitExecution()).thenReturn(3);
          when(stepService.countExecutedStep(workflowRunId, nextId)).thenReturn(underLimit ? 2 : 3);
        }

        Condition depTemplate = mockCondition(ConditionType.DEPEND_ON);

        Step stepFrom = mock(Step.class);
        when(stepFrom.getId()).thenReturn(stepFromTemplateId);
        when(depTemplate.getStepFrom()).thenReturn(stepFrom);

        when(conditionRepository.findAllLinkedToStepId(nextId)).thenReturn(List.of(depTemplate));

        Step executed = mock(Step.class);
        when(executed.getOutput()).thenReturn(hasOutput ? "out" : null);

        when(stepService.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
                stepFromTemplateId, workflowRunId))
            .thenReturn(List.of(executed));

        Condition depExec = mock(Condition.class);
        if (!expectedNull) doReturn(depExec).when(conditionService).isDependOn(stepFromTemplateId);

        // -------- Act --------
        List<Condition> result =
            conditionService.checkCondition(
                nextStepTemplateToExecute, "{\"in\":1}", workflowRun, stepService);

        // -------- Assert --------
        if (expectedNull) {
          assertNull(result);
          verify(conditionService, never()).isDependOn(anyString());
        } else {
          assertNotNull(result);
          assertEquals(1, result.size());
          assertSame(depExec, result.get(0));
          verify(conditionService).isDependOn(stepFromTemplateId);
        }
      }

      static Stream<Arguments> dependencyScenarios() {
        return Stream.of(
            Arguments.of(true, true, false),
            Arguments.of(false, true, true),
            Arguments.of(true, false, true),
            Arguments.of(false, false, true));
      }

      private Condition mockCondition(ConditionType type) {
        Condition c = mock(Condition.class);
        when(c.getType()).thenReturn(type);
        return c;
      }
    }
  }

  /* ============================================================
   * deleteAllConditionsByStepId
   * ============================================================ */
  @Nested
  class DeleteAllConditionsByStepId {

    @Test
    void shouldDoNothing_whenNoConditionLinkedToStep() {
      String stepId = UUID.randomUUID().toString();
      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(List.of());

      conditionService.deleteAllConditionsByStepId(stepId);

      verify(conditionRepository).findAllLinkedToStepId(stepId);
      verify(conditionRepository, never()).save(any());
      verify(conditionRepository, never()).delete(any());
    }

    @Test
    void shouldDeleteCondition_whenUnlinkedAndNoStepFromAndNoRemainingLinks() {
      String removedStepId = "step-A";

      Condition condition = new Condition();
      Step stepA = new Step();
      stepA.setId(removedStepId);
      conditionService.linkToStep(condition, stepA, true);

      when(conditionRepository.findAllLinkedToStepId(removedStepId)).thenReturn(List.of(condition));
      when(conditionRepository.save(any(Condition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      conditionService.deleteAllConditionsByStepId(removedStepId);

      verify(conditionRepository).save(condition);
      verify(conditionRepository).delete(condition);
      assertTrue(condition.getConditionSteps().isEmpty());
    }

    @Test
    void shouldKeepCondition_whenStillLinkedToAnotherStep() {
      String removedStepId = "step-A";

      Condition condition = new Condition();
      Step stepA = new Step();
      stepA.setId(removedStepId);
      Step stepB = new Step();
      stepB.setId("step-B");
      conditionService.linkToStep(condition, stepA, true);
      conditionService.linkToStep(condition, stepB, false);

      when(conditionRepository.findAllLinkedToStepId(removedStepId)).thenReturn(List.of(condition));
      when(conditionRepository.save(any(Condition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      conditionService.deleteAllConditionsByStepId(removedStepId);

      verify(conditionRepository).save(condition);
      verify(conditionRepository, never()).delete(any());
      assertEquals(1, condition.getConditionSteps().size());
      assertEquals("step-B", condition.getConditionSteps().get(0).getStep().getId());
    }
  }

  /* ============================================================
   * linkExistingConditionsToStep
   * ============================================================ */
  @Nested
  class LinkExistingConditionsToStep {

    @Test
    void shouldLinkAllExistingRootConditionsToStep() {
      Step step = new Step();
      step.setId("step-1");

      Condition root1 = new Condition();
      root1.setId("c-1");
      Condition root2 = new Condition();
      root2.setId("c-2");

      when(conditionRepository.findById("c-1")).thenReturn(Optional.of(root1));
      when(conditionRepository.findById("c-2")).thenReturn(Optional.of(root2));

      conditionService.linkExistingConditionsToStep(step, List.of("c-1", "c-2"));

      verify(conditionRepository).save(root1);
      verify(conditionRepository).save(root2);
      assertEquals(1, root1.getConditionSteps().size());
      assertEquals("step-1", root1.getConditionSteps().getFirst().getStep().getId());
      assertEquals(1, root2.getConditionSteps().size());
      assertEquals("step-1", root2.getConditionSteps().getFirst().getStep().getId());
    }

    @Test
    void shouldDoNothing_whenConditionIdsAreEmpty() {
      Step step = new Step();
      step.setId("step-1");

      conditionService.linkExistingConditionsToStep(step, List.of());

      verify(conditionRepository, never()).findById(anyString());
      verify(conditionRepository, never()).save(any());
    }
  }

  /* ============================================================
   * createConditionTree
   * ============================================================ */
  @Nested
  class CreateConditionTree {

    @Test
    void shouldCreateRootAndChildrenAndLinkSteps() {
      String workflowId = "wf-1";
      String linkedStepId = "linked-step";

      ConditionCreateInput rootInput = new ConditionCreateInput();
      rootInput.setTemporaryId("tmp-root");
      rootInput.setType(ConditionType.AND);

      ConditionCreateInput childInput = new ConditionCreateInput();
      childInput.setTemporaryId("tmp-child");
      childInput.setTemporaryIdConditionParent("tmp-root");
      childInput.setType(ConditionType.EQ);
      childInput.setKeyType(ConditionKeyType.PORTSCAN);
      childInput.setValue("445");

      EventInput input =
          EventInput.builder()
              .name("event-1")
              .description("desc-1")
              .workflowId(workflowId)
              .conditions(List.of(rootInput, childInput))
              .stepIds(List.of(linkedStepId))
              .build();

      Step linkedStep = new Step();
      linkedStep.setId(linkedStepId);

      when(stepRepository.findAllById(List.of(linkedStepId))).thenReturn(List.of(linkedStep));
      when(conditionRepository.save(any(Condition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Condition createdRoot = conditionService.createConditionTree(input);

      assertNotNull(createdRoot);
      assertEquals("event-1", createdRoot.getName());
      assertEquals("desc-1", createdRoot.getDescription());
      assertEquals(workflowId, createdRoot.getWorkflowId());
      assertEquals(ConditionType.AND, createdRoot.getType());
      assertEquals(1, createdRoot.getConditionChildren().size());

      verify(stepRepository).findAllById(List.of(linkedStepId));

      Condition savedChild = createdRoot.getConditionChildren().getFirst();
      assertEquals("445", savedChild.getValue());
      assertEquals(workflowId, savedChild.getWorkflowId());
      assertNotNull(savedChild.getConditionParent());
    }
  }

  /* ============================================================
   * updateConditionTree
   * ============================================================ */
  @Nested
  class UpdateConditionTree {

    @Test
    void shouldUpdateRootAndRebuildChildrenAndLinks() {
      String rootId = "root-1";
      String workflowId = "wf-new";
      String linkedStepId = "linked-step";

      Condition existingRoot = new Condition();
      existingRoot.setId(rootId);
      existingRoot.setName("old-name");
      existingRoot.setDescription("old-desc");
      existingRoot.setWorkflowId("wf-old");
      existingRoot.setType(ConditionType.OR);

      Condition oldChild = new Condition();
      oldChild.setConditionParent(existingRoot);
      existingRoot.getConditionChildren().add(oldChild);

      Step oldLinkedStep = new Step();
      oldLinkedStep.setId("old-linked-step");
      conditionService.linkToStep(oldChild, oldLinkedStep, true);

      ConditionCreateInput rootInput = new ConditionCreateInput();
      rootInput.setTemporaryId("tmp-root");
      rootInput.setType(ConditionType.AND);

      ConditionCreateInput childInput = new ConditionCreateInput();
      childInput.setTemporaryId("tmp-child");
      childInput.setTemporaryIdConditionParent("tmp-root");
      childInput.setType(ConditionType.EQ);
      childInput.setKeyType(ConditionKeyType.STATUS);
      childInput.setValue("ok");

      EventInput input =
          EventInput.builder()
              .name("new-name")
              .description("new-desc")
              .workflowId(workflowId)
              .conditions(List.of(rootInput, childInput))
              .stepIds(List.of(linkedStepId))
              .build();

      Step linkedStep = new Step();
      linkedStep.setId(linkedStepId);

      when(conditionRepository.findById(rootId)).thenReturn(Optional.of(existingRoot));
      when(stepRepository.findAllById(List.of(linkedStepId))).thenReturn(List.of(linkedStep));
      when(conditionRepository.save(any(Condition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      Condition updated = conditionService.updateConditionTree(rootId, input);

      assertEquals("new-name", updated.getName());
      assertEquals("new-desc", updated.getDescription());
      assertEquals(workflowId, updated.getWorkflowId());
      assertEquals(ConditionType.AND, updated.getType());
      assertEquals(1, updated.getConditionChildren().size());
      assertEquals("ok", updated.getConditionChildren().getFirst().getValue());
      assertEquals(workflowId, updated.getConditionChildren().getFirst().getWorkflowId());

      verify(conditionRepository, atLeast(2)).save(any(Condition.class));
    }

    @Test
    void shouldThrowWhenRootConditionDoesNotExist() {
      ConditionCreateInput rootInput = new ConditionCreateInput();
      rootInput.setTemporaryId("tmp-root");
      rootInput.setType(ConditionType.AND);

      EventInput input =
          EventInput.builder().name("x").workflowId("wf").conditions(List.of(rootInput)).build();
      when(conditionRepository.findById("missing-root")).thenReturn(Optional.empty());

      assertThrows(
          EntityNotFoundException.class,
          () -> conditionService.updateConditionTree("missing-root", input));
    }
  }
}
