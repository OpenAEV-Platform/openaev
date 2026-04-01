package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowState;
import io.openaev.database.model.WorkflowStateEntries;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowStateService Tests")
class WorkflowStateServiceTest {

  @Mock private WorkflowStateRepository workflowStateRepository;
  @Mock private ConditionService conditionService;
  @Mock private InjectorContractContentUtils injectorContractContentUtils;
  @Mock private StructuredOutputUtils structuredOutputUtils;

  @InjectMocks private WorkflowStateService workflowStateService;

  private final Gson gson = new Gson();
  private final ObjectMapper objectMapper = new ObjectMapper();

  // ========================================================================
  // createLocalState Tests
  // ========================================================================
  @Nested
  @DisplayName("createLocalState")
  class CreateStateTests {

    @Captor private ArgumentCaptor<WorkflowState> workflowStateCaptor;

    @Test
    @DisplayName("should create state with correct properties")
    void shouldCreateStateWithCorrectProperties() {
      // Prepare
      Set<String> executionKeys = Set.of("key1", "key2");
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act
      workflowStateService.createLocalState(executionKeys, stepTemplate, workflowExecution);

      // Assert
      verify(workflowStateRepository).save(workflowStateCaptor.capture());
      WorkflowState savedState = workflowStateCaptor.getValue();

      assertNotNull(savedState);
      assertEquals(stepTemplate, savedState.getStepTemplate());
      assertEquals(workflowExecution, savedState.getWorkflowExecution());
      assertNotNull(savedState.getEntries());
    }

    @Test
    @DisplayName("should throw exception with empty execution keys")
    void shouldNotCreateStateWithEmptyExecutionKeys_Throw() {
      // Prepare
      Set<String> executionKeys = Collections.emptySet();
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () ->
              workflowStateService.createLocalState(
                  executionKeys, stepTemplate, workflowExecution));
    }

    @Test
    @DisplayName("should throw exception with null execution keys")
    void shouldNotCreateStateWithNullExecutionKeys_Throw() {
      // Prepare
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act + Assert
      assertThrows(
          IllegalArgumentException.class,
          () -> workflowStateService.createLocalState(null, stepTemplate, workflowExecution));
    }

    @Test
    @DisplayName("should create state with single execution key")
    void shouldCreateStateWithSingleExecutionKey() {
      // Prepare
      Set<String> executionKeys = Set.of("singleKey");
      Step stepTemplate = mock(Step.class);
      Workflow workflowExecution = mock(Workflow.class);

      // Act
      workflowStateService.createLocalState(executionKeys, stepTemplate, workflowExecution);

      // Assert
      verify(workflowStateRepository).save(workflowStateCaptor.capture());
      assertNotNull(workflowStateCaptor.getValue());
      verifyNoMoreInteractions(workflowStateRepository);
    }
  }

  // ========================================================================
  // getState Tests
  // ========================================================================
  @Nested
  @DisplayName("getState")
  class GetStateTests {

    @Captor private ArgumentCaptor<String> stepTemplateIdCaptor;
    @Captor private ArgumentCaptor<String> workflowExecutionIdCaptor;

    @Test
    @DisplayName("should return state entries when found")
    void shouldReturnStateEntriesWhenFound() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      WorkflowStateEntries expectedEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("key1"));
      String entriesJson = gson.toJson(expectedEntries);

      WorkflowState workflowState = mock(WorkflowState.class);
      when(workflowState.getEntries()).thenReturn(entriesJson);
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(workflowState);

      // Act
      WorkflowStateEntries result =
          workflowStateService.getState(stepTemplateId, workflowExecutionId);

      // Assert
      verify(workflowStateRepository)
          .findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateIdCaptor.capture(), workflowExecutionIdCaptor.capture());
      assertEquals(stepTemplateId, stepTemplateIdCaptor.getValue());
      assertEquals(workflowExecutionId, workflowExecutionIdCaptor.getValue());
      assertNotNull(result);
    }

    @Test
    @DisplayName("should throw exception when state not found")
    void shouldThrowExceptionWhenNotFound() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(null);

      // Act & Assert
      assertThrows(
          NullPointerException.class,
          () -> workflowStateService.getState(stepTemplateId, workflowExecutionId));
    }

    @Test
    @DisplayName("should return state with execution keys")
    void shouldReturnStateWithExecutionKeys() {
      // Prepare
      String stepTemplateId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();

      Set<String> executionKeys = Set.of("key1", "key2", "key3");
      WorkflowStateEntries expectedEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), executionKeys);
      String entriesJson = gson.toJson(expectedEntries);

      WorkflowState workflowState = mock(WorkflowState.class);
      when(workflowState.getEntries()).thenReturn(entriesJson);
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(workflowState);

      // Act
      WorkflowStateEntries result =
          workflowStateService.getState(stepTemplateId, workflowExecutionId);

      // Assert
      assertNotNull(result);
      assertEquals(executionKeys, result.getExecutionKeys());
    }
  }

  // ========================================================================
  // newOutput Tests
  // ========================================================================
  @Nested
  @DisplayName("newOutput")
  class NewOutputTests {

    @Test
    @DisplayName("should add new value to input when path is not correlated")
    void shouldAddNewValueToInputWhenPathNotCorrelated() {
      // Prepare
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"test-value\"}}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("test-value"));
    }

    @Test
    @DisplayName("should not add duplicate value to input")
    void shouldNotAddDuplicateValueToInput() {
      // Prepare
      String key = "stdout";
      String path = "outputs.message.stdout";
      String output = "{\"outputs\":{\"message\":{\"stdout\":\"existing-value\"}}}";

      Set<String> existingValues = new HashSet<>();
      existingValues.add("existing-value");
      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, existingValues);
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertEquals(1, input.getValues().size());
    }

    @Test
    @DisplayName("should handle correlated path")
    void shouldHandleCorrelatedPath() {
      // Prepare
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      WorkflowStateEntries stateEntries = mock(WorkflowStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(new HashMap<>());
      when(stateEntries.getCorrelated()).thenReturn(new ArrayList<>());

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      verify(stateEntries).isPathCorrelated(path);
      verify(stateEntries).pathCorrelated(path);
    }

    @Test
    @DisplayName("should not add correlated when already exists")
    void shouldNotAddCorrelatedWhenAlreadyExists() {
      // Prepare
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      Set<WorkflowStateEntries.Pair> existingPairs = new HashSet<>();
      existingPairs.add(new WorkflowStateEntries.Pair("ip", "192.168.1.1"));
      existingPairs.add(new WorkflowStateEntries.Pair("port", "8080"));

      Map<Set<WorkflowStateEntries.Pair>, WorkflowStateEntries.Correlated> existingIndex =
          new HashMap<>();
      existingIndex.put(existingPairs, new WorkflowStateEntries.Correlated(existingPairs));

      WorkflowStateEntries stateEntries = mock(WorkflowStateEntries.class);
      when(stateEntries.isPathCorrelated(path)).thenReturn(true);
      when(stateEntries.pathCorrelated(path)).thenReturn(correlatedPaths);
      when(stateEntries.getIndexCorrelatedInput()).thenReturn(existingIndex);

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      verify(stateEntries, never()).getCorrelated();
    }
  }

  // ========================================================================
  // getValues Tests (tested indirectly through newOutput)
  // ========================================================================
  @Nested
  @DisplayName("getValues (private method - tested via newOutput)")
  class GetValuesTests {

    @Test
    @DisplayName("should extract primitive string value")
    void shouldExtractPrimitiveStringValue() {
      // Prepare
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":\"hello world\"}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("hello world"));
    }

    @Test
    @DisplayName("should handle null value in output")
    void shouldHandleNullValueInOutput() {
      // Prepare
      String key = "message";
      String path = "outputs.message";
      String output = "{\"outputs\":{\"message\":null}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().isEmpty() || input.getValues().contains(null));
    }

    @Test
    @DisplayName("should extract numeric value as string")
    void shouldExtractNumericValueAsString() {
      // Prepare
      String key = "count";
      String path = "outputs.count";
      String output = "{\"outputs\":{\"count\":42}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("42"));
    }

    @Test
    @DisplayName("should extract boolean value as string")
    void shouldExtractBooleanValueAsString() {
      // Prepare
      String key = "enabled";
      String path = "outputs.enabled";
      String output = "{\"outputs\":{\"enabled\":true}}";

      WorkflowStateEntries.Input input = new WorkflowStateEntries.Input(key, new HashSet<>());
      List<WorkflowStateEntries.Input> inputs = new ArrayList<>();
      inputs.add(input);

      WorkflowStateEntries stateEntries =
          new WorkflowStateEntries(inputs, new ArrayList<>(), new HashSet<>(), Set.of(key));

      // Act
      workflowStateService.newOutput(stateEntries, output, path, key);

      // Assert
      assertTrue(input.getValues().contains("true"));
    }
  }

  // ========================================================================
  // save Tests
  // ========================================================================
  @Nested
  @DisplayName("save")
  class SaveTests {

    @Test
    @DisplayName("should delegate directly to the repository")
    void shouldDelegateToRepository() {
      WorkflowState state = mock(WorkflowState.class);

      workflowStateService.save(state);

      verify(workflowStateRepository).save(state);
      verifyNoMoreInteractions(workflowStateRepository);
    }
  }

  // ========================================================================
  // getGlobalStateByWorkflowId Tests
  // ========================================================================
  @Nested
  @DisplayName("getGlobalStateByWorkflowId")
  class GetGlobalStateByWorkflowIdTests {

    @Test
    @DisplayName("should return global state when found")
    void shouldReturnGlobalState_whenFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      WorkflowState expected = mock(WorkflowState.class);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(expected);

      // Act
      WorkflowState result = workflowStateService.getGlobalStateByWorkflowId(workflowId);

      // Assert
      assertSame(expected, result);
      verify(workflowStateRepository).findByStepTemplateIsNullAndWorkflowExecutionId(workflowId);
    }

    @Test
    @DisplayName("should return null when no global state exists")
    void shouldReturnNull_whenNotFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(null);

      // Act
      WorkflowState result = workflowStateService.getGlobalStateByWorkflowId(workflowId);

      // Assert
      assertNull(result);
    }
  }

  // ========================================================================
  // getLocalStateByWorkflowIdAndStepId Tests
  // ========================================================================
  @Nested
  @DisplayName("Get LocalState By WorkflowId And StepId")
  class GetLocalStateByWorkflowIdAndStepIdTests {

    @Test
    @DisplayName("should return local state when found")
    void shouldReturnLocalState_whenFound() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();
      WorkflowState expected = mock(WorkflowState.class);
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepId, workflowExecutionId))
          .thenReturn(expected);

      // Act
      WorkflowState result =
          workflowStateService.getLocalStateByWorkflowIdAndStepId(stepId, workflowExecutionId);

      // Assert
      assertSame(expected, result);
      verify(workflowStateRepository)
          .findByStepTemplate_IdAndWorkflowExecution_Id(stepId, workflowExecutionId);
    }

    @Test
    @DisplayName("should return null when no local state exists")
    void shouldReturnNull_whenNotFound() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String workflowExecutionId = UUID.randomUUID().toString();
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepId, workflowExecutionId))
          .thenReturn(null);

      // Act
      WorkflowState result =
          workflowStateService.getLocalStateByWorkflowIdAndStepId(stepId, workflowExecutionId);

      // Assert
      assertNull(result);
    }
  }

  // ========================================================================
  // createGlobalState Tests
  // ========================================================================
  @Nested
  @DisplayName("Create Global State")
  class CreateGlobalStateTests {

    @Captor private ArgumentCaptor<WorkflowState> stateCaptor;

    @Test
    @DisplayName("should create and save global state with null step-template")
    void shouldCreateGlobalState_withNoWhitelistRules() {
      // Prepare
      Workflow workflow = mock(Workflow.class);
      when(workflow.getWhitelist()).thenReturn(Collections.emptyList());

      // Act
      WorkflowState result = workflowStateService.createGlobalState(workflow);

      // Assert
      assertNotNull(result);
      // Global state has no linked step template
      assertNull(result.getStepTemplate());
      // scope and entries must be serialised JSON strings
      assertNotNull(result.getScope());
      assertFalse(result.getScope().isBlank());
      assertNotNull(result.getEntries());
      assertFalse(result.getEntries().isBlank());
      assertEquals(workflow, result.getWorkflowExecution());
      verify(workflowStateRepository).save(result);
    }

    @Test
    @DisplayName("should populate scope entries from whitelist rules")
    void shouldCreateGlobalState_withWhitelistRules() {
      // Prepare
      Workflow workflow = mock(Workflow.class);

      WorkflowScopeRule rule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .valueType(ScopeRuleValueType.IP)
              .ruleValue("192.168.1.0/24")
              .build();
      when(workflow.getWhitelist()).thenReturn(List.of(rule));

      // Act
      WorkflowState result = workflowStateService.createGlobalState(workflow);

      // Assert
      assertNotNull(result);
      verify(workflowStateRepository).save(stateCaptor.capture());

      // Scope JSON must contain the rule value
      String scope = stateCaptor.getValue().getScope();
      assertNotNull(scope);
      assertTrue(scope.contains("192.168.1.0/24"), "Scope should contain the whitelisted IP range");
    }

    @Test
    @DisplayName("should return the persisted state instance")
    void shouldReturnSavedStateInstance() {
      // Prepare
      Workflow workflow = mock(Workflow.class);
      when(workflow.getWhitelist()).thenReturn(Collections.emptyList());

      // Act
      WorkflowState result = workflowStateService.createGlobalState(workflow);

      // returned object is the same that was passed to save
      verify(workflowStateRepository).save(result);
    }
  }

  // ========================================================================
  // syncInjectOutputToGlobalState Tests
  // ========================================================================
  @Nested
  @DisplayName("sync InjectOutput To GlobalState")
  class SyncInjectOutputToGlobalStateTests {

    @Test
    @DisplayName("should return early and not save when inject has no status")
    void shouldReturnEarly_whenInjectHasNoStatus() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      when(inject.getStatus()).thenReturn(Optional.empty());

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      verifyNoInteractions(workflowStateRepository);
    }

    @Test
    @DisplayName("should return early and not save when trace list is null")
    void shouldReturnEarly_whenTracesAreNull() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      InjectStatus status = mock(InjectStatus.class);
      when(status.getTraces()).thenReturn(null);
      when(inject.getStatus()).thenReturn(Optional.of(status));

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      verifyNoInteractions(workflowStateRepository);
    }

    @Test
    @DisplayName("should return early without saving when global state is null")
    void shouldReturnEarly_whenGlobalStateIsNull() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      Workflow workflow = mock(Workflow.class);
      String workflowId = UUID.randomUUID().toString();

      InjectStatus status = mock(InjectStatus.class);
      when(status.getTraces()).thenReturn(new ArrayList<>());
      when(inject.getStatus()).thenReturn(Optional.of(status));
      when(inject.getInjectorContract()).thenReturn(Optional.empty());
      when(structuredOutputUtils.extractOutputParsers(inject)).thenReturn(Set.of());
      when(injectorContractContentUtils.getAllContractOutputs(any(Set.class)))
          .thenReturn(List.of());

      when(stepRun.getWorkflow()).thenReturn(workflow);
      when(workflow.getId()).thenReturn(workflowId);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(null);

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip trace when structured output is null")
    void shouldSkipTrace_whenStructuredOutputIsNull() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      Workflow workflow = mock(Workflow.class);
      String workflowId = UUID.randomUUID().toString();
      InjectorContract injectorContract = mock(InjectorContract.class);

      ExecutionTrace trace = mock(ExecutionTrace.class);
      when(trace.getStructuredOutput()).thenReturn(null);

      InjectStatus status = mock(InjectStatus.class);
      when(status.getTraces()).thenReturn(List.of(trace));
      when(inject.getStatus()).thenReturn(Optional.of(status));
      when(inject.getInjectorContract()).thenReturn(Optional.of(injectorContract));
      when(injectorContractContentUtils.getAllContractOutputs(injectorContract))
          .thenReturn(List.of());

      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState globalState =
          WorkflowState.builder().workflowExecution(workflow).entries(gson.toJson(entries)).build();

      when(stepRun.getWorkflow()).thenReturn(workflow);
      when(workflow.getId()).thenReturn(workflowId);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      verify(workflowStateRepository).save(globalState);
    }

    @Test
    @DisplayName("should sync primitive array values into entries when InjectorContract present")
    void shouldSyncPrimitiveArrayValues_whenInjectorContractPresent() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      Workflow workflow = mock(Workflow.class);
      String workflowId = UUID.randomUUID().toString();
      InjectorContract injectorContract = mock(InjectorContract.class);

      // Structured output with an array of primitives for the "ipv4-field" key
      ObjectNode structuredOutput = objectMapper.createObjectNode();
      structuredOutput.putArray("ipv4-field").add("10.0.0.1").add("10.0.0.2");

      ExecutionTrace trace = mock(ExecutionTrace.class);
      when(trace.getStructuredOutput()).thenReturn(structuredOutput);

      InjectStatus status = mock(InjectStatus.class);
      when(status.getTraces()).thenReturn(List.of(trace));
      when(inject.getStatus()).thenReturn(Optional.of(status));
      when(inject.getInjectorContract()).thenReturn(Optional.of(injectorContract));

      // Contract declares "ipv4-field" as IPv4 type
      InjectorContractContentOutputElement element = new InjectorContractContentOutputElement();
      element.setField("ipv4-field");
      element.setType(ContractOutputType.IPv4);
      when(injectorContractContentUtils.getAllContractOutputs(injectorContract))
          .thenReturn(List.of(element));

      // Global state with an IPv4 input bucket
      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState globalState =
          WorkflowState.builder().workflowExecution(workflow).entries(gson.toJson(entries)).build();

      when(stepRun.getWorkflow()).thenReturn(workflow);
      when(workflow.getId()).thenReturn(workflowId);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      verify(workflowStateRepository).save(globalState);
      WorkflowStateEntries updatedEntries =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);
      Set<String> syncedValues =
          updatedEntries.getInputByKey(ConditionKeyType.IPv4.name()).getValues();
      assertTrue(syncedValues.contains("10.0.0.1"));
      assertTrue(syncedValues.contains("10.0.0.2"));
    }

    @Test
    @DisplayName("should use outputParsers when inject has no InjectorContract")
    void shouldUseOutputParsers_whenNoInjectorContract() {
      // Prepare
      Inject inject = mock(Inject.class);
      Step stepRun = mock(Step.class);
      Workflow workflow = mock(Workflow.class);
      String workflowId = UUID.randomUUID().toString();

      InjectStatus status = mock(InjectStatus.class);
      when(status.getTraces()).thenReturn(new ArrayList<>());
      when(inject.getStatus()).thenReturn(Optional.of(status));
      when(inject.getInjectorContract()).thenReturn(Optional.empty());

      OutputParser parser = mock(OutputParser.class);
      when(structuredOutputUtils.extractOutputParsers(inject)).thenReturn(Set.of(parser));
      when(injectorContractContentUtils.getAllContractOutputs(Set.of(parser)))
          .thenReturn(List.of());

      WorkflowStateEntries entries =
          new WorkflowStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of());
      WorkflowState globalState =
          WorkflowState.builder().workflowExecution(workflow).entries(gson.toJson(entries)).build();

      when(stepRun.getWorkflow()).thenReturn(workflow);
      when(workflow.getId()).thenReturn(workflowId);
      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);

      // Act
      workflowStateService.syncInjectOutputToGlobalState(inject, stepRun);

      // Assert
      // extractOutputParsers was used (no InjectorContract branch)
      verify(structuredOutputUtils).extractOutputParsers(inject);
      verify(injectorContractContentUtils).getAllContractOutputs(Set.of(parser));
    }
  }

  // ========================================================================
  // syncMappersToLocalPartition Tests
  // ========================================================================
  @Nested
  @DisplayName("sync Mappers To Local Partition")
  class SyncMappersToLocalPartitionTests {

    private Step buildStepWithId(String id) {
      Step s = mock(Step.class);
      when(s.getId()).thenReturn(id);
      return s;
    }

    private Workflow buildWorkflowWithId(String id) {
      Workflow w = mock(Workflow.class);
      when(w.getId()).thenReturn(id);
      return w;
    }

    /** Helper: builds a LOCAL mapper Condition mock for a given key type. */
    private Condition localMapper(ConditionKeyType keyType) {
      Condition c = mock(Condition.class);
      when(c.getMappingType()).thenReturn(MappingType.LOCAL);
      lenient().when(c.getKeyType()).thenReturn(keyType);
      return c;
    }

    /** currentOutput shaped as List<WorkflowStateEntries.Input> JSON. */
    private String buildOutput(ConditionKeyType keyType, String... values) {
      WorkflowStateEntries.Input input =
          new WorkflowStateEntries.Input(keyType.name(), new HashSet<>(Arrays.asList(values)));
      return gson.toJson(List.of(input));
    }

    @Test
    @DisplayName("should return false and not save when there are no mappers")
    void shouldReturnFalse_andNotSave_whenNoMappers() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of());
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template, workflow, "{}", null, List.of());

      // Assert
      assertFalse(result);
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return false when mapper is not LOCAL (GLOBAL type)")
    void shouldReturnFalse_whenMapperIsGlobal() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);

      Condition globalMapper = mock(Condition.class);
      when(globalMapper.getMappingType()).thenReturn(MappingType.GLOBAL);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of());
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "1.1.1.1"),
              null,
              List.of(globalMapper));

      // Assert
      assertFalse(result);
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return false when currentOutput is blank")
    void shouldReturnFalse_whenCurrentOutputIsBlank() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition mapper = mock(Condition.class);
      when(mapper.getMappingType()).thenReturn(MappingType.LOCAL);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template, workflow, "   ", null, List.of(mapper));

      // Assert
      assertFalse(result);
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return false when value does not pass filter condition")
    void shouldReturnFalse_whenValueRejectedByFilter() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition mapper = localMapper(ConditionKeyType.IPv4);
      Condition filter = mock(Condition.class);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);
      when(conditionService.isFilterConditionValid(any(), eq(filter))).thenReturn(false);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "192.168.1.1"),
              filter,
              List.of(mapper));

      // Assert
      assertFalse(result);
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should return true and save when a new valid value is added")
    void shouldReturnTrue_andSave_whenNewValueAdded() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition mapper = localMapper(ConditionKeyType.IPv4);
      Condition filter = mock(Condition.class);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);
      when(conditionService.isFilterConditionValid(any(), eq(filter))).thenReturn(true);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "10.10.0.1"),
              filter,
              List.of(mapper));

      // Assert
      assertTrue(result);
      verify(workflowStateRepository).save(localState);
      assertTrue(localState.getEntries().contains("10.10.0.1"));
    }

    @Test
    @DisplayName("should return false when value is already present in entries")
    void shouldReturnFalse_whenValueAlreadyPresent() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition mapper = localMapper(ConditionKeyType.IPv4);
      Condition filter = mock(Condition.class);

      // Pre-populate the entries with the value that will come in
      WorkflowStateEntries.Input existingInput =
          new WorkflowStateEntries.Input("IPv4", new HashSet<>(Set.of("10.10.0.1")));
      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(List.of(existingInput)),
              new ArrayList<>(),
              new HashSet<>(),
              Set.of("IPv4"));
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);
      when(conditionService.isFilterConditionValid(any(), eq(filter))).thenReturn(true);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "10.10.0.1"),
              filter,
              List.of(mapper));

      // Assert
      assertFalse(result);
      verify(workflowStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should initialise local state when it does not exist yet")
    void shouldInitialiseLocalState_whenNotFound() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition mapper = localMapper(ConditionKeyType.IPv4);
      Condition filter = mock(Condition.class);

      // Local state does not exist yet
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(null);
      when(conditionService.isFilterConditionValid(any(), eq(filter))).thenReturn(true);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "172.16.0.5"),
              filter,
              List.of(mapper));

      // Assert
      // A new value was added → changed = true, save is called
      assertTrue(result);
      verify(workflowStateRepository).save(any(WorkflowState.class));
    }

    @Test
    @DisplayName("should skip GLOBAL mapper and process only LOCAL mapper")
    void shouldSkipGlobalMapper_andProcessLocalMapper() {
      // Prepare
      String stepId = UUID.randomUUID().toString();
      String wfId = UUID.randomUUID().toString();

      Step template = buildStepWithId(stepId);
      Workflow workflow = buildWorkflowWithId(wfId);
      Condition filter = mock(Condition.class);

      Condition globalMapper = mock(Condition.class);
      when(globalMapper.getMappingType()).thenReturn(MappingType.GLOBAL);

      Condition localMapperCond = localMapper(ConditionKeyType.IPv4);

      WorkflowStateEntries entries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), Set.of("IPv4"));
      WorkflowState localState = WorkflowState.builder().entries(gson.toJson(entries)).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(stepId, wfId))
          .thenReturn(localState);
      when(conditionService.isFilterConditionValid(any(), eq(filter))).thenReturn(true);

      // Act
      boolean result =
          workflowStateService.syncMappersToLocalPartition(
              template,
              workflow,
              buildOutput(ConditionKeyType.IPv4, "10.0.0.99"),
              filter,
              List.of(globalMapper, localMapperCond));

      // Assert
      // GLOBAL was skipped; LOCAL was processed and added the value
      assertTrue(result);
      verify(workflowStateRepository).save(localState);
    }
  }
}
