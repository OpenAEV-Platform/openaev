package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScopeVariableRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.WorkflowNotEditableException;
import io.openaev.telemetry.metric_collectors.ChainingSafetyPolicyMetricCollector;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.telemetry.metric_collectors.ScopeMetricCollector;
import io.openaev.utils.fixtures.WorkflowFixture;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService Tests")
class WorkflowServiceTest {

  @Mock private WorkflowRepository workflowRepository;
  @Mock private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Mock private ScopeVariableRepository scopeVariableRepository;
  @Mock private io.openaev.database.repository.AssetRepository assetRepository;
  @Mock private io.openaev.database.repository.AssetGroupRepository assetGroupRepository;
  @Mock private io.openaev.database.repository.TeamRepository teamRepository;
  @Mock private io.openaev.database.repository.UserRepository userRepository;
  @Mock private StepService stepService;
  @Mock private ConditionService conditionService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Mock private ScopeSnapshotService scopeSnapshotService;
  @Mock private ScopeService scopeService;
  @Mock private WorkflowStateService workflowStateService;
  @Mock private ScopeMetricCollector scopeMetricCollector;
  @Mock private ChainingSafetyPolicyMetricCollector chainingSafetyPolicyMetricCollector;
  @Mock private ResultsMetricCollector resultsMetricCollector;

  @InjectMocks private WorkflowService workflowService;

  // ========================================================================
  // getWorkflowById Tests
  // ========================================================================
  @Nested
  @DisplayName("getWorkflowByIdAndStatus")
  class GetWorkflowByIdAndStatusTests {

    @Captor private ArgumentCaptor<String> workflowIdCaptor;

    @Test
    @DisplayName("should return workflow when found")
    void shouldReturnWorkflowWhenFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      workflow.setStatus(WorkflowStatus.TEMPLATE);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      // Act
      Workflow result =
          workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);

      // Assert
      verify(workflowRepository)
          .findByIdAndStatus(workflowIdCaptor.capture(), eq(WorkflowStatus.TEMPLATE));
      assertEquals(workflowId, workflowIdCaptor.getValue());
      assertNotNull(result);
      assertEquals(workflow, result);
    }

    @Test
    @DisplayName("should throw ElementNotFoundException when not found")
    void shouldThrowExceptionWhenNotFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE));

      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }
  }

  // ========================================================================
  // creationWorkflow Tests
  // ========================================================================
  @Nested
  @DisplayName("creationWorkflow")
  class CreationWorkflowTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;

    @Test
    @DisplayName("should create simulation workflow template with inline configuration defaults")
    void shouldCreateWorkflowTemplateWithInlineConfigurationDefaults() {
      // Prepare
      Exercise exercise = mock(Exercise.class);

      // Act
      workflowService.creationWorkflow(exercise);

      // Assert
      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow savedWorkflow = workflowCaptor.getValue();
      assertEquals(0, savedWorkflow.getVersion());
      assertEquals(WorkflowStatus.TEMPLATE, savedWorkflow.getStatus());
      assertEquals(exercise, savedWorkflow.getSimulation());
      // Configuration defaults stored inline on the workflow row
      assertFalse(savedWorkflow.isRateLimitEnabled());
      assertTrue(savedWorkflow.isTimeoutEnabled());
      assertEquals(
          WorkflowService.DEFAULT_TIMEOUT_SECONDS,
          savedWorkflow.getTimeoutSeconds(),
          "Timeout seconds must default to DEFAULT_TIMEOUT_SECONDS");
      assertTrue(savedWorkflow.isSafeModeEnabled());
    }

    @Test
    @DisplayName("should create scenario workflow template with default timeout seconds")
    void shouldCreateScenarioWorkflowTemplateWithDefaultTimeoutSeconds() {
      // Prepare
      Scenario scenario = mock(Scenario.class);

      // Act
      workflowService.creationWorkflow(scenario);

      // Assert
      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow savedWorkflow = workflowCaptor.getValue();
      assertEquals(0, savedWorkflow.getVersion());
      assertEquals(WorkflowStatus.TEMPLATE, savedWorkflow.getStatus());
      assertEquals(scenario, savedWorkflow.getScenario());
      assertFalse(savedWorkflow.isRateLimitEnabled());
      assertTrue(savedWorkflow.isTimeoutEnabled());
      assertEquals(
          WorkflowService.DEFAULT_TIMEOUT_SECONDS,
          savedWorkflow.getTimeoutSeconds(),
          "Timeout seconds must default to DEFAULT_TIMEOUT_SECONDS");
      assertTrue(savedWorkflow.isSafeModeEnabled());
    }

    @Test
    @DisplayName("DEFAULT_TIMEOUT_SECONDS constant should be 3600")
    void defaultTimeoutConstantShouldBe3600() {
      assertEquals(3600L, WorkflowService.DEFAULT_TIMEOUT_SECONDS);
    }
  }

  @Nested
  @DisplayName("saveWorkflowRun")
  class SaveWorkflowRunTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;

    @Test
    @DisplayName("should save and return workflow run")
    void shouldSaveAndReturnWorkflowRun() {
      // Prepare
      Workflow workflowRun = mock(Workflow.class);
      Workflow savedWorkflow = mock(Workflow.class);
      when(workflowRepository.save(workflowRun)).thenReturn(savedWorkflow);

      // Act
      Workflow result = workflowService.saveWorkflowRun(workflowRun);

      // Assert
      verify(workflowRepository).save(workflowCaptor.capture());
      assertEquals(workflowRun, workflowCaptor.getValue());
      assertEquals(savedWorkflow, result);
    }
  }

  // ========================================================================
  // launchWorkflow Tests
  // ========================================================================
  @Nested
  @DisplayName("launchWorkflow")
  class LaunchWorkflowTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;

    @Test
    @DisplayName("should increment version when template is edited")
    void shouldIncrementVersionWhenTemplateEdited() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Workflow template = mock(Workflow.class);
      when(template.isEdited()).thenReturn(true);
      when(template.getWorkflowsExecuted()).thenReturn(List.of(new Workflow()));
      when(template.getVersion()).thenReturn(1);
      when(template.getSimulation()).thenReturn(simulation);
      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      workflowService.launchWorkflowSimulation(template);

      // Assert
      verify(template).setEdited(false);
      verify(template).setVersion(2);
      // two saves: one for template version bump, one for the run
      verify(workflowRepository, times(2)).save(any(Workflow.class));
    }

    @Test
    @DisplayName("should not increment version when template is not edited")
    void shouldNotIncrementVersionWhenNotEdited() {
      // Prepare
      Exercise simulation = mock(Exercise.class);

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.isEdited()).thenReturn(false);
      when(workflowTemplate.getVersion()).thenReturn(1);
      when(workflowTemplate.getSimulation()).thenReturn(simulation);

      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      workflowService.launchWorkflowSimulation(workflowTemplate);

      // Assert
      verify(workflowTemplate, never()).setEdited(anyBoolean());
      verify(workflowTemplate, never()).setVersion(anyInt());
      verify(workflowRepository, times(1)).save(any(Workflow.class));
    }

    @Test
    @DisplayName("should create workflow run with correct properties")
    void shouldCreateRunWithCorrectProperties() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      int version = 3;

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.isEdited()).thenReturn(false);
      when(workflowTemplate.getVersion()).thenReturn(version);
      when(workflowTemplate.getSimulation()).thenReturn(simulation);
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflowSimulation(workflowTemplate);

      // Assert
      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow savedRun = workflowCaptor.getValue();

      assertNotNull(result);
      assertEquals(WorkflowStatus.RUN, savedRun.getStatus());
      assertEquals(simulation, savedRun.getSimulation());
      assertEquals(version, savedRun.getVersion());
      assertEquals(workflowTemplate, savedRun.getWorkflowTemplate());
      assertFalse(savedRun.isEdited());
    }

    @Test
    @DisplayName("should copy inline configuration fields from template to run")
    void shouldCopyInlineConfigurationFieldsFromTemplateToRun() {
      // Prepare
      Exercise simulation = mock(Exercise.class);

      Workflow template =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(3)
              .simulation(simulation)
              .isEdited(false)
              .rateLimitEnabled(true)
              .maxAttempts(5)
              .maxTemporalRateSeconds(15L)
              .timeoutEnabled(true)
              .timeoutSeconds(120L)
              .safeModeEnabled(false)
              .build();

      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflowSimulation(template);

      // Assert
      assertTrue(result.isRateLimitEnabled());
      assertEquals(5, result.getMaxAttempts());
      assertEquals(15L, result.getMaxTemporalRateSeconds());
      assertTrue(result.isTimeoutEnabled());
      assertEquals(120L, result.getTimeoutSeconds());
      assertFalse(result.isSafeModeEnabled());
    }

    @Test
    @DisplayName("should copy scope rules as new instances linked to the run")
    void shouldCopyScopeRulesAsNewInstancesLinkedToRun() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      String templateId = UUID.randomUUID().toString();

      Workflow template =
          Workflow.builder()
              .id(templateId)
              .status(WorkflowStatus.TEMPLATE)
              .version(1)
              .simulation(simulation)
              .isEdited(false)
              .build();

      WorkflowScopeRule existingRule = new WorkflowScopeRule();
      existingRule.setSelectedMode(ScopeRuleSelectedMode.ALLOWLIST);
      existingRule.setRuleSource(ScopeRuleSource.MANUAL);
      existingRule.setRuleValue("10.0.0.1");
      existingRule.setValueType(ScopeRuleValueType.IP);
      existingRule.setWorkflow(template);

      // copyScopeRules reads from the repository, not from the entity collection
      when(workflowScopeRuleRepository.findAllByWorkflowId(templateId))
          .thenReturn(List.of(existingRule));
      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflowSimulation(template);

      // Assert - one save: for the run (no version bump since template is not edited)
      verify(workflowRepository, times(1)).save(any(Workflow.class));

      List<WorkflowScopeRule> copiedRules = result.getWorkflowScopeRules();
      assertEquals(1, copiedRules.size());
      WorkflowScopeRule copiedRule = copiedRules.getFirst();
      // new instance, not the same object
      assertNotSame(existingRule, copiedRule);
      assertEquals(existingRule.getRuleValue(), copiedRule.getRuleValue());
      assertEquals(existingRule.getSelectedMode(), copiedRule.getSelectedMode());
      assertSame(result, copiedRule.getWorkflow());
    }
  }

  // ========================================================================
  // isSimulationChaining Tests
  // ========================================================================
  @Nested
  @DisplayName("isSimulationChaining")
  class IsSimulationChainingTests {

    static Stream<Arguments> testCases() {
      return Stream.of(
          Arguments.of("single", List.of(mock(Workflow.class)), true),
          Arguments.of("multiple", List.of(mock(Workflow.class), mock(Workflow.class)), true),
          Arguments.of("none", Collections.emptyList(), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testCases")
    void shouldReturnExpectedResult(String caseName, List<Workflow> workflows, boolean expected) {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      when(workflowRepository.findAllBySimulation_Id(simulationId)).thenReturn(workflows);

      // Act
      boolean result = workflowService.isSimulationChaining(simulationId);

      // Assert
      assertNotNull(caseName);
      assertEquals(expected, result);
      verify(workflowRepository).findAllBySimulation_Id(simulationId);
    }
  }

  // ========================================================================
  // findWorkflowTemplateBySimulationId Tests
  // ========================================================================
  @Nested
  @DisplayName("findWorkflowTemplateBySimulationId")
  class FindWorkflowTemplateBySimulationIdTests {

    @DisplayName("should return workflow template when found")
    @Test
    void shouldReturnTemplateWhenFound() {
      String simulationId = UUID.randomUUID().toString();
      Workflow template = mock(Workflow.class);
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(template);

      Optional<Workflow> result = workflowService.findWorkflowTemplateBySimulationId(simulationId);

      assertTrue(result.isPresent());
      assertSame(template, result.orElseThrow());
      verify(workflowRepository)
          .findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE);
    }

    @DisplayName("should return empty when template not found")
    @Test
    void shouldReturnEmptyWhenNotFound() {
      String simulationId = UUID.randomUUID().toString();
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(null);

      Optional<Workflow> result = workflowService.findWorkflowTemplateBySimulationId(simulationId);

      assertTrue(result.isEmpty());
      verify(workflowRepository)
          .findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE);
    }
  }

  // ========================================================================
  // deleteWorkflow Tests
  // ========================================================================
  @Nested
  @DisplayName("deleteWorkflow")
  class DeleteWorkflowTests {

    @Test
    @DisplayName("should delete workflow by ID")
    void shouldDeleteWorkflowById() {
      String workflowId = UUID.randomUUID().toString();

      workflowService.deleteWorkflow(workflowId);

      verify(workflowRepository).deleteById(workflowId);
      verifyNoMoreInteractions(workflowRepository);
    }
  }

  @Nested
  @DisplayName("getWorkflowConfiguration")
  class GetWorkflowConfigurationTests {

    @Test
    @DisplayName("should return template workflow carrying inline configuration")
    void shouldReturnTemplateWorkflow() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      // Act
      Workflow result = workflowService.getWorkflowConfiguration(workflowId);

      // Assert
      assertSame(workflow, result);
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }

    @DisplayName("should throw ElementNotFoundException when workflow not found")
    @Test
    void shouldThrowWhenWorkflowMissing() {
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getWorkflowConfiguration(workflowId));
      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }
  }

  @Nested
  @DisplayName("start workflow delegation")
  class StartWorkflowDelegationTests {

    @Test
    @DisplayName("should delegate simulation start to workflow execution orchestrator")
    void shouldDelegateSimulationStartToOrchestrator() throws Exception {
      String simulationId = UUID.randomUUID().toString();
      Workflow template = Workflow.builder().id("template").status(WorkflowStatus.TEMPLATE).build();
      Workflow run =
          Workflow.builder()
              .id("run")
              .status(WorkflowStatus.RUN)
              .workflowTemplate(template)
              .build();

      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(template);
      when(workflowScopeRuleRepository.findAllByWorkflowId("template"))
          .thenReturn(Collections.emptyList());
      when(workflowRepository.save(any(Workflow.class))).thenReturn(run);
      when(workflowRepository.findById(any(String.class))).thenReturn(Optional.ofNullable(run));

      workflowService.startWorkflowBySimulationId(simulationId);

      verify(workflowStateService).syncState(any(), any(), eq(run));
      verify(stepService).findAllStepTemplateByWorkflow("template");
    }

    @Test
    @DisplayName("should copy scenario step templates then delegate start to orchestrator")
    void shouldCopyScenarioStepTemplatesThenDelegateStart() throws Exception {
      String scenarioId = UUID.randomUUID().toString();
      Exercise simulation = mock(Exercise.class);

      Workflow scenarioTemplate =
          Workflow.builder().id("scenario-template").status(WorkflowStatus.TEMPLATE).build();
      Workflow simulationTemplate =
          Workflow.builder()
              .id("simulation-template")
              .status(WorkflowStatus.TEMPLATE)
              .workflowTemplate(scenarioTemplate)
              .build();
      Workflow run =
          Workflow.builder()
              .id("run")
              .status(WorkflowStatus.RUN)
              .workflowTemplate(simulationTemplate)
              .build();

      when(workflowRepository.findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE))
          .thenReturn(List.of(scenarioTemplate));
      when(workflowScopeRuleRepository.findAllByWorkflowId("scenario-template"))
          .thenReturn(Collections.emptyList());
      when(workflowScopeRuleRepository.findAllByWorkflowId("simulation-template"))
          .thenReturn(Collections.emptyList());
      when(workflowRepository.save(any(Workflow.class)))
          .thenReturn(simulationTemplate)
          .thenReturn(run);
      when(workflowRepository.findById(any(String.class))).thenReturn(Optional.ofNullable(run));

      workflowService.startWorkflowByScenarioIdAndSimulation(scenarioId, simulation);

      verify(stepService).copyStepTemplate(scenarioTemplate, simulationTemplate);
      verify(workflowStateService).syncState(any(), any(), eq(run));
      verify(stepService).findAllStepTemplateByWorkflow("simulation-template");
    }

    @Test
    @DisplayName("should seed scope values and mapped output types")
    void shouldSeedScopeValuesAndMappedOutputTypes() throws Exception {
      Workflow workflowTemplate =
          Workflow.builder().id("template").status(WorkflowStatus.TEMPLATE).build();
      Workflow run =
          Workflow.builder()
              .id("run")
              .status(WorkflowStatus.RUN)
              .workflowTemplate(workflowTemplate)
              .workflowScopeRules(
                  new java.util.ArrayList<>(
                      List.of(
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.IP)
                              .ruleValue("192.168.10.10")
                              .build(),
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.DOMAIN)
                              .ruleValue("example.org")
                              .build(),
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.IP_SUBNET)
                              .ruleValue("10.0.0.0/24")
                              .build(),
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.IP_SUBNET)
                              .ruleValue("2001:db8::/126")
                              .build(),
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.ASSET_ID)
                              .ruleValue("asset-123")
                              .build(),
                          WorkflowScopeRule.builder()
                              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                              .valueType(ScopeRuleValueType.ASSET_GROUP_ID)
                              .ruleValue("group-456")
                              .build())))
              .build();

      when(stepService.findAllStepTemplateByWorkflow("template"))
          .thenReturn(Collections.emptyList());
      when(workflowRepository.findById(any(String.class))).thenReturn(Optional.ofNullable(run));

      workflowService.startWorkflow(run);

      ArgumentCaptor<JsonElement> dataCaptor = ArgumentCaptor.forClass(JsonElement.class);
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, ChainingMappedType>> scopeTypeCaptor =
          ArgumentCaptor.forClass(Map.class);
      verify(workflowStateService)
          .syncState(dataCaptor.capture(), scopeTypeCaptor.capture(), eq(run));

      JsonObject mappedScopeData = dataCaptor.getValue().getAsJsonObject();
      assertEquals(
          "192.168.10.10",
          mappedScopeData.getAsJsonArray(ScopeRuleValueType.IP.name()).get(0).getAsString());
      assertEquals(
          "example.org",
          mappedScopeData.getAsJsonArray(ScopeRuleValueType.DOMAIN.name()).get(0).getAsString());
      assertEquals(
          "10.0.0.0/24",
          mappedScopeData.getAsJsonArray(ScopeRuleValueType.IP_SUBNET.name()).get(0).getAsString());
      assertEquals(
          "2001:db8::/126",
          mappedScopeData.getAsJsonArray(ScopeRuleValueType.IP_SUBNET.name()).get(1).getAsString());
      assertEquals(
          "asset-123",
          mappedScopeData.getAsJsonArray(ScopeRuleValueType.ASSET_ID.name()).get(0).getAsString());
      assertEquals(
          "group-456",
          mappedScopeData
              .getAsJsonArray(ScopeRuleValueType.ASSET_GROUP_ID.name())
              .get(0)
              .getAsString());

      assertEquals(
          ChainingTypeKind.PRIMITIVE,
          scopeTypeCaptor.getValue().get(ScopeRuleValueType.IP.name()).kind());
      assertEquals(
          List.of(PrimitiveType.IPv4, PrimitiveType.IPv6),
          scopeTypeCaptor.getValue().get(ScopeRuleValueType.IP.name()).primitiveTypes());
      assertEquals(
          List.of(PrimitiveType.Domain),
          scopeTypeCaptor.getValue().get(ScopeRuleValueType.DOMAIN.name()).primitiveTypes());
      assertEquals(
          List.of(PrimitiveType.IpSubnet),
          scopeTypeCaptor.getValue().get(ScopeRuleValueType.IP_SUBNET.name()).primitiveTypes());
      assertEquals(
          List.of(PrimitiveType.AssetId),
          scopeTypeCaptor.getValue().get(ScopeRuleValueType.ASSET_ID.name()).primitiveTypes());
      assertEquals(
          List.of(PrimitiveType.AssetGroupId),
          scopeTypeCaptor
              .getValue()
              .get(ScopeRuleValueType.ASSET_GROUP_ID.name())
              .primitiveTypes());

      assertTrue(mappedScopeData.has(PrimitiveType.IPv4.name()));
      assertEquals(254, mappedScopeData.getAsJsonArray(PrimitiveType.IPv4.name()).size());
      assertEquals(
          "10.0.0.1",
          mappedScopeData.getAsJsonArray(PrimitiveType.IPv4.name()).get(0).getAsString());
      assertEquals(
          "10.0.0.254",
          mappedScopeData
              .getAsJsonArray(PrimitiveType.IPv4.name())
              .get(mappedScopeData.getAsJsonArray(PrimitiveType.IPv4.name()).size() - 1)
              .getAsString());

      assertTrue(mappedScopeData.has(PrimitiveType.IPv6.name()));
      assertEquals(4, mappedScopeData.getAsJsonArray(PrimitiveType.IPv6.name()).size());
      assertEquals(
          List.of(PrimitiveType.IPv4),
          scopeTypeCaptor.getValue().get(PrimitiveType.IPv4.name()).primitiveTypes());
      assertEquals(
          List.of(PrimitiveType.IPv6),
          scopeTypeCaptor.getValue().get(PrimitiveType.IPv6.name()).primitiveTypes());
    }
  }

  @Nested
  @DisplayName("updateWorkflowConfiguration")
  class UpdateWorkflowConfigurationTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;

    @Test
    @DisplayName("should apply input to workflow and save it when a field changed")
    void shouldApplyInputToWorkflowAndSaveIt() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);

      // rateLimitEnabled differs from mock default (false) -> change detected
      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setRateLimitEnabled(true);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowsExecuted()).thenReturn(Collections.emptyList());

      // Act
      Workflow result = workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert - service loads the entity, applies the input, saves, and returns the original
      // entity
      verify(workflowRepository, times(1)).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflowRepository).save(workflowCaptor.capture());
      assertSame(workflow, workflowCaptor.getValue());
      assertSame(workflow, result);
    }

    @DisplayName("should throw ElementNotFoundException when workflow is missing")
    @Test
    void shouldThrowWhenWorkflowMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.updateWorkflowConfiguration(workflowId, input));
      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("should apply scope rules onto workflow and persist them")
    void shouldMapScopeRulesOntoWorkflowUsingRealMapper() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder().id(workflowId).status(WorkflowStatus.TEMPLATE).version(0).build();

      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setSafeModeEnabled(true);
      input.setWorkflowScopeRules(WorkflowFixture.getDefaultWorkflowScopeRuleInputList());
      // Service now owns the apply logic - no manual mapper call needed
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      Workflow result = workflowService.updateWorkflowConfiguration(workflowId, input);

      assertSame(workflow, result);
      assertEquals(5, result.getWorkflowScopeRules().size());
      assertEquals(3, result.getAllowlist().size());
      assertEquals(2, result.getDenylist().size());

      WorkflowScopeRule mappedIpRule =
          result.getAllowlist().stream()
              .filter(r -> "10.10.10.10".equals(r.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP, mappedIpRule.getValueType());
      assertSame(workflow, mappedIpRule.getWorkflow());

      WorkflowScopeRule mappedDomainRule =
          result.getAllowlist().stream()
              .filter(r -> "example.org".equals(r.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.DOMAIN, mappedDomainRule.getValueType());

      WorkflowScopeRule mappedAssetRule =
          result.getAllowlist().stream()
              .filter(r -> "asset-123".equals(r.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_ID, mappedAssetRule.getValueType());

      WorkflowScopeRule mappedSubnetRule =
          result.getDenylist().stream()
              .filter(r -> "10.10.10.0/24".equals(r.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP_SUBNET, mappedSubnetRule.getValueType());

      WorkflowScopeRule mappedAssetGroupRule =
          result.getDenylist().stream()
              .filter(r -> "asset-group-1".equals(r.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_GROUP_ID, mappedAssetGroupRule.getValueType());
    }

    @Test
    @DisplayName("should realign step templates on the new scope when scope rules changed")
    void given_changedScopeRules_should_realignStepTemplatesOnNewScope() {
      // Arrange - an action was authored before the asset was added to the allowlist
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder().id(workflowId).status(WorkflowStatus.TEMPLATE).version(0).build();
      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setWorkflowScopeRules(WorkflowFixture.getDefaultWorkflowScopeRuleInputList());
      Asset asset = new Asset();
      asset.setId("asset-123");

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      when(scopeService.getValidAssets(workflowId)).thenReturn(List.of(asset));

      // Act
      workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert - the scope is pushed onto the already-authored step templates
      verify(workflowRepository).flush();
      verify(stepService).syncScopeAssetsOnStepTemplates(workflow, List.of("asset-123"));
    }

    @Test
    @DisplayName("should not realign step templates when no scope rule changed")
    void given_unchangedScopeRules_should_notRealignStepTemplates() {
      // Arrange - only a rate-limit field changes
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder().id(workflowId).status(WorkflowStatus.TEMPLATE).version(0).build();
      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setRateLimitEnabled(true);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert
      verify(stepService, never()).syncScopeAssetsOnStepTemplates(any(), any());
      verify(scopeService, never()).getValidAssets(any());
    }
  }

  @Nested
  @DisplayName("template scope writes should realign action targets")
  class TemplateScopeRealignmentTests {

    @Test
    @DisplayName("writeAllowlistScope should realign scenario template when rules changed")
    void writeAllowlistScope_should_realignScenarioTemplate_whenRulesChanged() {
      // Arrange
      Workflow template =
          Workflow.builder().id("wf-template").status(WorkflowStatus.TEMPLATE).version(0).build();
      WorkflowScopeRuleInput rule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-1")
              .build();
      Asset asset = new Asset();
      asset.setId("asset-1");

      when(workflowRepository.findByScenario_IdAndStatus("scenario-1", WorkflowStatus.TEMPLATE))
          .thenReturn(List.of(template));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      when(scopeService.getValidAssets("wf-template")).thenReturn(List.of(asset));

      // Act
      workflowService.writeAllowlistScope("scenario-1", null, List.of(rule), false);

      // Assert
      verify(workflowRepository).flush();
      verify(stepService).syncScopeAssetsOnStepTemplates(template, List.of("asset-1"));
    }

    @Test
    @DisplayName("writeScopeRules should realign scenario template when rules changed")
    void writeScopeRules_should_realignScenarioTemplate_whenRulesChanged() {
      // Arrange
      Workflow template =
          Workflow.builder().id("wf-template").status(WorkflowStatus.TEMPLATE).version(0).build();
      WorkflowScopeRuleInput rule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("group-1")
              .build();
      Asset asset = new Asset();
      asset.setId("asset-2");

      when(workflowRepository.findByScenario_IdAndStatus("scenario-1", WorkflowStatus.TEMPLATE))
          .thenReturn(List.of(template));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      when(scopeService.getValidAssets("wf-template")).thenReturn(List.of(asset));

      // Act
      workflowService.writeScopeRules("scenario-1", null, List.of(rule));

      // Assert
      verify(workflowRepository).flush();
      verify(stepService).syncScopeAssetsOnStepTemplates(template, List.of("asset-2"));
    }

    @Test
    @DisplayName("writeScopeRules should not realign when nothing changed")
    void writeScopeRules_should_notRealign_whenNoRuleChanged() {
      // Arrange
      Workflow template =
          Workflow.builder().id("wf-template").status(WorkflowStatus.TEMPLATE).version(0).build();
      WorkflowScopeRule existing =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-1")
              .workflow(template)
              .build();
      template.getWorkflowScopeRules().add(existing);

      WorkflowScopeRuleInput sameRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-1")
              .build();

      when(workflowRepository.findByScenario_IdAndStatus("scenario-1", WorkflowStatus.TEMPLATE))
          .thenReturn(List.of(template));

      // Act
      workflowService.writeScopeRules("scenario-1", null, List.of(sameRule));

      // Assert
      verify(stepService, never()).syncScopeAssetsOnStepTemplates(any(), any());
      verify(scopeService, never()).getValidAssets(any());
      verify(workflowRepository, never()).flush();
    }

    @Test
    @DisplayName("cleanScopeRulesSimulation should realign when ghost rules are removed")
    void cleanScopeRulesSimulation_should_realign_whenGhostRulesRemoved() {
      // Arrange
      Workflow template =
          Workflow.builder()
              .id("wf-template")
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .simulation(new Exercise())
              .build();
      WorkflowScopeRule ghostRule =
          WorkflowScopeRule.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-ghost")
              .workflow(template)
              .build();
      template.getWorkflowScopeRules().add(ghostRule);

      Asset asset = new Asset();
      asset.setId("asset-1");

      when(workflowRepository.findBySimulation_IdAndStatus("sim-1", WorkflowStatus.TEMPLATE))
          .thenReturn(template);
      when(scopeSnapshotService.buildCurrentSnapshot(ghostRule)).thenReturn(null);
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      when(scopeService.getValidAssets("wf-template")).thenReturn(List.of(asset));

      // Act
      workflowService.cleanScopeRulesSimulation("sim-1");

      // Assert
      verify(workflowRepository).flush();
      verify(stepService).syncScopeAssetsOnStepTemplates(template, List.of("asset-1"));
    }
  }

  @Nested
  @DisplayName("scope rule value type detection")
  class ScopeRuleValueTypeTests {

    static Stream<Arguments> valueTypeCases() {
      return Stream.of(
          Arguments.of(
              "IPv4 subnet", ScopeRuleSource.MANUAL, "10.0.0.0/24", ScopeRuleValueType.IP_SUBNET),
          Arguments.of("IPv4 address", ScopeRuleSource.MANUAL, "10.0.0.1", ScopeRuleValueType.IP),
          Arguments.of("domain", ScopeRuleSource.MANUAL, "example.org", ScopeRuleValueType.DOMAIN),
          Arguments.of(
              "asset id", ScopeRuleSource.ASSET, "any-asset-uuid", ScopeRuleValueType.ASSET_ID),
          Arguments.of(
              "asset group id",
              ScopeRuleSource.ASSET_GROUP,
              "any-group-uuid",
              ScopeRuleValueType.ASSET_GROUP_ID));
    }

    @ParameterizedTest(name = "{0}: source={1}, value={2} -> {3}")
    @MethodSource("valueTypeCases")
    @DisplayName("should resolve correct value type from source and value")
    void given_scopeRuleInput_should_resolveCorrectValueType(
        String caseName,
        ScopeRuleSource source,
        String ruleValue,
        ScopeRuleValueType expectedType) {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder().id(workflowId).status(WorkflowStatus.TEMPLATE).version(0).build();

      WorkflowScopeRuleInput ruleInput =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(source)
              .ruleValue(ruleValue)
              .build();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder().workflowScopeRules(List.of(ruleInput)).build();

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow result = workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert
      assertNotNull(caseName);
      assertEquals(1, result.getAllowlist().size());
      WorkflowScopeRule mappedRule = result.getAllowlist().getFirst();
      assertEquals(ScopeRuleSelectedMode.ALLOWLIST, mappedRule.getSelectedMode());
      assertEquals(expectedType, mappedRule.getValueType());
      assertSame(workflow, mappedRule.getWorkflow());
    }
  }

  // ========================================================================
  // updateWorkflowConfiguration - logic-map freeze (ADR-005)
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration - logic-map freeze (ADR-005)")
  class LogicMapFreezeTests {

    private Workflow buildTemplateWithSimulation(ExerciseStatus status) {
      String workflowId = UUID.randomUUID().toString();
      Exercise simulation = new Exercise();
      simulation.setStatus(status);
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .simulation(simulation)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      return workflow;
    }

    @Test
    @DisplayName("given a SCHEDULED simulation should allow updating the configuration")
    void given_scheduledSimulation_should_allowUpdate() {
      // Arrange
      Workflow workflow = buildTemplateWithSimulation(ExerciseStatus.SCHEDULED);

      // Act & Assert
      assertDoesNotThrow(
          () ->
              workflowService.updateWorkflowConfiguration(
                  workflow.getId(), new WorkflowConfigurationInput()));
    }

    @Test
    @DisplayName("given a RUNNING simulation should reject updating the configuration")
    void given_runningSimulation_should_rejectUpdate() {
      // Arrange
      Workflow workflow = buildTemplateWithSimulation(ExerciseStatus.RUNNING);

      // Act & Assert
      assertThrows(
          WorkflowNotEditableException.class,
          () ->
              workflowService.updateWorkflowConfiguration(
                  workflow.getId(), new WorkflowConfigurationInput()));
    }

    @Test
    @DisplayName("given a FINISHED simulation should reject updating the configuration")
    void given_finishedSimulation_should_rejectUpdate() {
      // Arrange
      Workflow workflow = buildTemplateWithSimulation(ExerciseStatus.FINISHED);

      // Act & Assert
      assertThrows(
          WorkflowNotEditableException.class,
          () ->
              workflowService.updateWorkflowConfiguration(
                  workflow.getId(), new WorkflowConfigurationInput()));
    }

    @Test
    @DisplayName("given a CANCELED simulation should reject updating the configuration")
    void given_canceledSimulation_should_rejectUpdate() {
      // Arrange
      Workflow workflow = buildTemplateWithSimulation(ExerciseStatus.CANCELED);

      // Act & Assert
      assertThrows(
          WorkflowNotEditableException.class,
          () ->
              workflowService.updateWorkflowConfiguration(
                  workflow.getId(), new WorkflowConfigurationInput()));
    }

    @Test
    @DisplayName("given a scenario-owned workflow (no simulation) should allow updating")
    void given_scenarioWorkflow_should_allowUpdate() {
      // Arrange
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      // Act & Assert
      assertDoesNotThrow(
          () ->
              workflowService.updateWorkflowConfiguration(
                  workflow.getId(), new WorkflowConfigurationInput()));
    }
  }

  // ========================================================================
  // updateWorkflowConfiguration - scope variables
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration - scope variables")
  class ScopeVariablesTests {

    private WorkflowService service;

    @BeforeEach
    void setUp() {
      service =
          new WorkflowService(
              stepService,
              conditionService,
              workflowStateService,
              stepDelayQueueService,
              scopeSnapshotService,
              scopeService,
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              assetRepository,
              assetGroupRepository,
              teamRepository,
              userRepository,
              scopeMetricCollector,
              chainingSafetyPolicyMetricCollector,
              resultsMetricCollector);
    }

    private Workflow buildTemplate() {
      return buildTemplate(true);
    }

    private Workflow buildTemplate(boolean stubSave) {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      if (stubSave) {
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      }
      return workflow;
    }

    @Test
    @DisplayName("should create new scope variable when id is null")
    void given_newScopeVariableInput_should_createVariable() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      ScopeVariableInput input =
          new ScopeVariableInput(null, "company_name", PrimitiveType.Text, "Acme", "Company name");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertEquals(1, result.getWorkflowScopeVariables().size());
      ScopeVariable created = result.getWorkflowScopeVariables().getFirst();
      assertEquals("company_name", created.getKey());
      assertEquals(PrimitiveType.Text, created.getType());
      assertEquals("Acme", created.getValue());
      assertEquals("Company name", created.getDescription());
      assertSame(workflow, created.getWorkflow());
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should update existing scope variable when id matches")
    void given_existingScopeVariableId_should_updateVariable() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      ScopeVariable existing = new ScopeVariable();
      existing.setKey("old_key");
      existing.setType(PrimitiveType.Text);
      existing.setValue("old_value");
      existing.setDescription("old desc");
      existing.setWorkflow(workflow);
      // Simulate UUID assigned by JPA
      String varId = UUID.randomUUID().toString();
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      ScopeVariableInput input =
          new ScopeVariableInput(varId, "new_key", PrimitiveType.IPv4, "10.0.0.1", "new desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert - same instance mutated in-place
      assertEquals(1, result.getWorkflowScopeVariables().size());
      ScopeVariable updated = result.getWorkflowScopeVariables().getFirst();
      assertSame(existing, updated);
      assertEquals("new_key", updated.getKey());
      assertEquals(PrimitiveType.IPv4, updated.getType());
      assertEquals("10.0.0.1", updated.getValue());
      assertEquals("new desc", updated.getDescription());
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should remove scope variable when not present in input")
    void given_removedScopeVariableId_should_deleteVariable() {
      // Arrange
      Workflow workflow = buildTemplate();
      ScopeVariable existing = new ScopeVariable();
      existing.setKey("to_remove");
      existing.setType(PrimitiveType.Text);
      existing.setWorkflow(workflow);
      String varId = UUID.randomUUID().toString();
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      // Input omits the existing variable -> it should be removed
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of());

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertTrue(result.getWorkflowScopeVariables().isEmpty());
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should not save when variables are unchanged")
    void given_unchangedScopeVariables_should_notSave() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      ScopeVariable existing = new ScopeVariable();
      String varId = UUID.randomUUID().toString();
      existing.setKey("my_key");
      existing.setType(PrimitiveType.Text);
      existing.setValue("val");
      existing.setDescription("desc");
      existing.setWorkflow(workflow);
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      // Input is identical to existing variable
      ScopeVariableInput input =
          new ScopeVariableInput(varId, "my_key", PrimitiveType.Text, "val", "desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert - no change detected, save must not be called
      assertSame(workflow, result);
      verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("should not save when both existing and input variables are empty")
    void given_emptyVariablesOnBothSides_should_notSave() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of());

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertSame(workflow, result);
      verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("should preserve raw sensitive value when update input sends masked echo")
    void given_maskedEchoForSensitiveVariable_should_preserveRawValue() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      ScopeVariable existing = new ScopeVariable();
      String varId = UUID.randomUUID().toString();
      existing.setKey("password_var");
      existing.setType(PrimitiveType.Password);
      existing.setValue("TopSecret");
      existing.setDescription("desc");
      existing.setWorkflow(workflow);
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      ScopeVariableInput input =
          new ScopeVariableInput(
              varId, "password_var", PrimitiveType.Password, "T*******t", "desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertSame(workflow, result);
      assertEquals("TopSecret", result.getWorkflowScopeVariables().getFirst().getValue());
      verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("should preserve raw sensitive value when type changes and input is masked echo")
    void given_typeChangeAndMaskedEcho_should_preserveRawValue() {
      // Arrange
      Workflow workflow = buildTemplate(false);
      ScopeVariable existing = new ScopeVariable();
      String varId = UUID.randomUUID().toString();
      existing.setKey("password_var");
      existing.setType(PrimitiveType.Password);
      existing.setValue("TopSecret");
      existing.setDescription("desc");
      existing.setWorkflow(workflow);
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      ScopeVariableInput input =
          new ScopeVariableInput(varId, "password_var", PrimitiveType.Text, "T*******t", "desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertSame(workflow, result);
      ScopeVariable updated = result.getWorkflowScopeVariables().getFirst();
      assertEquals(PrimitiveType.Text, updated.getType());
      assertEquals("TopSecret", updated.getValue());
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should copy scope variables when launching a workflow simulation")
    void given_templateWithScopeVariables_should_copyThemToRun() {
      // Arrange
      String templateId = UUID.randomUUID().toString();
      Exercise simulation = mock(Exercise.class);
      Workflow template =
          Workflow.builder()
              .id(templateId)
              .status(WorkflowStatus.TEMPLATE)
              .version(1)
              .simulation(simulation)
              .isEdited(false)
              .build();

      ScopeVariable sourceVar = new ScopeVariable();
      sourceVar.setKey("env");
      sourceVar.setType(PrimitiveType.Text);
      sourceVar.setValue("production");
      sourceVar.setDescription("Environment name");
      sourceVar.setWorkflow(template);

      when(workflowScopeRuleRepository.findAllByWorkflowId(templateId)).thenReturn(List.of());
      when(scopeVariableRepository.findAllByWorkflowId(templateId)).thenReturn(List.of(sourceVar));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow run = service.launchWorkflowSimulation(template);

      // Assert
      assertEquals(1, run.getWorkflowScopeVariables().size());
      ScopeVariable copiedVar = run.getWorkflowScopeVariables().getFirst();
      assertNotSame(sourceVar, copiedVar);
      assertEquals("env", copiedVar.getKey());
      assertEquals(PrimitiveType.Text, copiedVar.getType());
      assertEquals("production", copiedVar.getValue());
      assertSame(run, copiedVar.getWorkflow());
    }
  }

  // ========================================================================
  // Scope Metrics Tests
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration - scope metrics")
  class ScopeMetricsTests {

    private WorkflowService service;

    @BeforeEach
    void setUp() {
      service =
          new WorkflowService(
              stepService,
              conditionService,
              workflowStateService,
              stepDelayQueueService,
              scopeSnapshotService,
              scopeService,
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              assetRepository,
              assetGroupRepository,
              teamRepository,
              userRepository,
              scopeMetricCollector,
              chainingSafetyPolicyMetricCollector,
              resultsMetricCollector);
    }

    private Workflow buildTemplate() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      lenient()
          .when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(i -> i.getArgument(0));
      return workflow;
    }

    @Test
    @DisplayName("should record metrics for new scope rules")
    void given_newScopeRules_should_trackMetrics() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(WorkflowFixture.getDefaultWorkflowScopeRuleInputList())
              .build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert - creation metrics recorded per mode
      verify(scopeMetricCollector).recordScopeCreated("ALLOWLIST", 3);
      verify(scopeMetricCollector).recordScopeCreated("DENYLIST", 2);

      // Assert - entry-added metrics recorded per type|source
      verify(scopeMetricCollector).recordEntryAdded("IP", "MANUAL", 1);
      verify(scopeMetricCollector).recordEntryAdded("DOMAIN", "MANUAL", 1);
      verify(scopeMetricCollector).recordEntryAdded("ASSET_ID", "ASSET", 1);
      verify(scopeMetricCollector).recordEntryAdded("IP_SUBNET", "MANUAL", 1);
      verify(scopeMetricCollector).recordEntryAdded("ASSET_GROUP_ID", "ASSET_GROUP", 1);

      // Assert - usage recorded only for CSV/MANUAL, not ASSET/ASSET_GROUP
      verify(scopeMetricCollector).recordUsage(workflow.getId(), "MANUAL");
      verify(scopeMetricCollector, never()).recordUsage(anyString(), eq("ASSET"));
      verify(scopeMetricCollector, never()).recordUsage(anyString(), eq("ASSET_GROUP"));
    }

    @Test
    @DisplayName("should not record metrics when only existing rules are retained")
    void given_retainedExistingRules_should_notTrackMetrics() {
      // Arrange
      Workflow workflow = buildTemplate();

      // First call: add new rules
      WorkflowConfigurationInput initialInput =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(
                  List.of(
                      WorkflowScopeRuleInput.builder()
                          .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                          .ruleSource(ScopeRuleSource.MANUAL)
                          .ruleValue("10.0.0.1")
                          .build()))
              .build();
      service.updateWorkflowConfiguration(workflow.getId(), initialInput);
      workflow.getWorkflowScopeRules().getFirst().setId(UUID.randomUUID().toString());
      reset(scopeMetricCollector);

      // Second call: same rules (now have IDs) - no new rules
      WorkflowScopeRule existingRule = workflow.getWorkflowScopeRules().getFirst();
      WorkflowScopeRuleInput retainedInput =
          WorkflowScopeRuleInput.builder()
              .id(existingRule.getId())
              .selectedMode(existingRule.getSelectedMode())
              .ruleSource(existingRule.getRuleSource())
              .ruleValue(existingRule.getRuleValue())
              .build();
      WorkflowConfigurationInput secondInput =
          WorkflowConfigurationInput.builder().workflowScopeRules(List.of(retainedInput)).build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), secondInput);

      // Assert - no metric calls since no new (ID-less) rules were added
      verifyNoInteractions(scopeMetricCollector);
    }

    @Test
    @DisplayName("should record metrics only for new rules when mixed with existing ones")
    void given_mixOfNewAndExistingRules_should_trackOnlyNewRules() {
      // Arrange
      Workflow workflow = buildTemplate();

      // First call: seed one existing rule
      WorkflowConfigurationInput initialInput =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(
                  List.of(
                      WorkflowScopeRuleInput.builder()
                          .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                          .ruleSource(ScopeRuleSource.MANUAL)
                          .ruleValue("10.0.0.1")
                          .build()))
              .build();
      service.updateWorkflowConfiguration(workflow.getId(), initialInput);
      workflow.getWorkflowScopeRules().getFirst().setId(UUID.randomUUID().toString());
      reset(scopeMetricCollector);

      // Second call: retain existing + add one new CSV rule
      WorkflowScopeRule existingRule = workflow.getWorkflowScopeRules().getFirst();
      WorkflowScopeRuleInput retainedInput =
          WorkflowScopeRuleInput.builder()
              .id(existingRule.getId())
              .selectedMode(existingRule.getSelectedMode())
              .ruleSource(existingRule.getRuleSource())
              .ruleValue(existingRule.getRuleValue())
              .build();
      WorkflowScopeRuleInput newCsvRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.DENYLIST)
              .ruleSource(ScopeRuleSource.CSV)
              .ruleValue("evil.example.com")
              .build();
      WorkflowConfigurationInput secondInput =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(List.of(retainedInput, newCsvRule))
              .build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), secondInput);

      // Assert - metrics only for the one new CSV rule
      verify(scopeMetricCollector).recordScopeCreated(ScopeRuleSelectedMode.DENYLIST.name(), 1);
      verify(scopeMetricCollector)
          .recordEntryAdded(ScopeRuleValueType.DOMAIN.name(), ScopeRuleSource.CSV.name(), 1);
      verify(scopeMetricCollector).recordUsage(workflow.getId(), ScopeRuleSource.CSV.name());
      verifyNoMoreInteractions(scopeMetricCollector);
    }

    @Test
    @DisplayName("should not record metrics when no scope rules are provided")
    void given_noScopeRules_should_notTrackMetrics() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder().workflowScopeRules(List.of()).build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert
      verifyNoInteractions(scopeMetricCollector);
    }
  }

  // ========================================================================
  // Scope rule value-label snapshot Tests (#7164)
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration - scope rule value label snapshot")
  class ScopeRuleValueLabelTests {

    private static final String TENANT = "tenant-1";

    private WorkflowService service;

    @BeforeEach
    void setUp() {
      service =
          new WorkflowService(
              stepService,
              conditionService,
              workflowStateService,
              stepDelayQueueService,
              scopeSnapshotService,
              scopeService,
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              assetRepository,
              assetGroupRepository,
              teamRepository,
              userRepository,
              scopeMetricCollector,
              chainingSafetyPolicyMetricCollector,
              resultsMetricCollector);
    }

    private Workflow buildTemplate() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      lenient()
          .when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(i -> i.getArgument(0));
      return workflow;
    }

    private WorkflowConfigurationInput ruleInput(ScopeRuleSource source, String value) {
      return WorkflowConfigurationInput.builder()
          .workflowScopeRules(
              List.of(
                  WorkflowScopeRuleInput.builder()
                      .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                      .ruleSource(source)
                      .ruleValue(value)
                      .build()))
          .build();
    }

    @Test
    @DisplayName("should snapshot the tenant-scoped asset name on an ASSET rule")
    void given_assetRule_should_snapshotAssetName() {
      Workflow workflow = buildTemplate();
      Asset asset = mock(Asset.class);
      when(asset.getName()).thenReturn("Prod DB");
      when(assetRepository.findByIdAndTenantId("asset-1", TENANT)).thenReturn(Optional.of(asset));

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.ASSET, "asset-1"));

        assertEquals(1, result.getAllowlist().size());
        assertEquals("Prod DB", result.getAllowlist().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should snapshot the tenant-scoped asset-group name on an ASSET_GROUP rule")
    void given_assetGroupRule_should_snapshotAssetGroupName() {
      Workflow workflow = buildTemplate();
      AssetGroup group = mock(AssetGroup.class);
      when(group.getName()).thenReturn("Crown jewels");
      when(assetGroupRepository.findByIdAndTenantId("group-1", TENANT))
          .thenReturn(Optional.of(group));

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.ASSET_GROUP, "group-1"));

        assertEquals(1, result.getAllowlist().size());
        assertEquals("Crown jewels", result.getAllowlist().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should snapshot the tenant-scoped team name on a TEAM rule")
    void given_teamRule_should_snapshotTeamName() {
      Workflow workflow = buildTemplate();
      Team team = new Team();
      team.setId("team-1");
      team.setName("It team");
      when(teamRepository.findByIdAndTenantId("team-1", TENANT)).thenReturn(Optional.of(team));

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.TEAM, "team-1"));

        assertEquals(1, result.getAllowlist().size());
        assertEquals("It team", result.getAllowlist().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should snapshot the tenant-scoped player name-or-email on a PLAYER rule")
    void given_playerRule_should_snapshotPlayerName() {
      Workflow workflow = buildTemplate();
      User user = new User();
      user.setId("player-1");
      user.setFirstname("John");
      user.setLastname("Doe");
      user.setEmail("john.doe@filigran.io");
      when(userRepository.findAllByIdInAndTenantId(List.of("player-1"), TENANT))
          .thenReturn(List.of(user));

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.PLAYER, "player-1"));

        assertEquals(1, result.getAllowlist().size());
        assertEquals("John Doe", result.getAllowlist().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should leave the label null for a MANUAL rule")
    void given_manualRule_should_notSnapshotLabel() {
      Workflow workflow = buildTemplate();

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.MANUAL, "10.0.0.1"));

        assertEquals(1, result.getAllowlist().size());
        assertNull(result.getAllowlist().getFirst().getRuleValueLabel());
      }
      verifyNoInteractions(assetRepository, assetGroupRepository);
    }

    @Test
    @DisplayName("should leave the label null when the asset id does not resolve within the tenant")
    void given_crossTenantOrDeletedAsset_should_notSnapshotLabel() {
      Workflow workflow = buildTemplate();
      when(assetRepository.findByIdAndTenantId("asset-x", TENANT)).thenReturn(Optional.empty());

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result =
            service.updateWorkflowConfiguration(
                workflow.getId(), ruleInput(ScopeRuleSource.ASSET, "asset-x"));

        assertEquals(1, result.getAllowlist().size());
        assertNull(result.getAllowlist().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should refresh the label on update when the asset still resolves")
    void given_ruleUpdateWithLiveAsset_should_refreshLabel() {
      Workflow workflow = buildTemplate();
      WorkflowScopeRule existing =
          WorkflowScopeRule.builder()
              .id(UUID.randomUUID().toString())
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-1")
              .valueType(ScopeRuleValueType.ASSET_ID)
              .ruleValueLabel("Old name")
              .workflow(workflow)
              .build();
      workflow.getWorkflowScopeRules().add(existing);

      Asset asset = mock(Asset.class);
      when(asset.getName()).thenReturn("New name");
      when(assetRepository.findByIdAndTenantId("asset-1", TENANT)).thenReturn(Optional.of(asset));

      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(
                  List.of(
                      WorkflowScopeRuleInput.builder()
                          .id(existing.getId())
                          .selectedMode(ScopeRuleSelectedMode.DENYLIST)
                          .ruleSource(ScopeRuleSource.ASSET)
                          .ruleValue("asset-1")
                          .build()))
              .build();

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result = service.updateWorkflowConfiguration(workflow.getId(), input);

        assertEquals(1, result.getWorkflowScopeRules().size());
        assertEquals("New name", result.getWorkflowScopeRules().getFirst().getRuleValueLabel());
      }
    }

    @Test
    @DisplayName("should preserve the previous label on update when the asset was deleted")
    void given_ruleUpdateWithDeletedAsset_should_preservePreviousLabel() {
      Workflow workflow = buildTemplate();
      WorkflowScopeRule existing =
          WorkflowScopeRule.builder()
              .id(UUID.randomUUID().toString())
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-1")
              .valueType(ScopeRuleValueType.ASSET_ID)
              .ruleValueLabel("Snapshotted name")
              .workflow(workflow)
              .build();
      workflow.getWorkflowScopeRules().add(existing);

      // asset deleted -> tenant-scoped lookup returns empty
      when(assetRepository.findByIdAndTenantId("asset-1", TENANT)).thenReturn(Optional.empty());

      // only the mode changes; the referenced asset is untouched
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .workflowScopeRules(
                  List.of(
                      WorkflowScopeRuleInput.builder()
                          .id(existing.getId())
                          .selectedMode(ScopeRuleSelectedMode.DENYLIST)
                          .ruleSource(ScopeRuleSource.ASSET)
                          .ruleValue("asset-1")
                          .build()))
              .build();

      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

        Workflow result = service.updateWorkflowConfiguration(workflow.getId(), input);

        assertEquals(1, result.getWorkflowScopeRules().size());
        assertEquals(
            "Snapshotted name", result.getWorkflowScopeRules().getFirst().getRuleValueLabel());
      }
    }
  }

  // ========================================================================
  // Safety Policy Metrics Tests
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration - safety policy metrics")
  class SafetyPolicyMetrics {

    private WorkflowService service;

    @BeforeEach
    void setUp() {
      service =
          new WorkflowService(
              stepService,
              conditionService,
              workflowStateService,
              stepDelayQueueService,
              scopeSnapshotService,
              scopeService,
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              assetRepository,
              assetGroupRepository,
              teamRepository,
              userRepository,
              scopeMetricCollector,
              chainingSafetyPolicyMetricCollector,
              resultsMetricCollector);
    }

    private Workflow buildTemplate() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder()
              .id(workflowId)
              .status(WorkflowStatus.TEMPLATE)
              .version(0)
              .timeoutEnabled(false)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .rateLimitEnabled(false)
              .build();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      lenient()
          .when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(i -> i.getArgument(0));
      return workflow;
    }

    @Test
    @DisplayName("should emit timeout metric when timeout fields changed")
    void shouldEmitTimeoutMetric_whenTimeoutFieldsChanged() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder().timeoutEnabled(true).timeoutSeconds(3600L).build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert
      verify(chainingSafetyPolicyMetricCollector).recordTimeoutConfigured(1L, 0L, true);
      verify(chainingSafetyPolicyMetricCollector, never())
          .recordRateLimitConfigured(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("should emit rate limit metric when rate limit fields changed")
    void shouldEmitRateLimitMetric_whenRateLimitFieldsChanged() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .rateLimitEnabled(true)
              .maxAttempts(5)
              .maxTemporalRateSeconds(60L)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert
      verify(chainingSafetyPolicyMetricCollector).recordRateLimitConfigured(5L, 60L, false);
      verify(chainingSafetyPolicyMetricCollector, never())
          .recordTimeoutConfigured(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("should emit both metrics when both groups changed")
    void shouldEmitBothMetrics_whenBothGroupsChanged() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .timeoutEnabled(true)
              .timeoutSeconds(7200L)
              .rateLimitEnabled(true)
              .maxAttempts(5)
              .maxTemporalRateSeconds(60L)
              .build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert
      verify(chainingSafetyPolicyMetricCollector).recordTimeoutConfigured(2L, 0L, false);
      verify(chainingSafetyPolicyMetricCollector).recordRateLimitConfigured(5L, 60L, false);
    }

    @Test
    @DisplayName("should emit no metric when no fields changed")
    void shouldEmitNoMetric_whenNoFieldsChanged() {
      // Arrange
      Workflow workflow = buildTemplate();
      WorkflowConfigurationInput input =
          WorkflowConfigurationInput.builder()
              .timeoutEnabled(false)
              .timeoutSeconds(WorkflowService.DEFAULT_TIMEOUT_SECONDS)
              .rateLimitEnabled(false)
              .build();

      // Act
      service.updateWorkflowConfiguration(workflow.getId(), input);

      // Assert
      verify(chainingSafetyPolicyMetricCollector, never())
          .recordTimeoutConfigured(anyLong(), anyLong(), anyBoolean());
      verify(chainingSafetyPolicyMetricCollector, never())
          .recordRateLimitConfigured(anyLong(), anyLong(), anyBoolean());
    }
  }

  // ========================================================================
  // evaluateWorkflowProgress Tests
  // ========================================================================
  @Nested
  @DisplayName("evaluateWorkflowProgress")
  class EvaluateWorkflowProgressTests {

    /**
     * Helper: arrange the mandatory DB reload stub (introduced to handle detached entities from
     * queue jobs). Every test that reaches normal evaluation logic must stub this.
     */
    private void stubReload(String workflowRunId, Workflow workflowRun) {
      when(workflowRepository.findById(workflowRunId)).thenReturn(Optional.of(workflowRun));
    }

    @Test
    @DisplayName("given workflow run not found in DB should throw ElementNotFoundException")
    void given_workflowRunNotFound_should_throwElementNotFoundException() {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      Workflow detachedRun =
          Workflow.builder().id(workflowRunId).status(WorkflowStatus.RUN).build();
      when(workflowRepository.findById(workflowRunId)).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class,
          () -> workflowService.evaluateWorkflowProgress(detachedRun));
      verify(workflowRepository).findById(workflowRunId);
    }

    @Test
    @DisplayName("given workflow run has no template should return early without evaluating steps")
    void given_nullWorkflowTemplate_should_returnEarlyWithoutEvaluatingSteps() throws Exception {
      // Arrange - run with no template (e.g. corrupted state)
      String workflowRunId = UUID.randomUUID().toString();
      Workflow workflowRun =
          Workflow.builder().id(workflowRunId).status(WorkflowStatus.RUN).build();
      // workflowTemplate is null by default in the builder
      stubReload(workflowRunId, workflowRun);

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert - returned as-is, step service never called
      assertSame(workflowRun, result);
      verify(stepService, never()).findAllStepTemplateByWorkflow(any());
    }

    @Test
    @DisplayName("given active steps exist should not set workflow to END")
    void given_activeStepsExist_should_notEndWorkflow() throws Exception {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      String workflowTemplateId = UUID.randomUUID().toString();

      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder()
              .id(workflowRunId)
              .status(WorkflowStatus.RUN)
              .workflowTemplate(workflowTemplate)
              .build();
      stubReload(workflowRunId, workflowRun);

      Step stepTemplate = mock(Step.class);

      when(workflowRepository.existsByIdAndStatus(workflowRunId, WorkflowStatus.END))
          .thenReturn(false);
      when(stepService.findAllStepTemplateByWorkflow(workflowTemplateId))
          .thenReturn(List.of(stepTemplate));
      // Active steps (RUN or READY) exist
      when(stepService.countActiveSteps(workflowRunId)).thenReturn(2L);
      when(stepService.createReadySteps(stepTemplate, workflowRun, null, 0))
          .thenReturn(Collections.emptyList());

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert
      assertNotEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(WorkflowStatus.RUN, result.getStatus());
      verify(stepDelayQueueService, never()).findAllByWorkflowRun(any());
    }

    @Test
    @DisplayName("given steps in delay queue should not set workflow to END")
    void given_stepsInDelayQueue_should_notEndWorkflow() throws Exception {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      String workflowTemplateId = UUID.randomUUID().toString();

      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder()
              .id(workflowRunId)
              .status(WorkflowStatus.RUN)
              .workflowTemplate(workflowTemplate)
              .build();
      stubReload(workflowRunId, workflowRun);

      Step stepTemplate = mock(Step.class);

      when(workflowRepository.existsByIdAndStatus(workflowRunId, WorkflowStatus.END))
          .thenReturn(false);
      when(stepService.findAllStepTemplateByWorkflow(workflowTemplateId))
          .thenReturn(List.of(stepTemplate));
      // No active steps
      when(stepService.countActiveSteps(workflowRunId)).thenReturn(0L);
      when(stepService.createReadySteps(stepTemplate, workflowRun, null, 0))
          .thenReturn(Collections.emptyList());
      // Delay queue has entries
      StepDelayQueue delayedEntry = mock(StepDelayQueue.class);
      when(stepDelayQueueService.findAllByWorkflowRun(workflowRun))
          .thenReturn(List.of(delayedEntry));

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert
      assertNotEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(WorkflowStatus.RUN, result.getStatus());
    }

    @Test
    @DisplayName("given new ready steps created should not set workflow to END")
    void given_newReadyStepsCreated_should_notEndWorkflow() throws Exception {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      String workflowTemplateId = UUID.randomUUID().toString();

      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder()
              .id(workflowRunId)
              .status(WorkflowStatus.RUN)
              .workflowTemplate(workflowTemplate)
              .build();
      stubReload(workflowRunId, workflowRun);

      Step stepTemplate = mock(Step.class);
      Step stepReady = mock(Step.class);

      when(workflowRepository.existsByIdAndStatus(workflowRunId, WorkflowStatus.END))
          .thenReturn(false);
      when(stepService.findAllStepTemplateByWorkflow(workflowTemplateId))
          .thenReturn(List.of(stepTemplate));
      // No pre-existing active steps
      when(stepService.countActiveSteps(workflowRunId)).thenReturn(0L);
      // But createReadySteps produces a new ready step
      when(stepService.createReadySteps(stepTemplate, workflowRun, null, 0))
          .thenReturn(List.of(stepReady));

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert
      assertNotEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(WorkflowStatus.RUN, result.getStatus());
      verify(stepService).enqueueReadySteps(List.of(stepReady), workflowRun);
      verify(stepDelayQueueService, never()).findAllByWorkflowRun(any());
    }

    @Test
    @DisplayName("given no active steps and empty delay queue should set workflow to END")
    void given_noActiveStepsAndEmptyDelayQueue_should_endWorkflow() throws Exception {
      // Arrange
      String workflowRunId = UUID.randomUUID().toString();
      String workflowTemplateId = UUID.randomUUID().toString();

      Workflow workflowTemplate = Workflow.builder().id(workflowTemplateId).build();
      Workflow workflowRun =
          Workflow.builder()
              .id(workflowRunId)
              .status(WorkflowStatus.RUN)
              .workflowTemplate(workflowTemplate)
              .build();
      stubReload(workflowRunId, workflowRun);

      Step stepTemplate = mock(Step.class);

      when(workflowRepository.existsByIdAndStatus(workflowRunId, WorkflowStatus.END))
          .thenReturn(false);
      when(stepService.findAllStepTemplateByWorkflow(workflowTemplateId))
          .thenReturn(List.of(stepTemplate));
      // No active steps
      when(stepService.countActiveSteps(workflowRunId)).thenReturn(0L);
      when(stepService.createReadySteps(stepTemplate, workflowRun, null, 0))
          .thenReturn(Collections.emptyList());
      // Delay queue is empty
      when(stepDelayQueueService.findAllByWorkflowRun(workflowRun))
          .thenReturn(Collections.emptyList());

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert
      assertEquals(WorkflowStatus.END, result.getStatus());
    }
  }

  // ========================================================================
  // markSimulationWorkflowKeepAlive Tests
  // ========================================================================
  @Nested
  @DisplayName("markSimulationWorkflowKeepAlive")
  class MarkSimulationWorkflowKeepAliveTests {

    @Test
    @DisplayName("marks the simulation template and its live run keep-alive with timeout off")
    void given_templateAndLiveRun_should_markBothKeepAlive() {
      // Arrange - distinct ids matter: Workflow equality is id-only, so two id-less instances
      // would be equal and the per-instance save verifications below would blur together.
      String simulationId = UUID.randomUUID().toString();
      Workflow template =
          Workflow.builder()
              .id(UUID.randomUUID().toString())
              .status(WorkflowStatus.TEMPLATE)
              .timeoutEnabled(true)
              .build();
      Workflow run =
          Workflow.builder()
              .id(UUID.randomUUID().toString())
              .status(WorkflowStatus.RUN)
              .timeoutEnabled(true)
              .build();
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(template);
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.RUN))
          .thenReturn(List.of(run));
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.END))
          .thenReturn(Collections.emptyList());

      // Act
      workflowService.markSimulationWorkflowKeepAlive(simulationId);

      // Assert
      assertTrue(template.isKeepAlive());
      assertFalse(template.isTimeoutEnabled());
      assertTrue(run.isKeepAlive());
      assertFalse(run.isTimeoutEnabled());
      verify(workflowRepository).save(template);
      verify(workflowRepository).save(run);
    }

    @Test
    @DisplayName("restores the empty run the launch evaluation just ended back to RUN")
    void given_freshlyEndedEmptyRun_should_restoreToRunAndMarkKeepAlive() {
      // Arrange - an autonomous launch starts EMPTY, so the initial evaluation inside
      // startWorkflow ENDs the run before this method executes; the RUN finder cannot see it.
      String simulationId = UUID.randomUUID().toString();
      Workflow endedRun =
          Workflow.builder().status(WorkflowStatus.END).timeoutEnabled(true).build();
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(null);
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.RUN))
          .thenReturn(Collections.emptyList());
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.END))
          .thenReturn(List.of(endedRun));

      // Act
      workflowService.markSimulationWorkflowKeepAlive(simulationId);

      // Assert - parked back in RUN awaiting the orchestrator, keep-alive on, watchdog off.
      assertEquals(WorkflowStatus.RUN, endedRun.getStatus());
      assertTrue(endedRun.isKeepAlive());
      assertFalse(endedRun.isTimeoutEnabled());
      verify(workflowRepository).save(endedRun);
    }

    @Test
    @DisplayName("does not re-save a workflow already keep-alive with timeout off")
    void given_alreadyMarkedWorkflow_should_notSaveAgain() {
      // Arrange
      String simulationId = UUID.randomUUID().toString();
      Workflow run =
          Workflow.builder()
              .status(WorkflowStatus.RUN)
              .keepAlive(true)
              .timeoutEnabled(false)
              .build();
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(null);
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.RUN))
          .thenReturn(List.of(run));
      when(workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.END))
          .thenReturn(Collections.emptyList());

      // Act
      workflowService.markSimulationWorkflowKeepAlive(simulationId);

      // Assert
      verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("is a no-op on a blank simulation id")
    void given_blankSimulationId_should_doNothing() {
      // Act
      workflowService.markSimulationWorkflowKeepAlive("  ");

      // Assert
      verifyNoInteractions(workflowRepository);
    }
  }
}
