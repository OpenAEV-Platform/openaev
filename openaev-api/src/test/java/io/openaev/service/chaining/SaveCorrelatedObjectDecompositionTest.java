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
@DisplayName("saveCorrelatedObject — primitive decomposition into inputs")
class SaveCorrelatedObjectDecompositionTest {

  @Mock private WorkflowStateRepository workflowStateRepository;
  @Mock private ConditionRepository conditionRepository;
  @Mock private ConditionUtils conditionUtils;
  @Mock private PrimitiveValidationContextBuilder primitiveValidationContextBuilder;

  @InjectMocks private WorkflowStateService workflowStateService;

  private final Gson gson = new Gson();

  private WorkflowState setupGlobalState(String workflowId, Workflow workflow) {
    WorkflowStateEntries initialEntries =
        new WorkflowStateEntries(
            new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
    WorkflowState globalState =
        WorkflowState.builder().entries(gson.toJson(initialEntries)).build();

    when(workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId))
        .thenReturn(globalState);
    when(workflowStateRepository.save(any(WorkflowState.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    PrimitiveValidationContext validationContext =
        new PrimitiveValidationContext(
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of());
    when(primitiveValidationContextBuilder.build(anyMap(), eq(workflow)))
        .thenReturn(validationContext);

    return globalState;
  }

  @Nested
  @DisplayName("Multi-field complex object (PortsScan)")
  class MultiFieldComplexObject {

    @Test
    @DisplayName("PortsScan {host, port, service} → 1 Correlated + 3 inputs (Host, Port, Service)")
    void givenPortsScanObject_shouldCreateCorrelatedAndDecomposeIntoInputs() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();
      WorkflowState globalState = setupGlobalState(workflowId, workflow);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "portscan": [
                      {"host": "10.0.0.1", "port": "443", "service": "https"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "portscan",
          ChainingMappedType.complex(
              List.of(PrimitiveType.Host, PrimitiveType.Port, PrimitiveType.Service),
              ContractOutputType.PortsScan));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persisted =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      // Exactly 1 Correlated with the business type and expected pair set
      assertEquals(1, persisted.getCorrelated().size());
      WorkflowStateEntries.Correlated correlated = persisted.getCorrelated().getFirst();
      assertEquals("PortsScan", correlated.getType());
      assertEquals(
          Set.of(
              new WorkflowStateEntries.Pair("Host", "10.0.0.1"),
              new WorkflowStateEntries.Pair("Port", "443"),
              new WorkflowStateEntries.Pair("Service", "https")),
          correlated.getValues());

      // 3 inputs decomposed from the same object
      assertEquals(Set.of("10.0.0.1"), persisted.getInputByKey("Host").getValues());
      assertEquals(Set.of("443"), persisted.getInputByKey("Port").getValues());
      assertEquals(Set.of("https"), persisted.getInputByKey("Service").getValues());
    }

    @Test
    @DisplayName("host appears BOTH in correlated pairSet AND in inputs; asset_id in NEITHER")
    void givenObjectWithHostAndAssetId_hostInBothAssetIdInNeither() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();
      WorkflowState globalState = setupGlobalState(workflowId, workflow);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "credentials": [
                      {"username": "user1", "password": "pass1", "host": "srv1", "asset_id": "aid-1"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "credentials",
          ChainingMappedType.complex(
              List.of(PrimitiveType.Username, PrimitiveType.Password),
              ContractOutputType.Credentials));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persisted =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      // host in correlated pairSet
      Set<String> correlatedKeys =
          persisted.getCorrelated().getFirst().getValues().stream()
              .map(WorkflowStateEntries.Pair::key)
              .collect(java.util.stream.Collectors.toSet());
      assertTrue(correlatedKeys.contains("Host"), "host must be in correlated pairSet");
      assertFalse(correlatedKeys.contains("AssetId"), "asset_id must NOT be in correlated pairSet");

      // host in inputs
      assertEquals(Set.of("srv1"), persisted.getInputByKey("Host").getValues());

      // asset_id in neither inputs nor correlated
      assertTrue(
          persisted.getInputs().stream().noneMatch(i -> i.getKey().equals("AssetId")),
          "asset_id must NOT land in inputs");
    }
  }

  @Nested
  @DisplayName("Mono-field complex object")
  class MonoFieldComplexObject {

    @Test
    @DisplayName("single-field complex → NO Correlated, but input IS populated")
    void givenMonoFieldObject_shouldNotCreateCorrelatedButShouldPopulateInput() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();
      WorkflowState globalState = setupGlobalState(workflowId, workflow);

      // A complex type that only has one content field after ATTACHMENT_KEYS exclusion
      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "username_output": [
                      {"username": "lonely-user", "asset_id": "aid-x"}
                    ]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "username_output",
          ChainingMappedType.complex(List.of(PrimitiveType.Username), ContractOutputType.Username));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persisted =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      // No Correlated (pairSet size == 1, under the guard)
      assertEquals(0, persisted.getCorrelated().size());

      // But the primitive STILL lands in inputs
      assertEquals(Set.of("lonely-user"), persisted.getInputByKey("Username").getValues());
    }
  }

  @Nested
  @DisplayName("Scalar output regression guard")
  class ScalarOutputRegressionGuard {

    @Test
    @DisplayName("pure scalar output lands in inputs and creates NO Correlated")
    void givenScalarOutput_shouldOnlyPopulateInputsNoCorrelated() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();
      WorkflowState globalState = setupGlobalState(workflowId, workflow);

      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "ipv4_values": ["192.168.1.1", "10.0.0.2"]
                  }
                  """)
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put("ipv4_values", ChainingMappedType.primitive(PrimitiveType.IPv4));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persisted =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      // Scalars go to inputs only
      assertEquals(Set.of("192.168.1.1", "10.0.0.2"), persisted.getInputByKey("IPv4").getValues());

      // No Correlated created
      assertEquals(0, persisted.getCorrelated().size());
    }
  }
}
