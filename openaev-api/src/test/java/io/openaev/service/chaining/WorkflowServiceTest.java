package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ChainingConfigurationInput;
import io.openaev.api.chaining.ChainingConfigurationMapper;
import io.openaev.database.model.ChainingConfiguration;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ChainingConfigurationRepository;
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
  @Mock private ChainingConfigurationRepository chainingConfigurationRepository;
  @Mock private ChainingConfigurationMapper chainingConfigurationMapper;

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
    @Captor private ArgumentCaptor<ChainingConfiguration> configurationCaptor;

    @Test
    void shouldCreateTemplateAndDefaultConfiguration() {
      Exercise exercise = mock(Exercise.class);

      workflowService.creationWorkflow(exercise);

      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow savedWorkflow = workflowCaptor.getValue();
      assertEquals(0, savedWorkflow.getVersion());
      assertEquals(WorkflowStatus.TEMPLATE, savedWorkflow.getStatus());
      assertEquals(exercise, savedWorkflow.getSimulation());

      verify(chainingConfigurationRepository).save(chainingConfigurationCaptor.capture());
      ChainingConfiguration savedConfiguration = chainingConfigurationCaptor.getValue();
      assertFalse(savedConfiguration.isRateLimitEnabled());
      assertFalse(savedConfiguration.isTimeoutEnabled());
      assertTrue(savedConfiguration.isSafeModeEnabled());
      assertEquals(savedWorkflow, savedConfiguration.getWorkflow());
      assertEquals(savedConfiguration, savedWorkflow.getChainingConfiguration());
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
    @Captor private ArgumentCaptor<ChainingConfiguration> chainingConfigurationCaptor;

    @Test
    void shouldIncrementVersionWhenTemplateEdited() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Workflow template = mock(Workflow.class);
      when(template.isEdited()).thenReturn(true);
      when(template.getVersion()).thenReturn(1);
      when(template.getSimulation()).thenReturn(simulation);
      when(template.getChainingConfiguration()).thenReturn(null);
      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      workflowService.launchWorkflow(template);

      // Assert
      verify(template).setEdited(false);
      verify(template).setVersion(2);
      verify(workflowRepository, times(2)).save(any(Workflow.class));
      verify(chainingConfigurationRepository, never()).save(any());
    }

    @Test
    void shouldCreateRunAndCopyChainingConfigurationWithScope() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Scope scope = new Scope();

      Workflow template =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(3)
              .simulation(simulation)
              .isEdited(false)
              .build();

      ChainingRateLimit templateRateLimit = new ChainingRateLimit();
      templateRateLimit.setEnableRateLimit(true);
      templateRateLimit.setMaxAttempts(5);
      templateRateLimit.setMaxTemporalRateMinutes(15);

      ChainingTimeOut templateTimeOut = new ChainingTimeOut();
      templateTimeOut.setEnableTimeOut(true);
      templateTimeOut.setTimeOutSeconds(120);

      ChainingConfiguration templateConfiguration = new ChainingConfiguration();
      templateConfiguration.setRateLimitEnabled(true);
      templateConfiguration.setMaxAttempts(5);
      templateConfiguration.setMaxTemporalRateSeconds(15L);
      templateConfiguration.setTimeoutEnabled(true);
      templateConfiguration.setTimeoutSeconds(120L);
      templateConfiguration.setSafeModeEnabled(false);
      templateConfiguration.setWorkflow(workflowTemplate);
      workflowTemplate.setChainingConfiguration(templateConfiguration);

      when(workflowRepository.save(any(Workflow.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(chainingConfigurationRepository.save(any(ChainingConfiguration.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      Workflow result = workflowService.launchWorkflow(template);

      // Assert
      verify(workflowRepository).save(workflowCaptor.capture());
      Workflow run = workflowCaptor.getValue();
      assertEquals(WorkflowStatus.RUN, run.getStatus());
      assertSame(template, run.getWorkflowTemplate());
      assertEquals(3, run.getVersion());
      assertSame(simulation, run.getSimulation());
      assertFalse(run.isEdited());

      verify(chainingConfigurationRepository).save(chainingConfigurationCaptor.capture());
      ChainingConfiguration runConfiguration = chainingConfigurationCaptor.getValue();
      assertSame(run, runConfiguration.getWorkflow());
      assertSame(runConfiguration, run.getChainingConfiguration());

      assertNotSame(templateConfiguration, runConfiguration);
      assertNotSame(templateRateLimit, runConfiguration.getRateLimit());
      assertNotSame(templateTimeOut, runConfiguration.getTimeOut());
      assertSame(scope, runConfiguration.getScope());
      assertFalse(runConfiguration.isSafeMode());
      assertTrue(runConfiguration.getRateLimit().isEnableRateLimit());
      assertEquals(5, runConfiguration.getRateLimit().getMaxAttempts());
      assertEquals(15, runConfiguration.getRateLimit().getMaxTemporalRateMinutes());
      assertTrue(runConfiguration.getTimeOut().isEnableTimeOut());
      assertEquals(120, runConfiguration.getTimeOut().getTimeOutSeconds());

      assertNotSame(templateConfiguration, savedConfiguration);

      assertFalse(savedConfiguration.isSafeModeEnabled());
      assertTrue(savedConfiguration.isRateLimitEnabled());
      assertEquals(5, savedConfiguration.getMaxAttempts());
      assertEquals(15L, savedConfiguration.getMaxTemporalRateSeconds());
      assertTrue(savedConfiguration.isTimeoutEnabled());
      assertEquals(120L, savedConfiguration.getTimeoutSeconds());
      assertSame(run, result);
    }

    @Test
    void shouldNotSaveConfigurationWhenTemplateHasNone() {
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
      verify(chainingConfigurationRepository, never()).save(any(ChainingConfiguration.class));
    }
  }

  @Nested
  @DisplayName("isSimulationChaining")
  class IsSimulationChainingTests {

    static Stream<Arguments> isSimulationChainingCases() {
      return Stream.of(
          Arguments.of("single", List.of(mock(Workflow.class)), true),
          Arguments.of("multiple", List.of(mock(Workflow.class), mock(Workflow.class)), true),
          Arguments.of("none", Collections.emptyList(), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("isSimulationChainingCases")
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
  @DisplayName("findWorkflowTemplateBySimulationId")
  class FindWorkflowTemplateBySimulationIdTests {

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
  @DisplayName("fetchChainingConfiguration")
  class FetchChainingConfigurationTests {

    @Test
    void shouldReturnConfigurationWhenPresent() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      ChainingConfiguration configuration = mock(ChainingConfiguration.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(configuration);

      // Act
      ChainingConfiguration result = workflowService.getChainingConfiguration(workflowId);

      assertSame(configuration, result);
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow, atLeastOnce()).getChainingConfiguration();
    }

    @Test
    void shouldThrowWhenWorkflowMissing() {
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getChainingConfiguration(workflowId));
      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }

    @Test
    void shouldThrowWhenConfigurationMissing() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(null);

      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getChainingConfiguration(workflowId));
      assertEquals(
          "Chaining configuration not found for this workflow: " + workflowId,
          exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow).getChainingConfiguration();
    }
  }

  @Nested
  @DisplayName("updateChainingConfiguration")
  class UpdateChainingConfigurationTests {

    @Captor private ArgumentCaptor<ChainingConfiguration> configurationCaptor;

    @Test
    void shouldUpdateConfigurationIncludingScopeAndSave() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      ChainingConfiguration configuration = mock(ChainingConfiguration.class);
      ChainingConfigurationInput input = mock(ChainingConfigurationInput.class);
      ChainingConfiguration savedConfiguration = mock(ChainingConfiguration.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(configuration);
      doNothing().when(chainingConfigurationMapper).applyInput(input, configuration);
      when(chainingConfigurationRepository.save(configuration)).thenReturn(savedConfiguration);

      // Act
      ChainingConfiguration result = workflowService.updateChainingConfiguration(workflowId, input);

      // Assert
      verify(chainingConfigurationMapper).applyInput(input, configuration);
      verify(chainingConfigurationRepository).save(chainingConfigurationCaptor.capture());
      assertEquals(configuration, chainingConfigurationCaptor.getValue());
      assertEquals(savedConfiguration, result);
    }

    @Test
    void shouldThrowWhenWorkflowMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      ChainingConfigurationInput input = mock(ChainingConfigurationInput.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.updateChainingConfiguration(workflowId, input));
      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verifyNoInteractions(chainingConfigurationMapper);
      verify(chainingConfigurationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenConfigurationMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      ChainingConfigurationInput input = mock(ChainingConfigurationInput.class);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(null);

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.updateChainingConfiguration(workflowId, input));
      assertEquals(
          "Chaining configuration not found for this workflow: " + workflowId,
          exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow).getChainingConfiguration();
      verifyNoInteractions(chainingConfigurationMapper);
      verify(chainingConfigurationRepository, never()).save(any());
    }

    @Test
    void shouldUpdateScopeRulesWithRealMapper() {
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      ChainingConfiguration configuration = new ChainingConfiguration();

      ChainingScopeRuleInput ipRule =
          ChainingScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.10.10.10")
              .build();
      ChainingScopeRuleInput domainRule =
          ChainingScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("example.org")
              .build();
      ChainingScopeRuleInput assetRule =
          ChainingScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.WHITELIST)
              .ruleSource(ScopeRuleSource.ASSET)
              .ruleValue("asset-123")
              .build();
      ChainingScopeRuleInput subnetRule =
          ChainingScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
              .ruleSource(ScopeRuleSource.MANUAL)
              .ruleValue("10.10.10.0/24")
              .build();
      ChainingScopeRuleInput assetGroupRule =
          ChainingScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.BLACKLIST)
              .ruleSource(ScopeRuleSource.ASSET_GROUP)
              .ruleValue("asset-group-1")
              .build();

      ChainingScopeInput scopeInput = new ChainingScopeInput();
      scopeInput.setScopeRules(List.of(ipRule, domainRule, assetRule, subnetRule, assetGroupRule));

      ChainingConfigurationInput input = new ChainingConfigurationInput();
      input.setSafeMode(true);
      input.setScope(scopeInput);

      ChainingConfigurationMapper realMapper = new ChainingConfigurationMapper();
      WorkflowService serviceWithRealMapper =
          new WorkflowService(workflowRepository, chainingConfigurationRepository, realMapper);

      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(configuration);
      when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
      when(chainingConfigurationRepository.save(configuration)).thenReturn(configuration);

      ChainingConfiguration result =
          serviceWithRealMapper.updateChainingConfiguration(workflowId, input);

      assertSame(configuration, result);
      assertNotNull(configuration.getScope());
      assertEquals(3, configuration.getScope().getWhitelist().size());
      assertEquals(2, configuration.getScope().getBlacklist().size());

      ScopeRule mappedIpRule =
          configuration.getScope().getWhitelist().stream()
              .filter(rule -> "10.10.10.10".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP, mappedIpRule.getValueType());
      assertSame(configuration.getScope(), mappedIpRule.getScope());

      ScopeRule mappedDomainRule =
          configuration.getScope().getWhitelist().stream()
              .filter(rule -> "example.org".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.DOMAIN, mappedDomainRule.getValueType());
      assertSame(configuration.getScope(), mappedDomainRule.getScope());

      ScopeRule mappedAssetRule =
          configuration.getScope().getWhitelist().stream()
              .filter(rule -> "asset-123".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_ID, mappedAssetRule.getValueType());
      assertSame(configuration.getScope(), mappedAssetRule.getScope());

      ScopeRule mappedSubnetRule =
          configuration.getScope().getBlacklist().stream()
              .filter(rule -> "10.10.10.0/24".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.IP_SUBNET, mappedSubnetRule.getValueType());
      assertSame(configuration.getScope(), mappedSubnetRule.getScope());

      ScopeRule mappedAssetGroupRule =
          configuration.getScope().getBlacklist().stream()
              .filter(rule -> "asset-group-1".equals(rule.getRuleValue()))
              .findFirst()
              .orElseThrow();
      assertEquals(ScopeRuleValueType.ASSET_GROUP_ID, mappedAssetGroupRule.getValueType());
      assertSame(configuration.getScope(), mappedAssetGroupRule.getScope());

      verify(workflow).setEdited(true);
      verify(workflowRepository).save(workflow);
      verify(chainingConfigurationRepository).save(configuration);
    }
  }
}
