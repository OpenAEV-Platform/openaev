package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.utils.ConditionUtils;
import io.openaev.validator.IpAddressUtils;
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

  @Mock private WorkflowStateStore workflowStateStore;
  @Mock private ConditionRepository conditionRepository;
  @Mock private ConditionUtils conditionUtils;
  @Mock private PrimitiveValidationContextBuilder primitiveValidationContextBuilder;

  @InjectMocks private WorkflowStateService workflowStateService;

  /**
   * Stubs the store so that every delta appended to the global state of {@code workflowRun} is
   * accumulated into the returned view, which then reflects what the sync persisted.
   */
  private WorkflowStateEntries captureGlobalAppends(Workflow workflowRun) {
    String stateId = "global-" + workflowRun.getId();
    lenient().when(workflowStateStore.getOrCreateGlobalStateId(workflowRun)).thenReturn(stateId);
    return captureAppends(stateId);
  }

  /** Same as {@link #captureGlobalAppends} for the local state of a step template. */
  private WorkflowStateEntries captureLocalAppends(Step stepTemplate, Workflow workflowRun) {
    String stateId = "local-" + stepTemplate.getId();
    lenient()
        .when(workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun))
        .thenReturn(stateId);
    return captureAppends(stateId);
  }

  private WorkflowStateEntries captureAppends(String stateId) {
    WorkflowStateEntries persisted = WorkflowStateEntries.empty();
    lenient()
        .doAnswer(
            inv -> {
              WorkflowStateEntries delta = inv.getArgument(1);
              delta
                  .getInputs()
                  .forEach(
                      input ->
                          persisted
                              .getInputByKey(input.getKey())
                              .getValues()
                              .addAll(input.getValues()));
              persisted.getCorrelated().addAll(delta.getCorrelated());
              return null;
            })
        .when(workflowStateStore)
        .append(eq(stateId), any(WorkflowStateEntries.class));
    return persisted;
  }

  private static PrimitiveValidationContext emptyValidationContext() {
    return new PrimitiveValidationContext(
        Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
        Set.of());
  }

  // ========================================================================
  // Read side and execution hashes
  // ========================================================================

  @Nested
  @DisplayName("read side and execution hashes")
  class ReadSideAndHashesTests {

    private final Step stepTemplate = Step.builder().id("step-template").build();
    private final Workflow workflowRun = Workflow.builder().id("workflow-run").build();

    @Test
    @DisplayName("loadGlobalEntries returns an empty view when the run has no global state")
    void given_noGlobalState_should_returnAnEmptyGlobalView() {
      // Arrange
      when(workflowStateStore.findGlobalStateId("workflow-run")).thenReturn(Optional.empty());

      // Act
      WorkflowStateEntries view =
          workflowStateService.loadGlobalEntries(workflowRun, Set.of("IPv4"), true);

      // Assert
      assertTrue(view.getInputs().isEmpty());
      assertTrue(view.getCorrelated().isEmpty());
      verify(workflowStateStore, never()).load(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("loadGlobalEntries loads the requested keys and parts, never the hashes")
    void given_globalState_should_loadTheRequestedKeysWithoutHashes() {
      // Arrange
      WorkflowStateEntries expected = WorkflowStateEntries.empty();
      when(workflowStateStore.findGlobalStateId("workflow-run")).thenReturn(Optional.of("g"));
      when(workflowStateStore.load("g", Set.of("IPv4"), true, false)).thenReturn(expected);

      // Act
      WorkflowStateEntries view =
          workflowStateService.loadGlobalEntries(workflowRun, Set.of("IPv4"), true);

      // Assert
      assertSame(expected, view);
    }

    @Test
    @DisplayName("loadLocalEntries forwards the keys and the correlated / hash flags")
    void given_localState_should_forwardKeysAndFlags() {
      // Arrange
      WorkflowStateEntries expected = WorkflowStateEntries.empty();
      when(workflowStateStore.findLocalStateId("step-template", "workflow-run"))
          .thenReturn(Optional.of("l"));
      when(workflowStateStore.load("l", Set.of("Port"), false, true)).thenReturn(expected);

      // Act
      WorkflowStateEntries view =
          workflowStateService.loadLocalEntries(
              stepTemplate, workflowRun, Set.of("Port"), false, true);

      // Assert
      assertSame(expected, view);
    }

    @Test
    @DisplayName("getCommittedHashes returns an empty set when the step has no local state")
    void given_noLocalState_should_returnNoCommittedHash() {
      // Arrange
      when(workflowStateStore.findLocalStateId("step-template", "workflow-run"))
          .thenReturn(Optional.empty());

      // Act
      Set<String> hashes = workflowStateService.getCommittedHashes(stepTemplate, workflowRun);

      // Assert
      assertTrue(hashes.isEmpty());
    }

    @Test
    @DisplayName("commitHashes with no hash neither creates a state nor writes")
    void given_noHash_should_neitherCreateAStateNorWrite() {
      // Act
      Set<String> committed =
          workflowStateService.commitHashes(stepTemplate, workflowRun, Set.of());

      // Assert
      assertTrue(committed.isEmpty());
      verifyNoInteractions(workflowStateStore);
    }

    @Test
    @DisplayName("commitHashes returns only the hashes the store actually committed")
    void given_hashes_should_returnOnlyTheCommittedSubset() {
      // Arrange
      when(workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun)).thenReturn("l");
      when(workflowStateStore.commitExecutionHashes("l", Set.of("h1", "h2")))
          .thenReturn(Set.of("h2"));

      // Act
      Set<String> committed =
          workflowStateService.commitHashes(stepTemplate, workflowRun, Set.of("h1", "h2"));

      // Assert
      assertEquals(Set.of("h2"), committed);
    }

    @Test
    @DisplayName("clearExecutionHashes is a no-op when the step has no local state")
    void given_noLocalState_should_notClearAnything() {
      // Arrange
      when(workflowStateStore.findLocalStateId("step-template", "workflow-run"))
          .thenReturn(Optional.empty());

      // Act
      workflowStateService.clearExecutionHashes(stepTemplate, workflowRun);

      // Assert
      verify(workflowStateStore, never()).clearExecutionHashes(any());
    }

    @Test
    @DisplayName("clearExecutionHashes clears the hashes of the step's local state")
    void given_localState_should_clearItsHashes() {
      // Arrange
      when(workflowStateStore.findLocalStateId("step-template", "workflow-run"))
          .thenReturn(Optional.of("l"));

      // Act
      workflowStateService.clearExecutionHashes(stepTemplate, workflowRun);

      // Assert
      verify(workflowStateStore).clearExecutionHashes("l");
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

      WorkflowStateEntries globalState = captureGlobalAppends(workflow);

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

      WorkflowStateEntries persistedEntries = globalState;

      Set<String> ipv4Values = persistedEntries.getInputByKey("IPv4").getValues();
      assertEquals(253, ipv4Values.size());
      assertTrue(ipv4Values.contains("10.0.0.1"));
      assertTrue(ipv4Values.contains("10.0.0.3"));
      assertFalse(ipv4Values.contains("10.0.0.2"));
      assertFalse(ipv4Values.contains("10.0.0.0"));
      assertFalse(ipv4Values.contains("10.0.0.255"));
      assertEquals(Set.of("example.org"), persistedEntries.getInputByKey("Domain").getValues());
      assertEquals(Set.of("10.0.0.0/24"), persistedEntries.getInputByKey("IpSubnet").getValues());
      assertEquals(Set.of(validAssetId), persistedEntries.getInputByKey("AssetId").getValues());
      assertEquals(
          Set.of(validAssetGroupId), persistedEntries.getInputByKey("AssetGroupId").getValues());
    }

    @Test
    @DisplayName("should expand accepted IPv6 subnet outputs into IPv6 workflow state values")
    void givenIpv6SubnetOutput_shouldStoreExpandedIpv6Values() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();

      WorkflowStateEntries globalState = captureGlobalAppends(workflow);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflow)))
          .thenReturn(
              new PrimitiveValidationContext(
                  Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                  Set.of(), Set.of()));

      String subnet = "2001:db8::/126";
      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "subnet_values": ["%s"]
                  }
                  """
                      .formatted(subnet))
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put("subnet_values", ChainingMappedType.primitive(PrimitiveType.IpSubnet));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persistedEntries = globalState;
      Set<String> expectedExpanded = new HashSet<>(IpAddressUtils.expandSubnetToHostIps(subnet));

      assertEquals(Set.of(subnet), persistedEntries.getInputByKey("IpSubnet").getValues());
      assertEquals(expectedExpanded, persistedEntries.getInputByKey("IPv6").getValues());
    }

    @Test
    @DisplayName("should expand subnet fields when subnet is part of a complex output")
    void givenComplexOutputContainingSubnet_shouldStoreSubnetAndExpandedIps() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();

      WorkflowStateEntries globalState = captureGlobalAppends(workflow);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflow)))
          .thenReturn(
              new PrimitiveValidationContext(
                  Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                  Set.of(), Set.of()));

      String subnet = "10.10.10.0/30";
      JsonObject dataToSync =
          JsonParser.parseString(
                  """
                  {
                    "portscan": [
                      {
                        "host": "example.local",
                        "ip_subnet": "%s"
                      }
                    ]
                  }
                  """
                      .formatted(subnet))
              .getAsJsonObject();

      Map<String, ChainingMappedType> typeMappings = new HashMap<>();
      typeMappings.put(
          "portscan",
          ChainingMappedType.complex(
              List.of(PrimitiveType.Host, PrimitiveType.IpSubnet), ContractOutputType.PortsScan));

      workflowStateService.syncState(dataToSync, typeMappings, workflow);

      WorkflowStateEntries persistedEntries = globalState;

      assertEquals(Set.of(subnet), persistedEntries.getInputByKey("IpSubnet").getValues());
      assertEquals(
          Set.of("10.10.10.1", "10.10.10.2"), persistedEntries.getInputByKey("IPv4").getValues());
    }

    @Test
    @DisplayName("should map complex subfields to contextual primitive keys")
    void givenComplexTypeSubfields_shouldStoreUnderContextualPrimitiveTypes() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = Workflow.builder().id(workflowId).build();

      WorkflowStateEntries globalState = captureGlobalAppends(workflow);
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

      WorkflowStateEntries persistedEntries = globalState;

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

    private final Step stepTemplate = Step.builder().id("step-template").build();
    private final Workflow workflowTemplate = Workflow.builder().id("wf-template").build();
    private final Workflow workflowRun =
        Workflow.builder().id("wf-run").workflowTemplate(workflowTemplate).build();

    private final Map<String, ChainingMappedType> portScanMappings =
        Map.of(
            "portscan",
            ChainingMappedType.complex(
                List.of(PrimitiveType.Host, PrimitiveType.Port), ContractOutputType.PortsScan));

    private JsonObject portScanOutput() {
      return JsonParser.parseString(
              """
              {
                "portscan": [
                  {"host": "10.0.0.1", "port": "22"}
                ]
              }
              """)
          .getAsJsonObject();
    }

    /** Links an event on the Host key type to {@link #stepTemplate}. */
    private void givenStepEventOnHost() {
      Condition leafCondition =
          Condition.builder()
              .keyTypes(List.of(PrimitiveType.Host))
              .value("10.0.0.1")
              .type(ConditionType.EQ)
              .build();
      ConditionStep conditionStep = new ConditionStep();
      conditionStep.setStep(stepTemplate);
      Condition rootCondition =
          Condition.builder()
              .conditionChildren(List.of(leafCondition))
              .conditionSteps(List.of(conditionStep))
              .build();
      when(conditionRepository.findFilterConditionsByWorkflowId(eq("wf-template"), anySet()))
          .thenReturn(List.of(rootCondition));
    }

    @Test
    @DisplayName("global state receives the tuple and its fields decomposed as inputs")
    void given_complexOutput_should_appendTupleAndInputsToGlobal() {
      WorkflowStateEntries global = captureGlobalAppends(workflowRun);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());
      when(conditionRepository.findFilterConditionsByWorkflowId(eq("wf-template"), anySet()))
          .thenReturn(List.of());

      workflowStateService.syncState(portScanOutput(), portScanMappings, workflowRun);

      assertEquals(1, global.getCorrelated().size());
      assertEquals("PortsScan", global.getCorrelated().getFirst().getType());
      assertEquals(Set.of("10.0.0.1"), global.getInputByKey("Host").getValues());
      assertEquals(Set.of("22"), global.getInputByKey("Port").getValues());
    }

    @Test
    @DisplayName(
        "when a correlated tuple field matches step event, full tuple should be in local correlated")
    void givenComplexOutput_whenFieldMatchesStepEvent_shouldPropagateFullTupleToLocal() {
      captureGlobalAppends(workflowRun);
      WorkflowStateEntries local = captureLocalAppends(stepTemplate, workflowRun);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());
      givenStepEventOnHost();
      when(conditionUtils.matchesAnyLeafCondition(eq("10.0.0.1"), any(), any())).thenReturn(true);
      when(conditionUtils.matchesAnyLeafCondition(eq("22"), any(), any())).thenReturn(false);

      workflowStateService.syncState(portScanOutput(), portScanMappings, workflowRun);

      assertEquals(1, local.getCorrelated().size(), "full tuple should be propagated");
      Set<WorkflowStateEntries.Pair> pairs = local.getCorrelated().getFirst().getValues();
      assertTrue(pairs.contains(new WorkflowStateEntries.Pair("Host", "10.0.0.1")));
      assertTrue(pairs.contains(new WorkflowStateEntries.Pair("Port", "22")));
      assertEquals(Set.of("10.0.0.1"), local.getInputByKey("Host").getValues());
    }

    @Test
    @DisplayName(
        "when no correlated tuple field matches step event, no tuple should be in local correlated")
    void givenComplexOutput_whenNoFieldMatchesStepEvent_shouldNotPropagateToLocal() {
      captureGlobalAppends(workflowRun);
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());
      givenStepEventOnHost();
      when(conditionUtils.matchesAnyLeafCondition(anyString(), any(), any())).thenReturn(false);

      workflowStateService.syncState(portScanOutput(), portScanMappings, workflowRun);

      verify(workflowStateStore, never()).getOrCreateLocalStateId(any(), any());
    }

    @Test
    @DisplayName("when no value is accepted, no state is created nor written")
    void given_noAcceptedValue_should_notTouchTheStore() {
      when(primitiveValidationContextBuilder.build(anyMap(), eq(workflowRun)))
          .thenReturn(emptyValidationContext());

      workflowStateService.syncState(
          JsonParser.parseString("{\"unmapped\": [\"x\"]}").getAsJsonObject(),
          portScanMappings,
          workflowRun);

      verifyNoInteractions(workflowStateStore);
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
