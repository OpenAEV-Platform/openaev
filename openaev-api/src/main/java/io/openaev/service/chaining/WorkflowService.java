package io.openaev.service.chaining;

import io.openaev.api.chaining.WorkflowConfigurationMapper;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.database.model.*;
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
  private final WorkflowConfigurationMapper workflowConfigurationMapper;

  // -- READ --

  /**
   * Retrieves a workflow by its ID and expected status.
   *
   * @param workflowId the ID of the workflow to retrieve
   * @param status the expected status
   * @return the found workflow
   * @throws ElementNotFoundException if no workflow with the given ID and status is found
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

  // -- WRITE --

  /**
   * Creates a new workflow template for a simulation with safe defaults for the inline
   * configuration (rate-limit and timeout disabled, safe-mode enabled).
   *
   * @param simulation the simulation to create the workflow for
   */
  public void creationWorkflow(Exercise simulation) {
    Workflow workflow =
        Workflow.builder()
            .version(0)
            .status(WorkflowStatus.TEMPLATE)
            .simulation(simulation)
            .rateLimitEnabled(false)
            .timeoutEnabled(false)
            .safeModeEnabled(true)
            .build();
    workflowRepository.save(workflow);
  }

  /**
   * Marks a workflow template as edited when at least one run has been executed from it.
   *
   * @param workflowId the ID of the workflow to update
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  public void updateWorkflowTemplate(String workflowId) {
    Workflow workflow = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    boolean newEditedValue = !workflow.getWorkflowsExecuted().isEmpty();
    if (workflow.isEdited() != newEditedValue) {
      workflow.setEdited(newEditedValue);
      workflowRepository.save(workflow);
    }
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
   * Launches a workflow for a simulation by creating a run from the template. Configuration fields
   * (rate-limit, timeout, safe-mode) and scope rules are copied from the template to the run.
   *
   * <p>If the template has been edited, its version is incremented before creating the run.
   *
   * @param workflowTemplate the template workflow to launch
   * @return the created workflow run
   */
  public Workflow launchWorkflow(Workflow workflowTemplate) {

    if (workflowTemplate.isEdited()) {
      workflowTemplate.setEdited(false);
      workflowTemplate.setVersion(workflowTemplate.getVersion() + 1);
      workflowTemplate = workflowRepository.save(workflowTemplate);
    }

    Workflow run =
        Workflow.builder()
            .isEdited(false)
            .status(WorkflowStatus.RUN)
            .simulation(workflowTemplate.getSimulation())
            .version(workflowTemplate.getVersion())
            .workflowTemplate(workflowTemplate)
            // Copy inline configuration from template
            .rateLimitEnabled(workflowTemplate.isRateLimitEnabled())
            .maxAttempts(workflowTemplate.getMaxAttempts())
            .maxTemporalRateSeconds(workflowTemplate.getMaxTemporalRateSeconds())
            .timeoutEnabled(workflowTemplate.isTimeoutEnabled())
            .timeoutSeconds(workflowTemplate.getTimeoutSeconds())
            .safeModeEnabled(workflowTemplate.isSafeModeEnabled())
            .build();

    Workflow savedRun = saveWorkflowRun(run);
    copyScopeRules(workflowTemplate, savedRun);
    return savedRun;
  }

  /**
   * Copies scope rules from a source workflow to a target workflow, creating fresh entities so each
   * workflow owns its own rule rows.
   */
  private void copyScopeRules(Workflow source, Workflow target) {
    List<WorkflowScopeRule> sourceRules = source.getWorkflowScopeRules();
    if (sourceRules == null || sourceRules.isEmpty()) {
      return;
    }
    List<WorkflowScopeRule> copies =
        sourceRules.stream()
            .map(
                rule -> {
                  WorkflowScopeRule copy = new WorkflowScopeRule();
                  copy.setSelectedMode(rule.getSelectedMode());
                  copy.setRuleSource(rule.getRuleSource());
                  copy.setRuleValue(rule.getRuleValue());
                  copy.setValueType(rule.getValueType());
                  copy.setWorkflow(target);
                  return copy;
                })
            .toList();
    target.setWorkflowScopeRules(copies);
    workflowRepository.save(target);
  }

  /**
   * Checks if a simulation has workflow enabled.
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
   * @return the workflow template wrapped in an Optional, or empty if not found
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

  /**
   * Returns the TEMPLATE workflow that holds the configuration for a given workflow ID. Since
   * configuration fields are stored inline on the workflow row, no separate lookup is needed.
   *
   * @param workflowId the ID of the workflow
   * @return the template workflow (which carries the configuration)
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  public Workflow getWorkflowConfiguration(@NotBlank String workflowId) {
    return getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
  }

  /**
   * Updates the inline workflow configuration fields and scope rules for a given TEMPLATE workflow.
   *
   * @param workflowId the ID of the workflow to update
   * @param input the new configuration values
   * @return the updated workflow
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  @Transactional
  public Workflow updateWorkflowConfiguration(
      @NotBlank String workflowId, @Valid WorkflowConfigurationInput input) {
    Workflow workflow = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    workflowConfigurationMapper.applyInput(input, workflow);
    updateWorkflowTemplate(workflowId);
    return workflowRepository.save(workflow);
  }
}
