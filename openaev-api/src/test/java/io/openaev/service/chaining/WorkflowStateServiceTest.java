package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.ConditionUtils;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowStateService Tests")
class WorkflowStateServiceTest {

  @Mock private WorkflowStateRepository workflowStateRepository;
  @Mock private ConditionRepository conditionRepository;
  @Mock private ConditionUtils conditionUtils;
  @Mock private PrimitiveValidationContextBuilder primitiveValidationContextBuilder;

  @InjectMocks private WorkflowStateService workflowStateService;

  private final Gson gson = new Gson();

  // ========================================================================
  // getLocalStateByWorkflowAndStep Tests
  // ========================================================================
  @Nested
  @DisplayName("getLocalStateByWorkflowAndStep")
  class GetLocalStateByWorkflowAndStepTests {

    @Test
    @DisplayName("should return local state when found")
    void given_existingState_should_returnLocalState() {
      // Arrange
      String stepTemplateId = UUID.randomUUID().toString();
      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      String workflowExecutionId = UUID.randomUUID().toString();
      Workflow workflowExecution = Workflow.builder().id(workflowExecutionId).build();

      WorkflowState expected = mock(WorkflowState.class);
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(expected);

      // Act
      WorkflowState result =
          workflowStateService.loadOrBuildLocalState(stepTemplate, workflowExecution);

      // Assert
      assertSame(expected, result);
      verify(workflowStateRepository)
          .findByStepTemplate_IdAndWorkflowExecution_Id(stepTemplateId, workflowExecutionId);
    }

    @Test
    @DisplayName("should initialize local state when not found")
    void given_noExistingState_should_initializeLocalState() {
      // Arrange
      String stepTemplateId = UUID.randomUUID().toString();
      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      String workflowExecutionId = UUID.randomUUID().toString();
      Workflow workflowExecution = Workflow.builder().id(workflowExecutionId).build();

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowExecutionId))
          .thenReturn(null);

      // Act
      WorkflowState result =
          workflowStateService.loadOrBuildLocalState(stepTemplate, workflowExecution);

      // Assert
      assertNotNull(result);
      assertEquals(workflowExecution, result.getWorkflowExecution());
      assertEquals(stepTemplate, result.getStepTemplate());
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
    void given_existingGlobalState_should_returnIt() {
      // Arrange
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
    void given_noGlobalState_should_returnNull() {
      // Arrange
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
  // save Tests
  // ========================================================================
  @Nested
  @DisplayName("save")
  class SaveTests {

    @Test
    @DisplayName("should delegate directly to the repository")
    void given_state_should_delegateToRepository() {
      // Arrange
      WorkflowState state = mock(WorkflowState.class);

      // Act
      workflowStateService.save(state);

      // Assert
      verify(workflowStateRepository).save(state);
      verifyNoMoreInteractions(workflowStateRepository);
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
    void given_nonCorrelatedPath_should_addNewValue() {
      // Arrange
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
    void given_existingValue_should_notAddDuplicate() {
      // Arrange
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
    void given_correlatedPath_should_handleIt() {
      // Arrange
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
    void given_existingCorrelated_should_notAddDuplicate() {
      // Arrange
      String path = "outputs.message.ip+outputs.message.port";
      String output = "{\"outputs\":{\"message\":{\"ip\":\"192.168.1.1\",\"port\":\"8080\"}}}";
      String key = "ip+port";

      List<String> correlatedPaths = List.of("outputs.message.ip", "outputs.message.port");

      Set<WorkflowStateEntries.Pair> existingPairs = new HashSet<>();
      existingPairs.add(new WorkflowStateEntries.Pair("ip", "192.168.1.1"));
      existingPairs.add(new WorkflowStateEntries.Pair("port", "8080"));

      Map<Set<WorkflowStateEntries.Pair>, WorkflowStateEntries.Correlated> existingIndex =
          new HashMap<>();
      existingIndex.put(existingPairs, new WorkflowStateEntries.Correlated(existingPairs, null));

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
    void given_stringOutput_should_extractValue() {
      // Arrange
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
    void given_nullOutput_should_handleGracefully() {
      // Arrange
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
    void given_numericOutput_should_extractAsString() {
      // Arrange
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
    void given_booleanOutput_should_extractAsString() {
      // Arrange
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

  @Nested
  @DisplayName("syncState - primitive validation on storage")
  class SyncStateValidationTests {

    @Test
    @DisplayName("should store only valid primitive values and scoped asset IDs")
    void givenMixedValues_shouldPersistOnlyValidOnes() {
      String workflowId = UUID.randomUUID().toString();
      String validAssetId = UUID.randomUUID().toString();
      String validAssetGroupId = UUID.randomUUID().toString();
      String deniedAssetGroupId = UUID.randomUUID().toString();
      String deniedIp = "10.0.0.2";
      String deniedDomain = "blocked.org";

      Workflow workflow = Workflow.builder().id(workflowId).build();

      WorkflowStateEntries initialEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState globalState =
          WorkflowState.builder().entries(gson.toJson(initialEntries)).build();

      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);

      PrimitiveValidationContext validationContext =
          new PrimitiveValidationContext(
              Set.of(validAssetGroupId),
              Set.of(validAssetId),
              Set.of("example.org"),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(),
              Set.of(deniedDomain),
              Set.of(deniedIp),
              Set.of());
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflow)))
          .thenReturn(validationContext);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "ipv4_values": ["10.0.0.1", "%s", "bad-ip"],
                    "domain_values": ["example.org", "%s", "bad domain"],
                    "subnet_values": ["10.0.0.0/24", "bad-subnet"],
                    "asset_values": ["%s", "not-scoped-asset"],
                    "asset_group_values": ["%s", "%s", "bad-group-id"]
                  }
                  """
                      .formatted(
                          deniedIp,
                          deniedDomain,
                          validAssetId,
                          validAssetGroupId,
                          deniedAssetGroupId))
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put("ipv4_values", ChainingMappedType.primitive(PrimitiveType.IPv4));
      typeMappings.put("domain_values", ChainingMappedType.primitive(PrimitiveType.Domain));
      typeMappings.put("subnet_values", ChainingMappedType.primitive(PrimitiveType.IpSubnet));
      typeMappings.put("asset_values", ChainingMappedType.primitive(PrimitiveType.AssetId));
      typeMappings.put(
          "asset_group_values", ChainingMappedType.primitive(PrimitiveType.AssetGroupId));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persistedEntries =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      assertEquals(Set.of("10.0.0.1"), persistedEntries.getInputByKey("IPv4").getValues());
      assertEquals(Set.of("example.org"), persistedEntries.getInputByKey("Domain").getValues());
      assertEquals(Set.of("10.0.0.0/24"), persistedEntries.getInputByKey("IpSubnet").getValues());
      assertEquals(Set.of(validAssetId), persistedEntries.getInputByKey("AssetId").getValues());
      assertEquals(
          Set.of(validAssetGroupId), persistedEntries.getInputByKey("AssetGroupId").getValues());
    }

    @Test
    @DisplayName("should map complex subfields to contextual primitive keys")
    void givenComplexTypeSubfields_shouldStoreUnderContextualPrimitiveTypes() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();

      WorkflowStateEntries initialEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState globalState =
          WorkflowState.builder().entries(gson.toJson(initialEntries)).build();

      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflow)))
          .thenReturn(
              new PrimitiveValidationContext(
                  Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                  Set.of(), Set.of()));

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "vulnerabilities": [
                      {
                        "name": "vuln-name",
                        "status": "open",
                        "host": "dc1.local",
                        "asset_id": "asset-1"
                      }
                    ],
                    "delegations": [
                      {
                        "account": "svc-app",
                        "host": "dc2.local",
                        "asset_id": "asset-2"
                      }
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "vulnerabilities",
          ChainingMappedType.complex(List.of(), ContractOutputType.Vulnerability));
      typeMappings.put(
          "delegations", ChainingMappedType.complex(List.of(), ContractOutputType.Delegation));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persistedEntries =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      assertTrue(inputValuesByKey(persistedEntries, "VulnerabilityName").contains("vuln-name"));
      assertTrue(inputValuesByKey(persistedEntries, "VulnerabilityStatus").contains("open"));
      assertTrue(inputValuesByKey(persistedEntries, "DelegationAccount").contains("svc-app"));
      assertFalse(inputValuesByKey(persistedEntries, "Account").contains("svc-app"));
    }
  }

  // ========================================================================
  // syncState — correlated tuple propagation to local step states
  // ========================================================================
  @Nested
  @DisplayName("syncState - correlated tuple propagation")
  class CorrelatedTuplePropagationTests {

    @Test
    @DisplayName(
        "when a correlated tuple field matches step event, full tuple should be in local correlated")
    void givenComplexOutput_whenFieldMatchesStepEvent_shouldPropagateFullTupleToLocal() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      String stepTemplateId = "step-template-1";
      String workflowTemplateId = "wf-template-1";

      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder().id(workflowId).workflowTemplate(workflowTemplate).build();

      // Global state — empty
      WorkflowStateEntries globalEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState globalState =
          WorkflowState.builder().entries(gson.toJson(globalEntries)).build();

      // Local state for step — empty
      WorkflowStateEntries localEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState localState =
          WorkflowState.builder()
              .stepTemplate(stepTemplate)
              .workflowExecution(workflowRun)
              .entries(gson.toJson(localEntries))
              .build();

      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);
      when(workflowStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());

      // Event condition on IPv4 key type: matches "10.0.0.1"
      Condition leafCondition =
          Condition.builder()
              .keyType(PrimitiveType.IPv4)
              .value("10.0.0.1")
              .operator(ConditionKeyType.eq)
              .build();
      ConditionStep cs1 = new ConditionStep();
      cs1.setStep(stepTemplate);
      Condition rootCondition =
          Condition.builder()
              .conditionChildren(List.of(leafCondition))
              .conditionSteps(List.of(cs1))
              .build();

      when(conditionRepository.findFilterConditionsByWorkflowIdAndKeyTypes(
              eq(workflowTemplateId), anySet(), anySet()))
          .thenReturn(List.of(rootCondition));

      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowId))
          .thenReturn(localState);

      // conditionUtils.matchesAnyLeafCondition: return true when val == "10.0.0.1"
      when(conditionUtils.matchesAnyLeafCondition(eq("10.0.0.1"), any())).thenReturn(true);
      when(conditionUtils.matchesAnyLeafCondition(eq("22"), any())).thenReturn(false);

      // Complex output: PortScan {ip, port}
      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "portscan": [
                      {"ip": "10.0.0.1", "port": "22"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "portscan",
          ChainingMappedType.complex(
              List.of(PrimitiveType.IPv4, PrimitiveType.Port), ContractOutputType.PortsScan));

      // Act
      workflowStateService.syncState(dataToSync, typeMappings, workflowRun);

      // Assert — local state must contain the full tuple {Ip, Port}
      WorkflowStateEntries persisted =
          gson.fromJson(localState.getEntries(), WorkflowStateEntries.class);
      assertEquals(1, persisted.getCorrelated().size(), "full tuple should be propagated");
      Set<WorkflowStateEntries.Pair> pairs = persisted.getCorrelated().getFirst().getValues();
      assertTrue(
          pairs.stream().anyMatch(p -> p.key().equals("IPv4") && p.value().equals("10.0.0.1")));
      assertTrue(pairs.stream().anyMatch(p -> p.key().equals("Port") && p.value().equals("22")));
    }

    @Test
    @DisplayName(
        "when no correlated tuple field matches step event, no tuple should be in local correlated")
    void givenComplexOutput_whenNoFieldMatchesStepEvent_shouldNotPropagateToLocal() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      String stepTemplateId = "step-template-2";
      String workflowTemplateId = "wf-template-2";

      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder().id(workflowId).workflowTemplate(workflowTemplate).build();

      WorkflowStateEntries globalEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState globalState =
          WorkflowState.builder().entries(gson.toJson(globalEntries)).build();

      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);
      when(workflowStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());

      // Event condition on IPv4: expects "192.168.1.1" — no field in the tuple matches
      Condition leafCondition =
          Condition.builder()
              .keyType(PrimitiveType.IPv4)
              .value("192.168.1.1")
              .operator(ConditionKeyType.eq)
              .build();
      ConditionStep cs2 = new ConditionStep();
      cs2.setStep(stepTemplate);
      Condition rootCondition =
          Condition.builder()
              .conditionChildren(List.of(leafCondition))
              .conditionSteps(List.of(cs2))
              .build();

      when(conditionRepository.findFilterConditionsByWorkflowIdAndKeyTypes(
              eq(workflowTemplateId), anySet(), anySet()))
          .thenReturn(List.of(rootCondition));

      // no matchesAnyLeafCondition returns true
      when(conditionUtils.matchesAnyLeafCondition(anyString(), any())).thenReturn(false);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "portscan": [
                      {"ip": "10.0.0.1", "port": "22"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "portscan",
          ChainingMappedType.complex(
              List.of(PrimitiveType.IPv4, PrimitiveType.Port), ContractOutputType.PortsScan));

      // Act
      workflowStateService.syncState(dataToSync, typeMappings, workflowRun);

      // Assert — local state repository should never be queried
      verify(workflowStateRepository, never())
          .findByStepTemplate_IdAndWorkflowExecution_Id(anyString(), anyString());
    }

    @Test
    @DisplayName("when same tuple already present in local state, should not add duplicate")
    void givenTupleAlreadyInLocalState_shouldNotAddDuplicate() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      String stepTemplateId = "step-template-3";
      String workflowTemplateId = "wf-template-3";

      Step stepTemplate = Step.builder().id(stepTemplateId).build();
      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder().id(workflowId).workflowTemplate(workflowTemplate).build();

      // Pre-existing correlated tuple in local state (same pair-set that will be produced)
      Set<WorkflowStateEntries.Pair> existingPairs = new HashSet<>();
      existingPairs.add(new WorkflowStateEntries.Pair("IPv4", "10.0.0.1"));
      existingPairs.add(new WorkflowStateEntries.Pair("Port", "22"));
      WorkflowStateEntries.Correlated existingTuple =
          new WorkflowStateEntries.Correlated(existingPairs, "PortsScan");

      List<WorkflowStateEntries.Correlated> preExistingCorrelated = new ArrayList<>();
      preExistingCorrelated.add(existingTuple);

      WorkflowStateEntries localEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), preExistingCorrelated, new HashSet<>(), new HashSet<>());
      WorkflowState localState =
          WorkflowState.builder()
              .stepTemplate(stepTemplate)
              .workflowExecution(workflowRun)
              .entries(gson.toJson(localEntries))
              .build();

      WorkflowStateEntries globalEntries =
          new WorkflowStateEntries(
              new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
      WorkflowState globalState =
          WorkflowState.builder().entries(gson.toJson(globalEntries)).build();

      when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
          .thenReturn(globalState);
      when(workflowStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());

      Condition leafCondition =
          Condition.builder()
              .keyType(PrimitiveType.IPv4)
              .value("10.0.0.1")
              .operator(ConditionKeyType.eq)
              .build();
      ConditionStep cs3 = new ConditionStep();
      cs3.setStep(stepTemplate);
      Condition rootCondition =
          Condition.builder()
              .conditionChildren(List.of(leafCondition))
              .conditionSteps(List.of(cs3))
              .build();

      when(conditionRepository.findFilterConditionsByWorkflowIdAndKeyTypes(
              eq(workflowTemplateId), anySet(), anySet()))
          .thenReturn(List.of(rootCondition));
      when(workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplateId, workflowId))
          .thenReturn(localState);
      when(conditionUtils.matchesAnyLeafCondition(eq("10.0.0.1"), any())).thenReturn(true);
      when(conditionUtils.matchesAnyLeafCondition(eq("22"), any())).thenReturn(false);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "portscan": [
                      {"ip": "10.0.0.1", "port": "22"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "portscan",
          ChainingMappedType.complex(
              List.of(PrimitiveType.IPv4, PrimitiveType.Port), ContractOutputType.PortsScan));

      // Act
      workflowStateService.syncState(dataToSync, typeMappings, workflowRun);

      // Assert — still only 1 correlated entry (no duplicate)
      WorkflowStateEntries persisted =
          gson.fromJson(localState.getEntries(), WorkflowStateEntries.class);
      assertEquals(1, persisted.getCorrelated().size(), "should not duplicate existing tuple");
    }

    private PrimitiveValidationContext emptyValidationContext() {
      return new PrimitiveValidationContext(
          Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
          Set.of());
    }
  }

  private static Set<String> inputValuesByKey(WorkflowStateEntries entries, String key) {
    return entries.getInputs().stream()
        .filter(input -> key.equals(input.getKey()))
        .findFirst()
        .map(WorkflowStateEntries.Input::getValues)
        .orElse(Set.of());
  }
}
