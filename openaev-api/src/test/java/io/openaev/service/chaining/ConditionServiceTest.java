package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.ConditionUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
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
  @Mock private WorkflowStateService workflowStateService;
  @Spy private ConditionUtils conditionUtils;

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
      boolean result = conditionUtils.isTimeCondition(condition);

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
      boolean actual = conditionUtils.isMapperCondition(condition);

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
      boolean result = conditionUtils.isFilterCondition(condition);

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
      lenient().when(template.getType()).thenReturn(type);

      Instant now = Instant.parse("2026-02-04T10:15:30Z");
      Instant goal =
          switch (relation) {
            case NOW_AFTER_GOAL -> Instant.parse("2026-02-04T10:15:00Z");
            case NOW_BEFORE_GOAL -> Instant.parse("2026-02-04T10:16:00Z");
            case NOW_EQUAL_GOAL -> now;
          };

      // -------- Act --------
      boolean result = conditionUtils.isTimeConditionValid(template, now, goal);

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
    void shouldReturnSingleBatchWithInput_whenNoConditionTemplates(List<Condition> templates)
        throws ChainingException {
      // -------- Prepare --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      when(conditionRepository.findAllLinkedToStepId(stepId)).thenReturn(templates);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("{\"in\":1}", result.getFirst().inputString());
      assertTrue(result.getFirst().usedMappers().isEmpty());

      verify(conditionRepository).findAllLinkedToStepId(stepId);
    }

    static Stream<Arguments> noConditionTemplates() {
      return Stream.of(Arguments.of((List<Condition>) null), Arguments.of(Collections.emptyList()));
    }

    @Test
    void shouldDelegateToExtractInputsForStepExecution_withMapperConditionsOnly()
        throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);
      Condition filterTemplate = mock(Condition.class);
      Condition mapperTemplate = mock(Condition.class);
      List<Condition> conditions = List.of(filterTemplate, mapperTemplate);

      doReturn(conditions).when(conditionService).findAllConditionsByStepId(stepId);
      when(conditionUtils.isMapperCondition(filterTemplate)).thenReturn(false);
      when(conditionUtils.isMapperCondition(mapperTemplate)).thenReturn(true);

      List<ConditionService.ExecutionBatch> expected =
          List.of(new ConditionService.ExecutionBatch("{\"IPv4\":\"10.10.10.10\"}", List.of()));
      doReturn(expected)
          .when(conditionService)
          .prepareInputsForStepExecution(stepTemplate, workflowRun, List.of(mapperTemplate));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertEquals(expected, result);
      verify(conditionService)
          .prepareInputsForStepExecution(stepTemplate, workflowRun, List.of(mapperTemplate));
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
      assertEquals("step-B", condition.getConditionSteps().getFirst().getStep().getId());
    }

    @Test
    void shouldPreserveExcludedCondition_whenUnlinkedButInExclusionList() {
      String removedStepId = "step-A";

      Condition condition = new Condition();
      condition.setId("cond-excluded");
      Step stepA = new Step();
      stepA.setId(removedStepId);
      conditionService.linkToStep(condition, stepA, true);

      when(conditionRepository.findAllLinkedToStepId(removedStepId)).thenReturn(List.of(condition));
      when(conditionRepository.save(any(Condition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      conditionService.deleteAllConditionsByStepId(removedStepId, List.of("cond-excluded"));

      verify(conditionRepository).save(condition);
      verify(conditionRepository, never()).delete(any());
      assertTrue(condition.getConditionSteps().isEmpty());
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
      childInput.setKeyType(ConditionKeyType.Portscan);
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
      childInput.setKeyType(ConditionKeyType.Status);
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

  /* ============================================================
   * MappingTypeResolution
   * ============================================================ */
  @Nested
  class MappingTypeResolution {

    /** MAPPER condition with explicit LOCAL → stays LOCAL. */
    @Test
    void shouldPreserveMappingType_whenMapperConditionHasExplicitValue() {
      // -------- Prepare --------
      ConditionCreateInput mapperInput = new ConditionCreateInput();
      mapperInput.setTemporaryId("tmp-mapper");
      mapperInput.setType(ConditionType.MAPPER);
      mapperInput.setMappingType(MappingType.LOCAL);

      EventInput input =
          EventInput.builder()
              .name("ev-mr")
              .workflowId("wf-mr")
              .conditions(List.of(mapperInput))
              .build();

      when(conditionRepository.save(any(Condition.class))).thenAnswer(inv -> inv.getArgument(0));

      // -------- Act --------
      Condition root = conditionService.createConditionTree(input);

      // -------- Assert --------
      assertEquals(MappingType.LOCAL, root.getMappingType());
    }

    /** MAPPER condition with no mappingType → defaults to DEFAULT. */
    @Test
    void shouldDefaultMappingTypeToDefault_whenMapperConditionHasNullMappingType() {
      // -------- Prepare --------
      ConditionCreateInput mapperInput = new ConditionCreateInput();
      mapperInput.setTemporaryId("tmp-mapper");
      mapperInput.setType(ConditionType.MAPPER);
      mapperInput.setMappingType(null); // not provided — should be auto-defaulted

      EventInput input =
          EventInput.builder()
              .name("ev-def")
              .workflowId("wf-def")
              .conditions(List.of(mapperInput))
              .build();

      when(conditionRepository.save(any(Condition.class))).thenAnswer(inv -> inv.getArgument(0));

      // -------- Act --------
      Condition root = conditionService.createConditionTree(input);

      // -------- Assert --------
      assertEquals(
          MappingType.DEFAULT,
          root.getMappingType(),
          "mappingType should be auto-defaulted to DEFAULT for MAPPER conditions");
    }

    /** Non-MAPPER condition never carries a mappingType. */
    @Test
    void shouldLeaveMappingTypeNull_whenNonMapperCondition() {
      // -------- Prepare --------
      ConditionCreateInput eqInput = new ConditionCreateInput();
      eqInput.setTemporaryId("tmp-eq");
      eqInput.setType(ConditionType.EQ);
      eqInput.setValue("445");

      EventInput input =
          EventInput.builder()
              .name("ev-nm")
              .workflowId("wf-nm")
              .conditions(List.of(eqInput))
              .build();

      when(conditionRepository.save(any(Condition.class))).thenAnswer(inv -> inv.getArgument(0));

      // -------- Act --------
      Condition root = conditionService.createConditionTree(input);

      // -------- Assert --------
      assertNull(root.getMappingType(), "mappingType must be null for non-MAPPER conditions");
    }
  }

  /* ============================================================
   * isFilterConditionValid / evaluateLeafCondition
   * ============================================================ */
  @Nested
  class IsFilterConditionValid {

    private Condition leaf(ConditionType type, String value) {
      Condition c = new Condition();
      c.setType(type);
      c.setValue(value);
      return c;
    }

    // -- null root filter --

    @Test
    void shouldReturnTrue_whenRootFilterIsNull() {
      // -------- Act / Assert --------
      assertTrue(conditionUtils.isFilterConditionValid("anything", null));
    }

    // -- AND logical operator --

    @Test
    void shouldReturnTrue_whenAndNode_withNoChildren() {
      // allMatch on an empty stream is vacuously true
      // -------- Prepare --------
      Condition and = new Condition();
      and.setType(ConditionType.AND);

      // -------- Act / Assert --------
      assertTrue(conditionUtils.isFilterConditionValid("x", and));
    }

    @Test
    void shouldReturnTrue_whenAndNode_allChildrenPass() {
      // -------- Prepare --------
      Condition and = new Condition();
      and.setType(ConditionType.AND);
      and.getConditionChildren().add(leaf(ConditionType.IS_NOT_NULL, null));
      and.getConditionChildren().add(leaf(ConditionType.EQ, "admin"));

      // -------- Act / Assert --------
      assertTrue(conditionUtils.isFilterConditionValid("admin", and));
    }

    @Test
    void shouldReturnFalse_whenAndNode_oneChildFails() {
      // -------- Prepare --------
      Condition and = new Condition();
      and.setType(ConditionType.AND);
      and.getConditionChildren().add(leaf(ConditionType.IS_NOT_NULL, null)); // passes
      and.getConditionChildren().add(leaf(ConditionType.EQ, "admin")); // fails for "other"

      // -------- Act / Assert --------
      assertFalse(conditionUtils.isFilterConditionValid("other", and));
    }

    // -- OR logical operator --

    @Test
    void shouldReturnFalse_whenOrNode_withNoChildren() {
      // anyMatch on an empty stream is false
      // -------- Prepare --------
      Condition or = new Condition();
      or.setType(ConditionType.OR);

      // -------- Act / Assert --------
      assertFalse(conditionUtils.isFilterConditionValid("x", or));
    }

    @Test
    void shouldReturnTrue_whenOrNode_atLeastOneChildPasses() {
      // -------- Prepare --------
      Condition or = new Condition();
      or.setType(ConditionType.OR);
      or.getConditionChildren().add(leaf(ConditionType.EQ, "admin")); // fails for "other"
      or.getConditionChildren().add(leaf(ConditionType.IS_NOT_NULL, null)); // passes for "other"

      // -------- Act / Assert --------
      assertTrue(conditionUtils.isFilterConditionValid("other", or));
    }

    @Test
    void shouldReturnFalse_whenOrNode_allChildrenFail() {
      // -------- Prepare --------
      Condition or = new Condition();
      or.setType(ConditionType.OR);
      or.getConditionChildren().add(leaf(ConditionType.EQ, "admin"));
      or.getConditionChildren().add(leaf(ConditionType.EQ, "root"));

      // -------- Act / Assert --------
      assertFalse(conditionUtils.isFilterConditionValid("other", or));
    }

    /* ----------------------------------------------------------
     * LeafConditions — exercises evaluateLeafCondition for every
     * ConditionType branch handled in the private switch.
     * ---------------------------------------------------------- */
    @Nested
    class LeafConditions {

      // -- IS_NULL --

      @Test
      void isNull_shouldReturnTrue_whenValueIsNull() {
        assertTrue(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.IS_NULL, null)));
      }

      @Test
      void isNull_shouldReturnFalse_whenValueIsNotNull() {
        assertFalse(
            conditionUtils.isFilterConditionValid("val", leaf(ConditionType.IS_NULL, null)));
      }

      // -- IS_NOT_NULL --

      @Test
      void isNotNull_shouldReturnTrue_whenValueIsNotNull() {
        assertTrue(
            conditionUtils.isFilterConditionValid("val", leaf(ConditionType.IS_NOT_NULL, null)));
      }

      @Test
      void isNotNull_shouldReturnFalse_whenValueIsNull() {
        assertFalse(
            conditionUtils.isFilterConditionValid(null, leaf(ConditionType.IS_NOT_NULL, null)));
      }

      // -- EQ --

      @Test
      void eq_shouldReturnTrue_whenValuesMatch_caseInsensitive() {
        assertTrue(conditionUtils.isFilterConditionValid("Admin", leaf(ConditionType.EQ, "admin")));
      }

      @Test
      void eq_shouldReturnFalse_whenValuesDontMatch() {
        assertFalse(conditionUtils.isFilterConditionValid("root", leaf(ConditionType.EQ, "admin")));
      }

      @Test
      void eq_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.EQ, "admin")));
      }

      // -- NEQ --

      @Test
      void neq_shouldReturnFalse_whenValuesMatch() {
        assertFalse(
            conditionUtils.isFilterConditionValid("admin", leaf(ConditionType.NEQ, "admin")));
      }

      @Test
      void neq_shouldReturnTrue_whenValuesDontMatch() {
        assertTrue(conditionUtils.isFilterConditionValid("root", leaf(ConditionType.NEQ, "admin")));
      }

      @Test
      void neq_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.NEQ, "admin")));
      }

      // -- IN --

      @Test
      void in_shouldReturnTrue_whenValueIsInTargetList() {
        assertTrue(
            conditionUtils.isFilterConditionValid(
                "admin", leaf(ConditionType.IN, "admin, root, guest")));
      }

      @Test
      void in_shouldReturnFalse_whenValueIsNotInTargetList() {
        assertFalse(
            conditionUtils.isFilterConditionValid(
                "unknown", leaf(ConditionType.IN, "admin, root")));
      }

      @Test
      void in_shouldBeCaseInsensitive() {
        assertTrue(
            conditionUtils.isFilterConditionValid("ADMIN", leaf(ConditionType.IN, "admin, root")));
      }

      @Test
      void in_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.IN, "admin")));
      }

      @Test
      void in_shouldReturnFalse_whenTargetIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid("admin", leaf(ConditionType.IN, null)));
      }

      // -- NIN --

      @Test
      void nin_shouldReturnFalse_whenValueIsInTargetList() {
        assertFalse(
            conditionUtils.isFilterConditionValid("admin", leaf(ConditionType.NIN, "admin, root")));
      }

      @Test
      void nin_shouldReturnTrue_whenValueIsNotInTargetList() {
        assertTrue(
            conditionUtils.isFilterConditionValid(
                "unknown", leaf(ConditionType.NIN, "admin, root")));
      }

      @Test
      void nin_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.NIN, "admin")));
      }

      // -- GT --

      @Test
      void gt_shouldReturnTrue_whenActualIsGreaterThanTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("10", leaf(ConditionType.GT, "5")));
      }

      @Test
      void gt_shouldReturnFalse_whenActualIsEqualToTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("5", leaf(ConditionType.GT, "5")));
      }

      @Test
      void gt_shouldReturnFalse_whenActualIsLessThanTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("3", leaf(ConditionType.GT, "5")));
      }

      @Test
      void gt_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.GT, "5")));
      }

      @Test
      void gt_shouldReturnFalse_whenActualValueIsNotNumeric() {
        assertFalse(conditionUtils.isFilterConditionValid("abc", leaf(ConditionType.GT, "5")));
      }

      // -- GTE --

      @Test
      void gte_shouldReturnTrue_whenActualIsGreaterThanTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("10", leaf(ConditionType.GTE, "5")));
      }

      @Test
      void gte_shouldReturnTrue_whenActualIsEqualToTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("5", leaf(ConditionType.GTE, "5")));
      }

      @Test
      void gte_shouldReturnFalse_whenActualIsLessThanTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("3", leaf(ConditionType.GTE, "5")));
      }

      @Test
      void gte_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.GTE, "5")));
      }

      // -- LT --

      @Test
      void lt_shouldReturnTrue_whenActualIsLessThanTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("3", leaf(ConditionType.LT, "5")));
      }

      @Test
      void lt_shouldReturnFalse_whenActualIsEqualToTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("5", leaf(ConditionType.LT, "5")));
      }

      @Test
      void lt_shouldReturnFalse_whenActualIsGreaterThanTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("10", leaf(ConditionType.LT, "5")));
      }

      @Test
      void lt_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.LT, "5")));
      }

      // -- LTE --

      @Test
      void lte_shouldReturnTrue_whenActualIsLessThanTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("3", leaf(ConditionType.LTE, "5")));
      }

      @Test
      void lte_shouldReturnTrue_whenActualIsEqualToTarget() {
        assertTrue(conditionUtils.isFilterConditionValid("5", leaf(ConditionType.LTE, "5")));
      }

      @Test
      void lte_shouldReturnFalse_whenActualIsGreaterThanTarget() {
        assertFalse(conditionUtils.isFilterConditionValid("10", leaf(ConditionType.LTE, "5")));
      }

      @Test
      void lte_shouldReturnFalse_whenActualValueIsNull() {
        assertFalse(conditionUtils.isFilterConditionValid(null, leaf(ConditionType.LTE, "5")));
      }

      @Test
      void default_shouldReturnTrue_forUnrecognizedLeafType() {
        assertTrue(
            conditionUtils.isFilterConditionValid("any", leaf(ConditionType.DEPEND_ON, "some-id")));
      }
    }
  }
}
