package io.openaev.service.chaining;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WorkflowService {
  private final WorkflowRepository workflowRepository;
  private final ExerciseService exerciseService;

  // -- READ --
  public Workflow getWorkflowById(@NotBlank final String workflowId) {
    return this.workflowRepository
        .findById(workflowId)
        .orElseThrow(() -> new ElementNotFoundException("Workflow not found"));
  }

  public void creationWorkflow(String exerciseId) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    Workflow workflow =
        Workflow.builder()
            .version(0)
            .status(WORKFLOW_STATUS.TEMPLATE)
            .simulationId(exercise)
            .build();
    workflowRepository.save(workflow);
  }

  public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(); // todo
    workflow.setEdited(true);
    workflowRepository.save(workflow);
  }

  public Workflow launchWorkflowTemplateBySimulation(String simulationId) {
    // 1 WORKFLOW TEMPLATE / SIMULATION
    Workflow workflowTemplate =
        workflowRepository.findBySimulationId_IdAndStatus(simulationId, WORKFLOW_STATUS.TEMPLATE);
    if (workflowTemplate == null) return null; // todo exception not find
    return this.launchWorkflow(workflowTemplate);
  }

  private Workflow launchWorkflow(Workflow workflowTemplate) {
    if (workflowTemplate.isEdited()) {
      workflowTemplate.setEdited(false);
      int version = workflowTemplate.getVersion();
      workflowTemplate.setVersion(++version);
      workflowTemplate = workflowRepository.save(workflowTemplate);
    }

    // COPY WORKFLOW Template to Workflow execution
    return Workflow.builder()
        .isEdited(false)
        .status(WORKFLOW_STATUS.RUN)
        .simulationId(workflowTemplate.getSimulationId())
        .version(workflowTemplate.getVersion())
        .build();
  }

  public void deleteWorkflow(String workflowId) {
    workflowRepository.deleteById(workflowId);
  }
}
