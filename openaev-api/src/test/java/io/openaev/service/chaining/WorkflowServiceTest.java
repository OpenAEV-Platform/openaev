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
import java.util.*;
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

  // ========================================================================
  // getWorkflowById Tests
  // ========================================================================
  @Nested
  @DisplayName("getWorkflowById")
  class GetWorkflowByIdTests {

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
    @Captor private ArgumentCaptor<ChainingConfiguration> chainingConfigurationCaptor;

    @Test
    @DisplayName("should create workflow template and default chaining configuration for exercise")
    void shouldCreateWorkflowTemplateAndDefaultChainingConfiguration() {
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

      verify(chainingConfigurationRepository).save(chainingConfigurationCaptor.capture());
      ChainingConfiguration savedConfiguration = chainingConfigurationCaptor.getValue();
      assertFalse(savedConfiguration.isRateLimitEnabled());
      assertFalse(savedConfiguration.isTimeoutEnabled());
      assertTrue(savedConfiguration.isSafeModeEnabled());
      assertEquals(savedWorkflow, savedConfiguration.getWorkflow());
      assertEquals(savedConfiguration, savedWorkflow.getChainingConfiguration());
    }
  }

  // ========================================================================
  // updateWorkflowTemplate Tests
  // ========================================================================
  @Nested
  @DisplayName("updateWorkflowTemplate")
  class UpdateWorkflowTemplateTests {

    @Test
    @DisplayName("should mark workflow as edited")
    void shouldMarkWorkflowAsEdited() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));

      // Act
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

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class, () -> workflowService.updateWorkflowTemplate(workflowId));
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflowRepository, never()).save(any());
    }
  }

  // ========================================================================
  // saveWorkflowRun Tests
  // ========================================================================
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
    void shouldIncrementVersionWhenEdited() {
      // Prepare
      Exercise simulation = mock(Exercise.class);

      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowTemplate.isEdited()).thenReturn(true);
      when(workflowTemplate.getVersion()).thenReturn(1);
      when(workflowTemplate.getSimulation()).thenReturn(simulation);

      when(workflowRepository.save(any(Workflow.class))).thenReturn(workflowTemplate);

      // Act
      workflowService.launchWorkflow(workflowTemplate);

      // Assert
      verify(workflowTemplate).setEdited(false);
      verify(workflowTemplate).setVersion(2);
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
      workflowService.launchWorkflow(workflowTemplate);

      // Assert
      verify(workflowTemplate, never()).setEdited(anyBoolean());
      verify(workflowTemplate, never()).setVersion(anyInt());
      verify(workflowRepository, times(1)).save(any(Workflow.class));
    }

    @Test
    @DisplayName("should create workflow run with correct properties")
    void shouldCreateWorkflowRunWithCorrectProperties() {
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
    @DisplayName("should copy and save chaining configuration for workflow run")
    void shouldCopyAndSaveChainingConfigurationForRun() {
      // Prepare
      Exercise simulation = mock(Exercise.class);

      Workflow workflowTemplate =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(3)
              .simulation(simulation)
              .build();
      ChainingConfiguration templateConfiguration = new ChainingConfiguration();
      templateConfiguration.setRateLimitEnabled(true);
      templateConfiguration.setMaxAttempts(5);
      templateConfiguration.setMaxTemporalRateSeconds(15L);
      templateConfiguration.setTimeoutEnabled(true);
      templateConfiguration.setTimeoutSeconds(120L);
      templateConfiguration.setSafeModeEnabled(false);
      templateConfiguration.setWorkflow(workflowTemplate);
      workflowTemplate.setChainingConfiguration(templateConfiguration);

      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));
      when(chainingConfigurationRepository.save(any(ChainingConfiguration.class)))
          .thenAnswer(i -> i.getArgument(0));

      // Act
      Workflow run = workflowService.launchWorkflow(workflowTemplate);

      // Assert
      ArgumentCaptor<ChainingConfiguration> chainingConfigurationCaptor =
          ArgumentCaptor.forClass(ChainingConfiguration.class);
      verify(chainingConfigurationRepository).save(chainingConfigurationCaptor.capture());
      ChainingConfiguration savedConfiguration = chainingConfigurationCaptor.getValue();

      assertSame(run, savedConfiguration.getWorkflow());
      assertEquals(savedConfiguration, run.getChainingConfiguration());

      assertNotSame(templateConfiguration, savedConfiguration);

      assertFalse(savedConfiguration.isSafeModeEnabled());
      assertTrue(savedConfiguration.isRateLimitEnabled());
      assertEquals(5, savedConfiguration.getMaxAttempts());
      assertEquals(15L, savedConfiguration.getMaxTemporalRateSeconds());
      assertTrue(savedConfiguration.isTimeoutEnabled());
      assertEquals(120L, savedConfiguration.getTimeoutSeconds());
    }

    @Test
    @DisplayName("should not save chaining configuration when template has none")
    void shouldNotSaveChainingConfigurationWhenTemplateHasNone() {
      // Prepare
      Exercise simulation = mock(Exercise.class);
      Workflow workflowTemplate =
          Workflow.builder()
              .status(WorkflowStatus.TEMPLATE)
              .version(1)
              .simulation(simulation)
              .build();
      when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

      // Act
      workflowService.launchWorkflow(workflowTemplate);

      // Assert
      verify(chainingConfigurationRepository, never()).save(any(ChainingConfiguration.class));
    }
  }

  // ========================================================================
  // isSimulationChaining Tests
  // ========================================================================
  @Nested
  @DisplayName("isSimulationChaining")
  class IsSimulationChainingTests {

    @Captor private ArgumentCaptor<String> simulationIdCaptor;

    private static Stream<Arguments> testCases() {
      return Stream.of(
          Arguments.of("single workflow", List.of(mock(Workflow.class)), true),
          Arguments.of(
              "multiple workflows", List.of(mock(Workflow.class), mock(Workflow.class)), true),
          Arguments.of("no workflows", Collections.emptyList(), false));
    }

    @ParameterizedTest(name = "should return {2} when {0}")
    @MethodSource("testCases")
    void shouldReturnCorrectResult(String name, List<Workflow> workflows, boolean expected) {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      when(workflowRepository.findAllBySimulation_Id(simulationId)).thenReturn(workflows);

      // Act
      boolean result = workflowService.isSimulationChaining(simulationId);

      // Assert
      verify(workflowRepository).findAllBySimulation_Id(simulationIdCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());
      assertEquals(expected, result);
    }
  }

  // ========================================================================
  // findWorkflowTemplateBySimulationId Tests
  // ========================================================================
  @Nested
  @DisplayName("findWorkflowTemplateBySimulationId")
  class FindWorkflowTemplateBySimulationIdTests {

    @Captor private ArgumentCaptor<String> simulationIdCaptor;

    @Captor private ArgumentCaptor<WorkflowStatus> statusCaptor;

    @Test
    @DisplayName("should return workflow template when found")
    void shouldReturnTemplateWhenFound() {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      Workflow workflowTemplate = mock(Workflow.class);
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(workflowTemplate);

      // Act
      Workflow result =
          workflowService.findWorkflowTemplateBySimulationId(simulationId).orElse(null);

      // Assert
      verify(workflowRepository)
          .findBySimulation_IdAndStatus(simulationIdCaptor.capture(), statusCaptor.capture());
      assertEquals(simulationId, simulationIdCaptor.getValue());
      assertEquals(WorkflowStatus.TEMPLATE, statusCaptor.getValue());
      assertEquals(workflowTemplate, result);
    }

    @Test
    @DisplayName("should return null when template not found")
    void shouldReturnNullWhenNotFound() {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      when(workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE))
          .thenReturn(null);

      // Act
      Workflow result =
          workflowService.findWorkflowTemplateBySimulationId(simulationId).orElse(null);

      // Assert
      assertNull(result);
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

    @Captor private ArgumentCaptor<String> workflowIdCaptor;

    @Test
    @DisplayName("should delete workflow by id")
    void shouldDeleteWorkflowById() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();

      // Act
      workflowService.deleteWorkflow(workflowId);

      // Assert
      verify(workflowRepository).deleteById(workflowIdCaptor.capture());
      assertEquals(workflowId, workflowIdCaptor.getValue());
      verifyNoMoreInteractions(workflowRepository);
    }
  }

  // ========================================================================
  // fetchChainingConfiguration Tests
  // ========================================================================
  @Nested
  @DisplayName("fetchChainingConfiguration")
  class FetchChainingConfigurationTests {

    @Test
    @DisplayName("should return chaining configuration when found")
    void shouldReturnChainingConfigurationWhenFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      ChainingConfiguration chainingConfiguration = mock(ChainingConfiguration.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(chainingConfiguration);

      // Act
      ChainingConfiguration result = workflowService.getChainingConfiguration(workflowId);

      // Assert
      assertEquals(chainingConfiguration, result);
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
      verify(workflow, atLeastOnce()).getChainingConfiguration();
    }

    @Test
    @DisplayName("should throw ElementNotFoundException when workflow not found")
    void shouldThrowExceptionWhenWorkflowNotFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.empty());

      // Act & Assert
      ElementNotFoundException exception =
          assertThrows(
              ElementNotFoundException.class,
              () -> workflowService.getChainingConfiguration(workflowId));
      assertEquals(
          "Workflow TEMPLATE not found. Workflow ID : " + workflowId, exception.getMessage());
      verify(workflowRepository).findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    }

    @Test
    @DisplayName("should throw ElementNotFoundException when chaining configuration is missing")
    void shouldThrowExceptionWhenChainingConfigurationIsMissing() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      Workflow workflow = mock(Workflow.class);
      when(workflowRepository.findByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE))
          .thenReturn(Optional.of(workflow));
      when(workflow.getChainingConfiguration()).thenReturn(null);

      // Act & Assert
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

  // ========================================================================
  // updateChainingConfiguration Tests
  // ========================================================================
  @Nested
  @DisplayName("updateChainingConfiguration")
  class UpdateChainingConfigurationTests {

    @Captor private ArgumentCaptor<ChainingConfiguration> chainingConfigurationCaptor;

    @Test
    @DisplayName("should update chaining configuration and save it")
    void shouldUpdateChainingConfigurationAndSaveIt() {
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
    @DisplayName("should throw ElementNotFoundException when workflow is missing")
    void shouldThrowExceptionWhenWorkflowIsMissing() {
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
    @DisplayName("should throw ElementNotFoundException when chaining configuration is missing")
    void shouldThrowExceptionWhenConfigurationIsMissing() {
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
  }
}
