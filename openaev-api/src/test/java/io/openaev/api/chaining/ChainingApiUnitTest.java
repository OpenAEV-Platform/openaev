package io.openaev.api.chaining;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChainingApi unit tests")
class ChainingApiUnitTest {

  @Mock private ExerciseService exerciseService;
  @Mock private CustomDashboardService customDashboardService;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private ScenarioService scenarioService;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private WorkflowService workflowService;
  @Mock private StepService stepService;
  @Mock private TagRepository tagRepository;

  @InjectMocks private ChainingApi chainingApi;

  @Nested
  @DisplayName("duplicateSimulationChaining")
  class DuplicateExercise {

    @Test
    void shouldDuplicateSimulationAndCopyStepTemplate() throws ChainingException {
      String simulationId = "simulation-id";
      Exercise simulation = new Exercise();
      simulation.setId("simulation-dup-id");
      Workflow sourceWorkflow = new Workflow();
      Workflow duplicatedWorkflow = new Workflow();

      doReturn(true).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
      when(exerciseService.getDuplicateExercise(simulationId)).thenReturn(simulation);
      when(workflowService.findWorkflowTemplateBySimulationId(simulation.getId()))
          .thenReturn(Optional.of(sourceWorkflow));
      when(workflowService.duplicateSimulation(simulationId, simulation)).thenReturn(duplicatedWorkflow);

      Exercise result = chainingApi.duplicateExercise(simulationId);

      assertSame(simulation, result);
      verify(stepService).copyStepTemplate(sourceWorkflow, duplicatedWorkflow);
    }

    @Test
    void shouldThrowWhenFeatureDisabled() {
      doReturn(false).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);

      assertThrows(ChainingException.class, () -> chainingApi.duplicateExercise("simulation-id"));

      verifyNoInteractions(exerciseService, workflowService, stepService);
    }

    @Test
    void shouldThrowWhenWorkflowTemplateNotFound() throws ChainingException {
      String simulationId = "simulation-id";
      Exercise simulation = new Exercise();
      simulation.setId("simulation-dup-id");
      doReturn(true).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
      when(exerciseService.getDuplicateExercise(simulationId)).thenReturn(simulation);
      when(workflowService.findWorkflowTemplateBySimulationId(simulation.getId()))
          .thenReturn(Optional.empty());

      assertThrows(ChainingException.class, () -> chainingApi.duplicateExercise(simulationId));

      verify(workflowService, never()).duplicateSimulation(anyString(), any(Exercise.class));
      verify(stepService, never()).copyStepTemplate(any(Workflow.class), any(Workflow.class));
    }
  }

  @Nested
  @DisplayName("duplicateScenarioChaining")
  class DuplicateScenario {

    @Test
    void shouldDuplicateScenarioAndCopyStepTemplate() throws ChainingException {
      String scenarioId = "scenario-id";
      Scenario scenario = new Scenario();
      Workflow sourceWorkflow = new Workflow();
      Workflow duplicatedWorkflow = new Workflow();

      doReturn(true).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
      when(scenarioService.getDuplicateScenario(scenarioId)).thenReturn(scenario);
      when(workflowService.findWorkflowTemplateByScenarioId(scenarioId))
          .thenReturn(Optional.of(sourceWorkflow));
      when(workflowService.duplicateScenario(scenarioId, scenario)).thenReturn(duplicatedWorkflow);

      Scenario result = chainingApi.duplicateScenarioChaining(scenarioId);

      assertSame(scenario, result);
      verify(stepService).copyStepTemplate(sourceWorkflow, duplicatedWorkflow);
    }

    @Test
    void shouldThrowWhenFeatureDisabled() {
      doReturn(false).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);

      assertThrows(
          ChainingException.class, () -> chainingApi.duplicateScenarioChaining("scenario-id"));

      verifyNoInteractions(scenarioService, workflowService, stepService);
    }

    @Test
    void shouldThrowWhenWorkflowTemplateNotFound() throws ChainingException {
      String scenarioId = "scenario-id";
      Scenario scenario = new Scenario();
      doReturn(true).when(previewFeatureService).isFeatureEnabled(PreviewFeature.INJECT_CHAINING);
      when(scenarioService.getDuplicateScenario(scenarioId)).thenReturn(scenario);
      when(workflowService.findWorkflowTemplateByScenarioId(scenarioId)).thenReturn(Optional.empty());

      assertThrows(ChainingException.class, () -> chainingApi.duplicateScenarioChaining(scenarioId));

      verify(workflowService, never()).duplicateScenario(anyString(), any(Scenario.class));
      verify(stepService, never()).copyStepTemplate(any(Workflow.class), any(Workflow.class));
    }
  }
}

