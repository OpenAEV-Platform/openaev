package io.openaev.service.workflow;

import io.openaev.api.workflow.WorkflowConfigurationMapper;
import io.openaev.api.workflow.dto.WorkflowConfigurationInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowConfigurationRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class WorkflowService {
  private final WorkflowRepository workflowRepository;
  private final WorkflowConfigurationRepository workflowConfigurationRepository;
  private final WorkflowConfigurationMapper workflowConfigurationMapper;

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
    // Create workflow configuration
    createDefaultWorkflowConfiguration(workflow);
  }

  /** Creates a default workflow configuration for a scenario. */
  private void createDefaultWorkflowConfiguration(Workflow workflow) {
    WorkflowConfiguration configuration = new WorkflowConfiguration();
    configuration.setRateLimitEnabled(false);
    configuration.setTimeoutEnabled(false);
    configuration.setSafeModeEnabled(true);
    configuration.setWorkflow(workflow);
    workflow.setWorkflowConfiguration(configuration);
    this.workflowConfigurationRepository.save(configuration);
  }

  /**
   * Marks a workflow template as edited.
   *
   * @param workflowId the ID of the workflow to update
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    // Mark as edited if at least one run has been executed from this template
    workflow.setEdited(!workflow.getWorkflowsExecuted().isEmpty());
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

    Workflow savedWorkflowRun = saveWorkflowRun(run);
    saveWorkflowConfiguration(workflowTemplate, savedWorkflowRun);
    return savedWorkflowRun;
  }

  private void saveWorkflowConfiguration(Workflow workflowTemplate, Workflow workflowRun) {
    WorkflowConfiguration templateConfiguration = workflowTemplate.getWorkflowConfiguration();
    if (templateConfiguration == null) {
      return;
    }
    WorkflowConfiguration runConfiguration = copyConfiguration(templateConfiguration, workflowRun);
    workflowRun.setWorkflowConfiguration(runConfiguration);
    this.workflowConfigurationRepository.save(runConfiguration);
  }

  /**
   * Creates a shallow copy of a {@link WorkflowConfiguration}, bound to the given workflow run.
   *
   * @param source the template configuration to copy from
   * @param workflowRun the workflow run to bind the new configuration to
   * @return a new {@link WorkflowConfiguration} with the same field values
   */
  private WorkflowConfiguration copyConfiguration(
      WorkflowConfiguration source, Workflow workflowRun) {
    WorkflowConfiguration copy = new WorkflowConfiguration();
    copy.setSafeModeEnabled(source.isSafeModeEnabled());
    // Rate limit
    copy.setRateLimitEnabled(source.isRateLimitEnabled());
    copy.setMaxAttempts(source.getMaxAttempts());
    copy.setMaxTemporalRateSeconds(source.getMaxTemporalRateSeconds());
    // Timeout
    copy.setTimeoutEnabled(source.isTimeoutEnabled());
    copy.setTimeoutSeconds(source.getTimeoutSeconds());
    copy.setWorkflow(workflowRun);
    return copy;
  }

  /**
   * Checks if a simulation has workflow workflow enabled.
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

  public WorkflowConfiguration getWorkflowConfiguration(@NotBlank String workflowId) {
    Workflow workflow = this.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    if (workflow.getWorkflowConfiguration() == null) {
      throw new ElementNotFoundException(
          "Workflow configuration not found for this workflow: " + workflowId);
    }
    return workflow.getWorkflowConfiguration();
  }

  @Transactional
  public WorkflowConfiguration updateWorkflowConfiguration(
      @NotBlank String workflowId, @Valid WorkflowConfigurationInput input) {
    WorkflowConfiguration configuration = getWorkflowConfiguration(workflowId);
    workflowConfigurationMapper.applyInput(input, configuration);
    this.updateWorkflowTemplate(workflowId);
    return workflowConfigurationRepository.save(configuration);
  }
}
