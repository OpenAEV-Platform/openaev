package io.openaev.service.chaining;

import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.IpAddressUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
@Service
public class WorkflowService {

  private final WorkflowRepository workflowRepository;
  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;

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

  /**
   * Returns the TEMPLATE workflow for the given ID with its scope-rules collection eagerly
   * initialized, so the caller can safely read the collection after the session closes (e.g. inside
   * a static mapper called from the controller layer).
   *
   * @param workflowId the ID of the workflow
   * @return the template workflow with scope rules initialized
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  @Transactional(readOnly = true)
  public Workflow getWorkflowConfiguration(@NotBlank String workflowId) {
    Workflow workflow = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    Hibernate.initialize(workflow.getWorkflowScopeRules());
    return workflow;
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
   * Loads the TEMPLATE workflow, applies the configuration input and persists it only when at least
   * one field or scope rule has actually changed.
   *
   * <p>The entire operation runs inside a single transaction so that lazy-collection access and the
   * subsequent save are atomic.
   *
   * @param workflowId the ID of the TEMPLATE workflow to update
   * @param input the new configuration values
   * @return the (possibly updated) workflow
   * @throws ElementNotFoundException if no TEMPLATE workflow is found with the given ID
   */
  @Transactional(rollbackFor = Exception.class)
  public Workflow updateWorkflowConfiguration(
      @NotBlank String workflowId, WorkflowConfigurationInput input) {
    Workflow workflow = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    boolean changed = applyConfigurationInput(input, workflow);
    if (changed) {
      boolean workflowExecutedNotEmpty = !workflow.getWorkflowsExecuted().isEmpty();
      workflow.setEdited(workflowExecutedNotEmpty);
      workflowRepository.save(workflow);
    }
    return workflow;
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
            .rateLimitEnabled(workflowTemplate.isRateLimitEnabled())
            .maxAttempts(workflowTemplate.getMaxAttempts())
            .maxTemporalRateSeconds(workflowTemplate.getMaxTemporalRateSeconds())
            .timeoutEnabled(workflowTemplate.isTimeoutEnabled())
            .timeoutSeconds(workflowTemplate.getTimeoutSeconds())
            .safeModeEnabled(workflowTemplate.isSafeModeEnabled())
            .build();

    copyScopeRules(workflowTemplate, run);
    return saveWorkflowRun(run);
  }

  /**
   * Copies scope rules from a source workflow to a target workflow, creating fresh entities so each
   * workflow owns its own rule rows.
   */
  private void copyScopeRules(Workflow source, Workflow target) {
    List<WorkflowScopeRule> sourceRules =
        workflowScopeRuleRepository.findAllByWorkflowId(source.getId());

    if (CollectionUtils.isEmpty(sourceRules)) {
      return;
    }

    target
        .getWorkflowScopeRules()
        .addAll(sourceRules.stream().map(rule -> WorkflowScopeRule.copyOf(rule, target)).toList());
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

  // -- Configuration Update --

  /**
   * Copies all fields from {@code input} onto {@code workflow} and returns {@code true} when at
   * least one value changed.
   */
  private boolean applyConfigurationInput(WorkflowConfigurationInput input, Workflow workflow) {
    boolean changed = false;
    if (workflow.isRateLimitEnabled() != input.isRateLimitEnabled()) {
      workflow.setRateLimitEnabled(input.isRateLimitEnabled());
      changed = true;
    }
    if (!Objects.equals(workflow.getMaxAttempts(), input.getMaxAttempts())) {
      workflow.setMaxAttempts(input.getMaxAttempts());
      changed = true;
    }
    if (!Objects.equals(workflow.getMaxTemporalRateSeconds(), input.getMaxTemporalRateSeconds())) {
      workflow.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
      changed = true;
    }
    if (workflow.isTimeoutEnabled() != input.isTimeoutEnabled()) {
      workflow.setTimeoutEnabled(input.isTimeoutEnabled());
      changed = true;
    }
    if (!Objects.equals(workflow.getTimeoutSeconds(), input.getTimeoutSeconds())) {
      workflow.setTimeoutSeconds(input.getTimeoutSeconds());
      changed = true;
    }
    if (workflow.isSafeModeEnabled() != input.isSafeModeEnabled()) {
      workflow.setSafeModeEnabled(input.isSafeModeEnabled());
      changed = true;
    }
    return applyScopeRules(input.getWorkflowScopeRules(), workflow) || changed;
  }

  /**
   * Reconciles the workflow's scope-rule collection against the provided inputs: removes rules not
   * present in the input, adds new ones, and updates changed ones in-place.
   *
   * @return {@code true} if the collection was modified
   */
  private boolean applyScopeRules(List<WorkflowScopeRuleInput> ruleInputs, Workflow workflow) {
    List<WorkflowScopeRule> existing = workflow.getWorkflowScopeRules();

    if (CollectionUtils.isEmpty(ruleInputs) && CollectionUtils.isEmpty(existing)) {
      return false;
    }
    if (CollectionUtils.isEmpty(ruleInputs)) {
      existing.clear();
      return true;
    }

    List<WorkflowScopeRuleInput> deduplicated = deduplicateRules(ruleInputs);

    Set<String> inputIds =
        deduplicated.stream()
            .map(WorkflowScopeRuleInput::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<String, WorkflowScopeRule> existingById =
        existing.stream().collect(Collectors.toMap(WorkflowScopeRule::getId, r -> r));

    boolean changed = existing.removeIf(r -> !inputIds.contains(r.getId()));

    Set<String> processedIds = new HashSet<>();

    for (WorkflowScopeRuleInput ruleInput : deduplicated) {
      String ruleId = ruleInput.getId();
      if (ruleId == null) {
        existing.add(buildScopeRule(ruleInput, workflow));
        changed = true;
      } else {
        if (!processedIds.contains(ruleId)) {
          WorkflowScopeRule existingRule = existingById.get(ruleId);
          if (existingRule != null && hasRuleChanged(existingRule, ruleInput)) {
            updateScopeRule(existingRule, ruleInput);
            changed = true;
          }
          processedIds.add(ruleId);
        }
      }
    }
    return changed;
  }

  /**
   * Filters out duplicate scope-rule inputs, keeping only the first occurrence of each unique
   * (selectedMode, ruleSource, ruleValue) combination.
   */
  private List<WorkflowScopeRuleInput> deduplicateRules(List<WorkflowScopeRuleInput> rules) {
    Set<String> seen = new HashSet<>();
    return rules.stream()
        .filter(
            rule ->
                seen.add(
                    rule.getSelectedMode()
                        + ":"
                        + rule.getRuleSource()
                        + ":"
                        + (rule.getRuleValue() != null
                            ? rule.getRuleValue().trim().toLowerCase()
                            : "")))
        .toList();
  }

  private boolean hasRuleChanged(WorkflowScopeRule existing, WorkflowScopeRuleInput input) {
    return existing.getSelectedMode() != input.getSelectedMode()
        || existing.getRuleSource() != input.getRuleSource()
        || !Objects.equals(existing.getRuleValue(), input.getRuleValue());
  }

  private void updateScopeRule(WorkflowScopeRule existing, WorkflowScopeRuleInput input) {
    existing.setSelectedMode(input.getSelectedMode());
    existing.setRuleSource(input.getRuleSource());
    existing.setRuleValue(input.getRuleValue());
    existing.setValueType(detectValueType(input));
  }

  private WorkflowScopeRule buildScopeRule(WorkflowScopeRuleInput input, Workflow workflow) {
    return WorkflowScopeRule.builder()
        .selectedMode(input.getSelectedMode())
        .ruleSource(input.getRuleSource())
        .ruleValue(input.getRuleValue())
        .valueType(detectValueType(input))
        .workflow(workflow)
        .build();
  }

  private ScopeRuleValueType detectValueType(WorkflowScopeRuleInput input) {
    if (input.getRuleSource() != null) {
      return switch (input.getRuleSource()) {
        case ASSET -> ScopeRuleValueType.ASSET_ID;
        case ASSET_GROUP -> ScopeRuleValueType.ASSET_GROUP_ID;
        default -> resolveValueTypeFromString(input.getRuleValue());
      };
    }
    return resolveValueTypeFromString(input.getRuleValue());
  }

  private ScopeRuleValueType resolveValueTypeFromString(String value) {
    String trimmed = value != null ? value.trim() : "";
    if (IpAddressUtils.isIpv4Subnet(trimmed) || IpAddressUtils.isIpv6Subnet(trimmed)) {
      return ScopeRuleValueType.IP_SUBNET;
    }
    if (IpAddressUtils.isIpv4Address(trimmed) || IpAddressUtils.isIpv6Address(trimmed)) {
      return ScopeRuleValueType.IP;
    }
    return ScopeRuleValueType.DOMAIN;
  }
}
