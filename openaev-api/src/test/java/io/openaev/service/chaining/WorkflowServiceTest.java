package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
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

    @Test
    @DisplayName("should create workflow template for exercise")
    void shouldCreateWorkflowTemplate() {
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
      when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));

      // Act
      workflowService.updateWorkflowTemplate(workflowId);

      // Assert
      verify(workflowRepository).findById(workflowId);
      verify(workflow).setEdited(true);
      verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("should throw exception when workflow not found")
    void shouldThrowExceptionWhenNotFound() {
      // Prepare
      String workflowId = UUID.randomUUID().toString();
      when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(
          NoSuchElementException.class, () -> workflowService.updateWorkflowTemplate(workflowId));
      verify(workflowRepository).findById(workflowId);
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
}
