package io.openaev.service.chaining;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WorkflowService {
  private final WorkflowRepository workflowRepository;

  // -- READ --

  /**
   * Retrieves a workflow by its ID.
   *
   * @param workflowId the ID of the workflow to retrieve
   * @return the found workflow
   * @throws ElementNotFoundException if no workflow is found with the given ID
   */
  public Workflow getWorkflowById(@NotBlank final String workflowId) {
    return this.workflowRepository
        .findById(workflowId)
        .orElseThrow(() -> new ElementNotFoundException("Workflow not found"));
  }

  /**
   * Creates a new workflow template for an exercise.
   *
   * @param exercise the exercise to create the workflow for
   */
  public void creationWorkflow(Exercise exercise) {
    Workflow workflow =
        Workflow.builder().version(0).status(WorkflowStatus.TEMPLATE).simulation(exercise).build();
    workflowRepository.save(workflow);
  }

  /**
   * Marks a workflow template as edited.
   *
   * @param workflowId the ID of the workflow to update
   */
  public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(); // todo
    workflow.setEdited(true);
    workflowRepository.save(workflow);
  }

  /**
   * Saves a workflow run to the repository.
   *
   * @param workflowRun the workflow run to save
   * @return the saved workflow run
   */
  public Workflow saveWorkflowRun(Workflow workflowRun) {
    return workflowRepository.save(workflowRun);
  }

  /**
   * Launches a workflow for a simulation by creating a run from the template.
   *
   * <p>If the template has been edited, its version is incremented before creating the run. A new
   * workflow run is created as a copy of the template with RUN status.
   *
   * @param simulationId the ID of the simulation to launch the workflow for
   * @return the created workflow run, or null if no template exists
   */
  public Workflow launchWorkflow(String simulationId) {
    // 1 WORKFLOW TEMPLATE / SIMULATION
    Workflow workflowTemplate =
        workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE);
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
            .status(WorkflowStatus.RUN)
            .simulation(workflowTemplate.getSimulation())
            .version(workflowTemplate.getVersion())
            .workflowTemplate(workflowTemplate)
            .build();

    return saveWorkflowRun(run);
  }

  /**
   * Checks if an exercise has workflow chaining enabled.
   *
   * @param exerciseId the ID of the exercise to check
   * @return true if the exercise has at least one workflow, false otherwise
   */
  public boolean isExerciseChaining(String exerciseId) {
    List<Workflow> workflows = this.workflowRepository.findAllBySimulation_Id(exerciseId);
    return !workflows.isEmpty();
  }

  /**
   * Finds the workflow template for an exercise.
   *
   * @param exerciseId the ID of the exercise
   * @return the workflow template, or null if not found
   */
  public Workflow findWorkflowTemplateByIdExercise(String exerciseId) {
    return this.workflowRepository.findBySimulation_IdAndStatus(
        exerciseId, WorkflowStatus.TEMPLATE);
  }

  /**
   * Deletes a workflow by its ID.
   *
   * @param workflowId the ID of the workflow to delete
   */
  public void deleteWorkflow(String workflowId) {
    workflowRepository.deleteById(workflowId);
  }
}
