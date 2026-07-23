package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.ConditionUtils;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

      boolean expected = !(type == ConditionType.MAPPER || type == ConditionType.DEPEND_ON);
      // -------- Act --------
      boolean result = conditionUtils.isFilterCondition(condition);

      // -------- Assert --------
      assertEquals(expected, result);
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
      when(filterTemplate.getType()).thenReturn(ConditionType.MAPPER);
      when(conditionUtils.isMapperCondition(filterTemplate)).thenReturn(false);
      when(conditionUtils.isMapperCondition(mapperTemplate)).thenReturn(true);

      List<ConditionService.ExecutionBatch> expected =
          List.of(
              new ConditionService.ExecutionBatch("{\"IPv4\":\"10.10.10.10\"}", List.of(), null));
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
   * prepareInputsForStepExecution — correlated + fallback batching
   * ============================================================ */
  @Nested
  class PrepareInputsForStepExecution {
    private final Gson gson = new Gson();

    private Condition mapper(MappingType mappingType, PrimitiveType keyType, String value) {
      Condition mapper = new Condition();
      mapper.setType(ConditionType.MAPPER);
      mapper.setMappingType(mappingType);
      mapper.setKeyType(keyType);
      mapper.setValue(value);
      return mapper;
    }

    private WorkflowStateEntries.Input input(String key, String... values) {
      return WorkflowStateEntries.Input.builder()
          .key(key)
          .values(new HashSet<>(Set.of(values)))
          .build();
    }

    private WorkflowStateEntries.Correlated correlated(
        String type, WorkflowStateEntries.Pair... values) {
      return WorkflowStateEntries.Correlated.builder()
          .type(type)
          .values(new HashSet<>(Set.of(values)))
          .build();
    }

    private WorkflowStateEntries entries(
        List<WorkflowStateEntries.Input> inputs, List<WorkflowStateEntries.Correlated> correlated) {
      return new WorkflowStateEntries(
          new ArrayList<>(inputs), new ArrayList<>(correlated), new HashSet<>(), new HashSet<>());
    }

    private WorkflowStateEntries entries(
        List<WorkflowStateEntries.Input> inputs,
        List<WorkflowStateEntries.Correlated> correlated,
        Set<String> hashExecution) {
      return new WorkflowStateEntries(
          new ArrayList<>(inputs),
          new ArrayList<>(correlated),
          new HashSet<>(hashExecution),
          new HashSet<>());
    }

    private String hashCombo(Map<String, String> combo) {
      WorkflowStateEntries temp =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      return temp.hashCombo(combo);
    }

    private WorkflowState stateFromEntries(WorkflowStateEntries entries) {
      WorkflowState state = new WorkflowState();
      state.setEntries(gson.toJson(entries));
      return state;
    }

    private JsonObject inputJson(ConditionService.ExecutionBatch batch) {
      return JsonParser.parseString(batch.inputString()).getAsJsonObject();
    }

    @Test
    void given_noCorrelatedData_should_buildFallbackCartesianBatches() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-no-correlated");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.GLOBAL, PrimitiveType.IPv4, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Port, null));

      WorkflowStateEntries globalEntries =
          entries(
              List.of(input("IPv4", "10.0.0.1", "10.0.0.2"), input("Port", "80", "443")),
              List.of());
      WorkflowStateEntries localEntries = entries(List.of(), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-no-correlated"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(4, batches.size());
      Set<String> pairs =
          batches.stream()
              .map(
                  b -> {
                    JsonObject json = inputJson(b);
                    return json.get("IPv4").getAsString() + ":" + json.get("Port").getAsString();
                  })
              .collect(java.util.stream.Collectors.toSet());
      assertEquals(Set.of("10.0.0.1:80", "10.0.0.1:443", "10.0.0.2:80", "10.0.0.2:443"), pairs);
    }

    @Test
    void given_localAndGlobalSubsetCorrelation_should_completeUncoveredKeyFromMappedPool() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-subset");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.LOCAL, PrimitiveType.IPv4, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Port, null),
              mapper(MappingType.DEFAULT, PrimitiveType.Text, "static-default"));

      WorkflowStateEntries localEntries =
          entries(
              List.of(input("IPv4", "10.0.0.1")),
              List.of(correlated("LocalIp", new WorkflowStateEntries.Pair("IPv4", "10.0.0.1"))));
      WorkflowStateEntries globalEntries = entries(List.of(input("Port", "80", "443")), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-subset"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(2, batches.size());
      for (ConditionService.ExecutionBatch batch : batches) {
        JsonObject json = inputJson(batch);
        assertEquals("10.0.0.1", json.get("IPv4").getAsString());
        assertTrue(Set.of("80", "443").contains(json.get("Port").getAsString()));
        assertEquals("static-default", json.get("Text").getAsString());
      }
    }

    @Test
    void given_localCorrelatedCoversRequiredKeys_shouldAlsoGenerateBestEffortCombinations() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-local-priority");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.LOCAL, PrimitiveType.Port, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Host, null));

      WorkflowStateEntries localEntries =
          entries(
              List.of(input("Port", "5040")),
              List.of(
                  correlated(
                      "PortsScan",
                      new WorkflowStateEntries.Pair("Host", "0.0.0.0"),
                      new WorkflowStateEntries.Pair("Port", "5040"),
                      new WorkflowStateEntries.Pair("Service", "TCP"))));
      WorkflowStateEntries globalEntries =
          entries(List.of(input("Host", "0.0.0.0", "1.1.1.1", "2.2.2.2", "3.3.3.3")), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-local-priority"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(4, batches.size());
      Set<String> hostPorts =
          batches.stream()
              .map(
                  b -> {
                    JsonObject json = inputJson(b);
                    return json.get("Host").getAsString() + ":" + json.get("Port").getAsString();
                  })
              .collect(java.util.stream.Collectors.toSet());
      assertEquals(
          Set.of("0.0.0.0:5040", "1.1.1.1:5040", "2.2.2.2:5040", "3.3.3.3:5040"), hostPorts);
    }

    @Test
    void given_localMappersAndCorrelatedOnly_should_generateBatchEvenIfHostNotInInputs() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-local-correlated-only");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.LOCAL, PrimitiveType.Port, null),
              mapper(MappingType.LOCAL, PrimitiveType.Host, null));

      WorkflowStateEntries localEntries =
          entries(
              List.of(input("Port", "5040")),
              List.of(
                  correlated(
                      "PortsScan",
                      new WorkflowStateEntries.Pair("Host", "0.0.0.0"),
                      new WorkflowStateEntries.Pair("Port", "5040"),
                      new WorkflowStateEntries.Pair("Service", "TCP"))));

      when(workflowStateService.getGlobalStateByWorkflowId("wf-local-correlated-only"))
          .thenReturn(stateFromEntries(entries(List.of(), List.of())));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(1, batches.size());
      JsonObject json = inputJson(batches.getFirst());
      assertEquals("5040", json.get("Port").getAsString());
      assertEquals("0.0.0.0", json.get("Host").getAsString());
    }

    @Test
    void
        given_portInLocalCorrelatedAndHostInGlobalState_should_completeHostFromGlobalMappingType() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-local-port-global-host");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.LOCAL, PrimitiveType.Port, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Host, null));

      WorkflowStateEntries localEntries =
          entries(
              List.of(),
              List.of(
                  correlated(
                      "PortsScan",
                      new WorkflowStateEntries.Pair("Port", "5040"),
                      new WorkflowStateEntries.Pair("Filename", "scan.txt"))));
      WorkflowStateEntries globalEntries =
          entries(List.of(input("Host", "1.1.1.1", "2.2.2.2")), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-local-port-global-host"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(2, batches.size());
      Set<String> hostPorts =
          batches.stream()
              .map(
                  b -> {
                    JsonObject json = inputJson(b);
                    return json.get("Host").getAsString() + ":" + json.get("Port").getAsString();
                  })
              .collect(java.util.stream.Collectors.toSet());
      assertEquals(Set.of("1.1.1.1:5040", "2.2.2.2:5040"), hostPorts);
    }

    @Test
    void given_fullCorrelatedCoverageButAlreadyExecuted_shouldKeepBestEffortCombinations() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-local-priority-executed");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.LOCAL, PrimitiveType.Port, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Host, null));

      Map<String, String> executedCombo = new java.util.TreeMap<>();
      executedCombo.put("Host", "0.0.0.0");
      executedCombo.put("Port", "5040");
      String executedHash = hashCombo(executedCombo);

      WorkflowStateEntries localEntries =
          entries(
              List.of(input("Port", "5040")),
              List.of(
                  correlated(
                      "PortsScan",
                      new WorkflowStateEntries.Pair("Host", "0.0.0.0"),
                      new WorkflowStateEntries.Pair("Port", "5040"),
                      new WorkflowStateEntries.Pair("Service", "TCP"))),
              Set.of(executedHash));
      WorkflowStateEntries globalEntries =
          entries(List.of(input("Host", "0.0.0.0", "1.1.1.1", "2.2.2.2", "3.3.3.3")), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-local-priority-executed"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(3, batches.size());
      Set<String> hostPorts =
          batches.stream()
              .map(
                  b -> {
                    JsonObject json = inputJson(b);
                    return json.get("Host").getAsString() + ":" + json.get("Port").getAsString();
                  })
              .collect(java.util.stream.Collectors.toSet());
      assertEquals(Set.of("1.1.1.1:5040", "2.2.2.2:5040", "3.3.3.3:5040"), hostPorts);
    }

    @Test
    void given_supersetCorrelatedTuple_should_ignoreExtraKeysOutsideMapperScope() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-superset");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.GLOBAL, PrimitiveType.IPv4, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Port, null));

      WorkflowStateEntries globalEntries =
          entries(
              List.of(input("IPv4", "10.0.0.1"), input("Port", "443")),
              List.of(
                  correlated(
                      "RichTuple",
                      new WorkflowStateEntries.Pair("IPv4", "10.0.0.1"),
                      new WorkflowStateEntries.Pair("Port", "443"),
                      new WorkflowStateEntries.Pair("Text", "folder-A"))));
      WorkflowStateEntries localEntries = entries(List.of(), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-superset"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(1, batches.size());
      JsonObject json = inputJson(batches.getFirst());
      assertEquals("10.0.0.1", json.get("IPv4").getAsString());
      assertEquals("443", json.get("Port").getAsString());
      assertFalse(json.has("Text"));
    }

    @Test
    void given_correlatedAndFallbackSameCombo_should_deduplicateToSingleBatch() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-dedup");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.GLOBAL, PrimitiveType.IPv4, null),
              mapper(MappingType.GLOBAL, PrimitiveType.Port, null));

      WorkflowStateEntries globalEntries =
          entries(
              List.of(input("IPv4", "10.0.0.9"), input("Port", "8443")),
              List.of(
                  correlated(
                      "HostPort",
                      new WorkflowStateEntries.Pair("IPv4", "10.0.0.9"),
                      new WorkflowStateEntries.Pair("Port", "8443"))));
      WorkflowStateEntries localEntries = entries(List.of(), List.of());

      when(workflowStateService.getGlobalStateByWorkflowId("wf-dedup"))
          .thenReturn(stateFromEntries(globalEntries));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(localEntries));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(1, batches.size());
      JsonObject json = inputJson(batches.getFirst());
      assertEquals("10.0.0.9", json.get("IPv4").getAsString());
      assertEquals("8443", json.get("Port").getAsString());
    }

    @Test
    void given_onlyDefaultMappers_should_returnSingleBatchWithDefaults() {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-default-only");

      List<Condition> mappers =
          List.of(
              mapper(MappingType.DEFAULT, PrimitiveType.Text, "admin"),
              mapper(MappingType.DEFAULT, PrimitiveType.Host, "worker-01"));

      when(workflowStateService.getGlobalStateByWorkflowId("wf-default-only"))
          .thenReturn(stateFromEntries(entries(List.of(), List.of())));
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(stateFromEntries(entries(List.of(), List.of())));

      // -------- Act --------
      List<ConditionService.ExecutionBatch> batches =
          conditionService.prepareInputsForStepExecution(stepTemplate, workflowRun, mappers);

      // -------- Assert --------
      assertEquals(1, batches.size());
      JsonObject json = inputJson(batches.getFirst());
      assertEquals("admin", json.get("Text").getAsString());
      assertEquals("worker-01", json.get("Host").getAsString());
      assertNotNull(batches.getFirst().hash());
    }
  }

  /* ============================================================
   * checkCondition — DEPEND_ON evaluation
   * ============================================================ */
  @Nested
  class CheckConditionDependOn {

    @Test
    void given_dependOnConditionSatisfied_should_returnDirectBatch() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      String dependentStepTemplateId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition dependOnCondition = new Condition();
      dependOnCondition.setType(ConditionType.DEPEND_ON);
      dependOnCondition.setValue(dependentStepTemplateId);

      doReturn(List.of(dependOnCondition)).when(conditionService).findAllConditionsByStepId(stepId);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dependentStepTemplateId, workflowId))
          .thenReturn(true);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("{\"in\":1}", result.getFirst().inputString());
    }

    @Test
    void given_dependOnConditionNotSatisfied_should_returnEmptyList() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      String dependentStepTemplateId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition dependOnCondition = new Condition();
      dependOnCondition.setType(ConditionType.DEPEND_ON);
      dependOnCondition.setValue(dependentStepTemplateId);

      doReturn(List.of(dependOnCondition)).when(conditionService).findAllConditionsByStepId(stepId);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dependentStepTemplateId, workflowId))
          .thenReturn(false);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void given_dependOnConditionWithNullValue_should_returnEmptyList() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      Condition dependOnCondition = new Condition();
      dependOnCondition.setType(ConditionType.DEPEND_ON);
      dependOnCondition.setValue(null);

      doReturn(List.of(dependOnCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void given_dependOnConditionWithBlankValue_should_returnEmptyList() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      when(stepTemplate.getId()).thenReturn(stepId);

      Condition dependOnCondition = new Condition();
      dependOnCondition.setType(ConditionType.DEPEND_ON);
      dependOnCondition.setValue("   ");

      doReturn(List.of(dependOnCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void given_multipleDependOnConditions_allSatisfied_should_returnDirectBatch()
        throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      String dep1 = UUID.randomUUID().toString();
      String dep2 = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition cond1 = new Condition();
      cond1.setType(ConditionType.DEPEND_ON);
      cond1.setValue(dep1);

      Condition cond2 = new Condition();
      cond2.setType(ConditionType.DEPEND_ON);
      cond2.setValue(dep2);

      doReturn(List.of(cond1, cond2)).when(conditionService).findAllConditionsByStepId(stepId);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dep1, workflowId)).thenReturn(true);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dep2, workflowId)).thenReturn(true);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("{\"in\":1}", result.getFirst().inputString());
    }

    @Test
    void given_multipleDependOnConditions_oneFails_should_returnEmptyList()
        throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();
      String dep1 = UUID.randomUUID().toString();
      String dep2 = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition cond1 = new Condition();
      cond1.setType(ConditionType.DEPEND_ON);
      cond1.setValue(dep1);

      Condition cond2 = new Condition();
      cond2.setType(ConditionType.DEPEND_ON);
      cond2.setValue(dep2);

      doReturn(List.of(cond1, cond2)).when(conditionService).findAllConditionsByStepId(stepId);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dep1, workflowId)).thenReturn(true);
      when(stepRepository.existsByStepTemplateIdAndWorkflowId(dep2, workflowId)).thenReturn(false);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  /* ============================================================
   * checkCondition — FILTER evaluation (EQ conditions)
   * ============================================================ */
  @Nested
  class CheckConditionFilterEq {

    private WorkflowState buildWorkflowState(String entries) {
      WorkflowState state = new WorkflowState();
      state.setEntries(entries);
      return state;
    }

    private String buildStateEntriesJson(String key, Set<String> values) {
      StringBuilder valuesJson = new StringBuilder("[");
      boolean first = true;
      for (String v : values) {
        if (!first) valuesJson.append(",");
        valuesJson.append("\"").append(v).append("\"");
        first = false;
      }
      valuesJson.append("]");
      return "{\"inputs\":[{\"key\":\""
          + key
          + "\",\"values\":"
          + valuesJson
          + "}],\"correlated\":[],\"hashExecution\":[],\"executionKeys\":[]}";
    }

    private String buildEmptyStateEntriesJson() {
      return "{\"inputs\":[],\"correlated\":[],\"hashExecution\":[],\"executionKeys\":[]}";
    }

    @Test
    void given_eqFilterMatchingGlobalState_should_returnDirectBatch() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState globalState =
          buildWorkflowState(buildStateEntriesJson("IPv4", Set.of("10.0.0.1")));
      WorkflowState localState = buildWorkflowState(buildEmptyStateEntriesJson());

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("{\"in\":1}", result.getFirst().inputString());
    }

    @Test
    void given_eqFilterNotMatchingGlobalState_should_returnEmptyList() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState globalState =
          buildWorkflowState(buildStateEntriesJson("IPv4", Set.of("192.168.0.1")));
      WorkflowState localState = buildWorkflowState(buildEmptyStateEntriesJson());

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void given_eqFilterMatchingLocalState_should_returnDirectBatch() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState globalState = buildWorkflowState(buildEmptyStateEntriesJson());
      WorkflowState localState =
          buildWorkflowState(buildStateEntriesJson("IPv4", Set.of("10.0.0.1")));

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void given_eqFilterWithNullGlobalState_should_handleGracefully() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState localState =
          buildWorkflowState(buildStateEntriesJson("IPv4", Set.of("10.0.0.1")));

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(null);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void given_eqFilterWithKeyNotInState_should_returnEmptyList() throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      // State has a different key — IPv4 won't be found
      WorkflowState globalState =
          buildWorkflowState(buildStateEntriesJson("Portscan", Set.of("445")));
      WorkflowState localState = buildWorkflowState(buildEmptyStateEntriesJson());

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void given_eqFilterWithMultipleValues_oneMatching_should_returnDirectBatch()
        throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState globalState =
          buildWorkflowState(
              buildStateEntriesJson("IPv4", Set.of("192.168.0.1", "10.0.0.1", "172.16.0.1")));
      WorkflowState localState = buildWorkflowState(buildEmptyStateEntriesJson());

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void given_eqFilterSatisfied_andNoMappers_should_returnDirectBatchWithOriginalInput()
        throws ChainingException {
      // -------- Arrange --------
      Step stepTemplate = mock(Step.class);
      Workflow workflowRun = mock(Workflow.class);

      String stepId = UUID.randomUUID().toString();
      String workflowId = UUID.randomUUID().toString();

      when(stepTemplate.getId()).thenReturn(stepId);
      when(workflowRun.getId()).thenReturn(workflowId);

      Condition eqCondition = new Condition();
      eqCondition.setType(ConditionType.EQ);
      eqCondition.setKeyType(PrimitiveType.IPv4);
      eqCondition.setValue("10.0.0.1");

      doReturn(List.of(eqCondition)).when(conditionService).findAllConditionsByStepId(stepId);

      WorkflowState globalState =
          buildWorkflowState(buildStateEntriesJson("IPv4", Set.of("10.0.0.1")));
      WorkflowState localState = buildWorkflowState(buildEmptyStateEntriesJson());

      when(workflowStateService.getGlobalStateByWorkflowId(workflowId)).thenReturn(globalState);
      when(workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun))
          .thenReturn(localState);

      // -------- Act --------
      List<ConditionService.ExecutionBatch> result =
          conditionService.checkCondition(stepTemplate, workflowRun, "{\"in\":1}");

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("{\"in\":1}", result.getFirst().inputString());
      assertTrue(result.getFirst().usedMappers().isEmpty());
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
      childInput.setKeyType(PrimitiveType.Port);
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
      childInput.setKeyType(PrimitiveType.Text);
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
      return leaf(type, value, true);
    }

    private Condition leaf(ConditionType type, String value, boolean caseSensitive) {
      Condition c = new Condition();
      c.setType(type);
      c.setValue(value);
      c.setCaseSensitive(caseSensitive);
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
        assertTrue(
            conditionUtils.isFilterConditionValid("Admin", leaf(ConditionType.EQ, "admin", false)));
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
            conditionUtils.isFilterConditionValid(
                "ADMIN", leaf(ConditionType.IN, "admin, root", false)));
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
