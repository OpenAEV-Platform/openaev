package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScopeVariableRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.PreviewFeatureService;
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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService Tests")
class WorkflowServiceTest {

  @Mock private WorkflowRepository workflowRepository;
  @Mock private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Mock private ScopeVariableRepository scopeVariableRepository;
  @Mock private PreviewFeatureService previewFeatureService;

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
    @DisplayName("should create workflow template with inline configuration defaults")
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
      assertFalse(savedWorkflow.isTimeoutEnabled());
      assertTrue(savedWorkflow.isSafeModeEnabled());
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

      // Assert — one save: for the run (no version bump since template is not edited)
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
  @DisplayName("updateWorkflowConfiguration")
  class UpdateWorkflowConfigurationTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;

    @Test
    @DisplayName("should apply input to workflow and save it when a field changed")
    void shouldApplyInputToWorkflowAndSaveIt() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);

      // rateLimitEnabled differs from mock default (false) → change detected
      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setRateLimitEnabled(true);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowsExecuted()).thenReturn(Collections.emptyList());

      // Act
      Workflow result = workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert — service loads the entity, applies the input, saves, and returns the original
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

      // Service now owns the apply logic — no manual mapper call needed
      WorkflowService service =
          new WorkflowService(
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              previewFeatureService);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      Workflow result = service.updateWorkflowConfiguration(workflowId, input);

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

      WorkflowService service =
          new WorkflowService(
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              previewFeatureService);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflowId, input);

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
  // Scope Variables Tests
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowConfiguration – scope variables")
  class ScopeVariablesTests {

    private WorkflowService service;

    @BeforeEach
    void setUp() {
      service =
          new WorkflowService(
              workflowRepository,
              workflowScopeRuleRepository,
              scopeVariableRepository,
              previewFeatureService);
    }

    private Workflow buildTemplate() {
      return buildTemplate(true);
    }

    private Workflow buildTemplate(boolean stubSave) {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow =
          Workflow.builder().id(workflowId).status(WorkflowStatus.TEMPLATE).version(0).build();
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
          new ScopeVariableInput(null, "company_name", ArgumentType.Text, "Acme", "Company name");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert
      assertEquals(1, result.getWorkflowScopeVariables().size());
      ScopeVariable created = result.getWorkflowScopeVariables().getFirst();
      assertEquals("company_name", created.getKey());
      assertEquals(ArgumentType.Text, created.getType());
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
      existing.setType(ArgumentType.Text);
      existing.setValue("old_value");
      existing.setDescription("old desc");
      existing.setWorkflow(workflow);
      // Simulate UUID assigned by JPA
      String varId = UUID.randomUUID().toString();
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      ScopeVariableInput input =
          new ScopeVariableInput(varId, "new_key", ArgumentType.IPv4, "10.0.0.1", "new desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert — same instance mutated in-place
      assertEquals(1, result.getWorkflowScopeVariables().size());
      ScopeVariable updated = result.getWorkflowScopeVariables().getFirst();
      assertSame(existing, updated);
      assertEquals("new_key", updated.getKey());
      assertEquals(ArgumentType.IPv4, updated.getType());
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
      existing.setType(ArgumentType.Text);
      existing.setWorkflow(workflow);
      String varId = UUID.randomUUID().toString();
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      // Input omits the existing variable → it should be removed
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
      existing.setType(ArgumentType.Text);
      existing.setValue("val");
      existing.setDescription("desc");
      existing.setWorkflow(workflow);
      org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", varId);
      workflow.getWorkflowScopeVariables().add(existing);

      // Input is identical to existing variable
      ScopeVariableInput input =
          new ScopeVariableInput(varId, "my_key", ArgumentType.Text, "val", "desc");
      WorkflowConfigurationInput configInput = new WorkflowConfigurationInput();
      configInput.setWorkflowScopeVariables(List.of(input));

      // Act
      Workflow result = service.updateWorkflowConfiguration(workflow.getId(), configInput);

      // Assert — no change detected, save must not be called
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
      sourceVar.setType(ArgumentType.Text);
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
      assertEquals(ArgumentType.Text, copiedVar.getType());
      assertEquals("production", copiedVar.getValue());
      assertSame(run, copiedVar.getWorkflow());
    }
  }
}
