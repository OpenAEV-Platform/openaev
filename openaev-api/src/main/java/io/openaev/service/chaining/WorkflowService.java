package io.openaev.service.chaining;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.WORKFLOW_STATUS;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class WorkflowService {
  private final WorkflowRepository workflowRepository;

  // -- READ --
  public Workflow getWorkflowById(@NotBlank final String workflowId) {
    return this.workflowRepository
        .findById(workflowId)
        .orElseThrow(() -> new ElementNotFoundException("Workflow not found"));
  }

  public void creationWorkflow(Exercise exercise) {
    Workflow workflow =
        Workflow.builder().version(0).status(WORKFLOW_STATUS.TEMPLATE).simulation(exercise).build();
    workflowRepository.save(workflow);
  }

  public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(); // todo
    workflow.setEdited(true);
    workflowRepository.save(workflow);
  }

  public Workflow saveWorkflowRun(Workflow workflowRun) {
    return workflowRepository.save(workflowRun);
  }

  public Workflow launchWorkflow(String simulationId) {
    // 1 WORKFLOW TEMPLATE / SIMULATION
    Workflow workflowTemplate =
        workflowRepository.findBySimulation_IdAndStatus(simulationId, WORKFLOW_STATUS.TEMPLATE);
    if (workflowTemplate == null) return null;

    if (workflowTemplate.isEdited()) {
      workflowTemplate.setEdited(false);
      int version = workflowTemplate.getVersion();
      workflowTemplate.setVersion(++version);
      workflowTemplate = workflowRepository.save(workflowTemplate);
    }

    // COPY WORKFLOW Template to Workflow execution
    Workflow run =
        Workflow.builder()
            .isEdited(false)
            .status(WORKFLOW_STATUS.RUN)
            .simulation(workflowTemplate.getSimulation())
            .version(workflowTemplate.getVersion())
            .workflowTemplate(workflowTemplate)
            .build();

    return saveWorkflowRun(run);
  }

  public boolean isExerciseChaining(String exerciseId) {
    List<Workflow> workflows = this.workflowRepository.findAllBySimulation_Id(exerciseId);
    if (workflows.isEmpty()) return false;
    return true;
  }

  public Workflow findWorkflowTemplateByIdExercise(String exerciseId) {
    return this.workflowRepository.findBySimulation_IdAndStatus(
        exerciseId, WORKFLOW_STATUS.TEMPLATE);
  }

  public void deleteWorkflow(String workflowId) {
    workflowRepository.deleteById(workflowId);
  }
}
