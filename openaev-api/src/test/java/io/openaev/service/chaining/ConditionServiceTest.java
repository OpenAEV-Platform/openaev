package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.rest.exception.ChainingException;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
  @Mock private QueueChainingService queueChainingService;
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

      when(conditionRepository.findAllByStep_Id(stepId)).thenReturn(expected);

      // -------- Act --------
      List<Condition> result = conditionService.findAllConditionsByStepId(stepId);

      // -------- Assert --------
      assertSame(expected, result);

      verify(conditionRepository).findAllByStep_Id(stepId);
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
      assertEquals("step_template_id", result.getKey());
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

      when(conditionRepository.findAllByStep_Id(stepId)).thenReturn(templates);

      // -------- Act --------
      List<Condition> result =
          conditionService.checkCondition(stepTemplate, "{\"in\":1}", workflowRun, stepService);

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());

      verify(conditionRepository).findAllByStep_Id(stepId);
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

      when(conditionRepository.findAllByStep_Id(stepId)).thenReturn(List.of(timeTemplate));

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

      verify(conditionRepository).findAllByStep_Id(stepId);
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

      when(conditionRepository.findAllByStep_Id(stepId)).thenReturn(List.of(timeTemplate));

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

      verify(queueChainingService)
          .delayStep(eq(stepTemplate), eq(workflowRun), delayCaptor.capture());
      assertTrue(delayCaptor.getValue() > 0, "delay should be > 0 when goal is in the future");
    }

    @Test
    void shouldWrapIOException_whenDelayStepThrows() throws Exception {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      StepService stepService = mock(StepService.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      Condition timeTemplate = mock(Condition.class);
      when(timeTemplate.getType()).thenReturn(ConditionType.AFTER);
      when(timeTemplate.getValue()).thenReturn("60000");

      when(conditionRepository.findAllByStep_Id(stepId)).thenReturn(List.of(timeTemplate));

      when(workflowRun.getWorkflowCreatedAt())
          .thenReturn(Instant.now().plus(2, ChronoUnit.MINUTES));

      doReturn(false)
          .when(conditionService)
          .isTimeConditionValid(eq(timeTemplate), any(Instant.class), any(Instant.class));

      IOException io = new IOException("boom");
      doThrow(io)
          .when(queueChainingService)
          .delayStep(any(Step.class), any(Workflow.class), anyLong());

      // -------- Act --------
      assertThrows(
          ChainingException.class,
          () ->
              conditionService.checkCondition(
                  stepTemplate, "{\"in\":1}", workflowRun, stepService));

      // -------- Assert --------
      verify(queueChainingService).delayStep(eq(stepTemplate), eq(workflowRun), anyLong());
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

        when(nextStepTemplateToExecute.getLimitExecution()).thenReturn(3);
        when(stepService.countExecutedStep(workflowRunId, nextId)).thenReturn(underLimit ? 2 : 3);

        Condition depTemplate = mockCondition(ConditionType.DEPEND_ON);

        Step stepFrom = mock(Step.class);
        when(stepFrom.getId()).thenReturn(stepFromTemplateId);
        when(depTemplate.getStepFrom()).thenReturn(stepFrom);

        when(conditionRepository.findAllByStep_Id(nextId)).thenReturn(List.of(depTemplate));

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
}
