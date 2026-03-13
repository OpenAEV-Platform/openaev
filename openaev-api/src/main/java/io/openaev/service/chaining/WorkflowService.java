package io.openaev.service.chaining;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
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
  public Workflow getWorkflowByIdAndStatus(
      @NotBlank final String workflowId, WorkflowStatus status) {
    return this.workflowRepository
        .findByIdAndStatus(workflowId, status)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Workflow "
                        + (status != null ? status.name() : null)
                        + " not found. Workflow ID : "
                        + workflowId));
  }

  /**
   * Creates a new workflow template for a simulation.
   *
   * @param simulation the simulation to create the workflow for
   */
  public void creationWorkflow(Exercise simulation) {
    Workflow workflow =
        Workflow.builder()
            .version(0)
            .status(WorkflowStatus.TEMPLATE)
            .simulation(simulation)
            .build();
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
   * @param workflowTemplate workflow to launch
   * @return the created workflow run, or null if no template exists
   */
  public Workflow launchWorkflow(Workflow workflowTemplate) {

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
   * Checks if a simulation has workflow chaining enabled.
   *
   * @param simulationId the ID of the simulation to check
   * @return true if the simulation has at least one workflow, false otherwise
   */
  public boolean isSimulationChaining(String simulationId) {
    List<Workflow> workflows = this.workflowRepository.findAllBySimulation_Id(simulationId);
    return !workflows.isEmpty();
  }

  /**
   * Finds the workflow template for a simulation.
   *
   * @param simulationId the ID of the simulation
   * @return the workflow template, or null if not found
   */
  public Optional<Workflow> findWorkflowTemplateBySimulationId(String simulationId) {
    return Optional.ofNullable(
        this.workflowRepository.findBySimulation_IdAndStatus(
            simulationId, WorkflowStatus.TEMPLATE));
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
