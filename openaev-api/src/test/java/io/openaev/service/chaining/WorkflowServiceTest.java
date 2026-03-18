package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.WorkflowConfigurationMapper;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowConfigurationRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
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
  @Mock private WorkflowConfigurationRepository workflowConfigurationRepository;
  @Mock private WorkflowConfigurationMapper workflowConfigurationMapper;

  @InjectMocks private WorkflowService workflowService;

  @Nested
  @DisplayName("getWorkflowByIdAndStatus")
  class GetWorkflowByIdAndStatusTests {

    @Test
    void shouldReturnWorkflowWhenFound() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      Workflow result =
          workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);

      assertSame(workflow, result);
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }

    @Test
    void shouldThrowWhenWorkflowNotFound() {
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE));

      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }
  }

  @Nested
  @DisplayName("creationWorkflow")
  class CreationWorkflowTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;
    @Captor private ArgumentCaptor<WorkflowConfiguration> configurationArgumentCaptor;

    @Test
    @DisplayName("should create workflow template and default workflow configuration for exercise")
    void shouldCreateWorkflowTemplateAndDefaultWorkflowConfiguration() {
      // Prepare
      Exercise exercise = mock(Exercise.class);

      workflowService.creationWorkflow(exercise);

      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow savedWorkflow = workflowCaptor.getValue();
      assertEquals(0, savedWorkflow.getVersion());
      assertEquals(WorkflowStatus.TEMPLATE, savedWorkflow.getStatus());
      assertEquals(exercise, savedWorkflow.getSimulation());

      verify(workflowConfigurationRepository).save(configurationArgumentCaptor.capture());
      WorkflowConfiguration savedConfiguration = configurationArgumentCaptor.getValue();
      assertFalse(savedConfiguration.isRateLimitEnabled());
      assertFalse(savedConfiguration.isTimeoutEnabled());
      assertTrue(savedConfiguration.isSafeModeEnabled());
      assertEquals(savedWorkflow, savedConfiguration.getWorkflow());
      assertEquals(savedConfiguration, savedWorkflow.getWorkflowConfiguration());
    }
  }

  @Nested
  @DisplayName("updateWorkflowTemplate")
  class UpdateWorkflowTemplateTests {

    @Test
    void shouldMarkWorkflowAsEdited() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      when(workflow.getWorkflowsExecuted())
          .thenReturn(List.of(mock(Workflow.class))); // Simulate at least one run executed
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      workflowService.updateWorkflowTemplate(workflowId);

      // Assert
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow).setEdited(true);
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should throw ElementNotFoundException when workflow not found")
    void shouldThrowExceptionWhenNotFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      assertThrows(
          ElementNotFoundException.class, () -> workflowService.updateWorkflowTemplate(workflowId));
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflowRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("saveWorkflowRun")
  class SaveWorkflowRunTests {

    @Test
    void shouldSaveAndReturnWorkflowRun() {
      Workflow run = mock(Workflow.class);
      Workflow persisted = mock(Workflow.class);
      when(workflowRepository.save(run)).thenReturn(persisted);

      Workflow result = workflowService.saveWorkflowRun(run);

      assertSame(persisted, result);
      verify(workflowRepository).save(run);
    }
  }

  @Nested
  @DisplayName("launchWorkflow")
  class LaunchWorkflowTests {

    @Captor private ArgumentCaptor<Workflow> workflowCaptor;
    @Captor private ArgumentCaptor<WorkflowConfiguration> workflowConfigurationCaptor;

    @Test
    void shouldIncrementVersionWhenTemplateEdited() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Workflow template = mock(Workflow.class);
      when(template.isEdited()).thenReturn(true);
      when(template.getVersion()).thenReturn(1);
      when(template.getSimulation()).thenReturn(simulation);
      when(template.getWorkflowConfiguration()).thenReturn(null);
      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      workflowService.launchWorkflow(template);

      // Assert
      verify(template).setEdited(false);
      verify(template).setVersion(2);
      verify(workflowRepository, times(2)).save(any(Workflow.class));
      verify(workflowConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("should create workflow run with correct properties")
    void shouldCreateRunAndCopyWorkflowConfigurationWithScope() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      int version = 3;

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.isEdited()).thenReturn(false);
      when(workflowTemplate.getVersion()).thenReturn(version);
      when(workflowTemplate.getSimulation()).thenReturn(simulation);

      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflow(workflowTemplate);

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
    @DisplayName("should copy and save workflow configuration for workflow run")
    void shouldCopyAndSaveWorkflowConfigurationForRun() {
      // Prepare
      Exercise simulation = mock(Exercise.class);

      Workflow workflowTemplate =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(3)
              .simulation(simulation)
              .isEdited(false)
              .build();

      WorkflowConfiguration templateConfiguration = new WorkflowConfiguration();
      templateConfiguration.setRateLimitEnabled(true);
      templateConfiguration.setMaxAttempts(5);
      templateConfiguration.setMaxTemporalRateSeconds(15L);
      templateConfiguration.setTimeoutEnabled(true);
      templateConfiguration.setTimeoutSeconds(120L);
      templateConfiguration.setSafeModeEnabled(false);
      templateConfiguration.setWorkflow(workflowTemplate);
      workflowTemplate.setWorkflowConfiguration(templateConfiguration);

      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(workflowConfigurationRepository.save(any(WorkflowConfiguration.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflow(workflowTemplate);

      // Assert
      ArgumentCaptor<WorkflowConfiguration> workflowConfigurationCaptor =
          ArgumentCaptor.forClass(WorkflowConfiguration.class);
      verify(workflowConfigurationRepository).save(workflowConfigurationCaptor.capture());
      WorkflowConfiguration savedConfiguration = workflowConfigurationCaptor.getValue();

      assertSame(result, savedConfiguration.getWorkflow());
      assertEquals(savedConfiguration, result.getWorkflowConfiguration());

      assertNotSame(templateConfiguration, savedConfiguration);

      assertFalse(savedConfiguration.isSafeModeEnabled());
      assertTrue(savedConfiguration.isRateLimitEnabled());
      assertEquals(5, savedConfiguration.getMaxAttempts());
      assertEquals(15L, savedConfiguration.getMaxTemporalRateSeconds());
      assertTrue(savedConfiguration.isTimeoutEnabled());
      assertEquals(120L, savedConfiguration.getTimeoutSeconds());
      assertSame(result, result);
    }

    @Test
    @DisplayName("should not save workflow configuration when template has none")
    void shouldNotSaveWorkflowConfigurationWhenTemplateHasNone() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Workflow template =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(1)
              .simulation(simulation)
              .isEdited(false)
              .build();

      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      workflowService.launchWorkflow(template);

      // Assert
      verify(workflowConfigurationRepository, never()).save(any(WorkflowConfiguration.class));
    }
  }

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

  @Nested
  @DisplayName("Should find WorkflowTemplate By SimulationId")
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

    @DisplayName("should return null when template not found")
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

  @Nested
  @DisplayName("deleteWorkflow")
  class DeleteWorkflowTests {

    @Test
    void shouldDeleteWorkflowById() {
      String workflowId = UUID.randomUUID().toString();

      workflowService.deleteWorkflow(workflowId);

      verify(workflowRepository).deleteById(workflowId);
      verifyNoMoreInteractions(workflowRepository);
    }
  }

  @Nested
  @DisplayName("fetchWorkflowConfiguration")
  class FetchWorkflowConfigurationTests {

    @Test
    @DisplayName("should return workflow configuration when found")
    void shouldReturnWorkflowConfigurationWhenFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      WorkflowConfiguration workflowConfiguration = mock(WorkflowConfiguration.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowConfiguration()).thenReturn(workflowConfiguration);

      // Act
      WorkflowConfiguration result = workflowService.getWorkflowConfiguration(workflowId);

      // Assert
      assertEquals(workflowConfiguration, result);
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow, atLeastOnce()).getWorkflowConfiguration();
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

    @Test
    @DisplayName("should throw ElementNotFoundException when workflow configuration is missing")
    void shouldThrowExceptionWhenWorkflowConfigurationIsMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowConfiguration()).thenReturn(null);

      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getWorkflowConfiguration(workflowId));
      assertEquals(
          "Workflow configuration not found for this workflow: " + workflowId,
          exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow).getWorkflowConfiguration();
    }
  }

  @Nested
  @DisplayName("updateWorkflowConfiguration")
  class UpdateWorkflowConfigurationTests {

    @Captor private ArgumentCaptor<WorkflowConfiguration> configurationArgumentCaptor;

    @Test
    @DisplayName("should update workflow configuration and save it")
    void shouldUpdateWorkflowConfigurationAndSaveIt() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      WorkflowConfiguration configuration = mock(WorkflowConfiguration.class);
      WorkflowConfigurationInput input = mock(WorkflowConfigurationInput.class);
      WorkflowConfiguration savedConfiguration = mock(WorkflowConfiguration.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowConfiguration()).thenReturn(configuration);
      doNothing().when(workflowConfigurationMapper).applyInput(input, configuration);
      when(workflowConfigurationRepository.save(configuration)).thenReturn(savedConfiguration);

      // Act
      WorkflowConfiguration result = workflowService.updateWorkflowConfiguration(workflowId, input);

      // Assert
      verify(workflowConfigurationMapper).applyInput(input, configuration);
      verify(workflowConfigurationRepository).save(configurationArgumentCaptor.capture());
      assertEquals(configuration, configurationArgumentCaptor.getValue());
      assertEquals(savedConfiguration, result);
    }

    @DisplayName("should throw ElementNotFoundException when workflow is missing")
    @Test
    void shouldThrowWhenWorkflowMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      WorkflowConfigurationInput input = mock(WorkflowConfigurationInput.class);
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
      verifyNoInteractions(workflowConfigurationMapper);
      verify(workflowConfigurationRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw ElementNotFoundException when workflow configuration is missing")
    void shouldThrowExceptionWhenConfigurationIsMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      WorkflowConfigurationInput input = mock(WorkflowConfigurationInput.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowConfiguration()).thenReturn(null);

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.updateWorkflowConfiguration(workflowId, input));
      assertEquals(
          "Workflow configuration not found for this workflow: " + workflowId,
          exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow).getWorkflowConfiguration();
      verifyNoInteractions(workflowConfigurationMapper);
      verify(workflowConfigurationRepository, never()).save(any());
    }

    @Test
    void shouldUpdateScopeRulesWithRealMapper() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      WorkflowConfiguration configuration = new WorkflowConfiguration();

      WorkflowScopeRuleInput ipRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.10.10.10")
              .build();
      WorkflowScopeRuleInput domainRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("example.org")
              .build();
      WorkflowScopeRuleInput assetRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-123")
              .build();
      WorkflowScopeRuleInput subnetRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.10.10.0/24")
              .build();
      WorkflowScopeRuleInput assetGroupRule =
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("asset-group-1")
              .build();

      WorkflowScopeInput scopeInput = new WorkflowScopeInput();
      scopeInput.setWorkflowScopeRules(
          List.of(ipRule, domainRule, assetRule, subnetRule, assetGroupRule));

      WorkflowConfigurationInput input = new WorkflowConfigurationInput();
      input.setSafeModeEnabled(true);
      input.setWorkflowScope(scopeInput);

      WorkflowConfigurationMapper realMapper = new WorkflowConfigurationMapper();
      WorkflowService serviceWithRealMapper =
          new WorkflowService(workflowRepository, workflowConfigurationRepository, realMapper);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getWorkflowConfiguration()).thenReturn(configuration);
      when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
      when(workflowConfigurationRepository.save(configuration)).thenReturn(configuration);

      WorkflowConfiguration result =
          serviceWithRealMapper.updateWorkflowConfiguration(workflowId, input);

      assertSame(configuration, result);
      assertNotNull(configuration.getWorkflowScope());
      assertEquals(3, configuration.getWorkflowScope().getWhitelist().size());
      assertEquals(2, configuration.getWorkflowScope().getBlacklist().size());

      WorkflowScopeRule mappedIpRule =
          configuration.getWorkflowScope().getWhitelist().stream()
              .filter(rule -> "10.10.10.10".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP, mappedIpRule.getValueType());
      assertSame(configuration.getWorkflowScope(), mappedIpRule.getWorkflowScope());

      WorkflowScopeRule mappedDomainRule =
          configuration.getWorkflowScope().getWhitelist().stream()
              .filter(rule -> "example.org".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.DOMAIN, mappedDomainRule.getValueType());
      assertSame(configuration.getWorkflowScope(), mappedDomainRule.getWorkflowScope());

      WorkflowScopeRule mappedAssetRule =
          configuration.getWorkflowScope().getWhitelist().stream()
              .filter(rule -> "asset-123".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_ID, mappedAssetRule.getValueType());
      assertSame(configuration.getWorkflowScope(), mappedAssetRule.getWorkflowScope());

      WorkflowScopeRule mappedSubnetRule =
          configuration.getWorkflowScope().getBlacklist().stream()
              .filter(rule -> "10.10.10.0/24".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP_SUBNET, mappedSubnetRule.getValueType());
      assertSame(configuration.getWorkflowScope(), mappedSubnetRule.getWorkflowScope());

      WorkflowScopeRule mappedAssetGroupRule =
          configuration.getWorkflowScope().getBlacklist().stream()
              .filter(rule -> "asset-group-1".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_GROUP_ID, mappedAssetGroupRule.getValueType());
      assertSame(configuration.getWorkflowScope(), mappedAssetGroupRule.getWorkflowScope());

      verify(workflow).setEdited(true);
      verify(workflowRepository).save(workflow);
      verify(workflowConfigurationRepository).save(configuration);
    }
  }
}
