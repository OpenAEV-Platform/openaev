package io.openaev.service.chaining;

import com.google.gson.Gson;
import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScopeVariableRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.IpAddressUtils;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowService {

  private static final Gson GSON = new Gson();

  private final StepService stepService;
  private final PreviewFeatureService previewFeatureService;

  private final WorkflowRepository workflowRepository;
  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;
  private final ScopeVariableRepository scopeVariableRepository;
  private final WorkflowStateService workflowStateService;

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
    Hibernate.initialize(workflow.getWorkflowScopeVariables());
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
   * Creates a new workflow template for a scenario.
   *
   * @param scenario the scenario to create the workflow for
   */
  public void creationWorkflow(Scenario scenario) {
    Workflow workflow =
        Workflow.builder()
            .version(0)
            .status(WorkflowStatus.TEMPLATE)
            .scenario(scenario)
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
  public Workflow launchWorkflowSimulation(Workflow workflowTemplate) {
    workflowTemplate = updateEditedWorkflow(workflowTemplate);

    Workflow run = copyWorkflowTemplateToRun(workflowTemplate);

    return saveWorkflowRun(run);
  }

  /**
   * Launches a workflow for a scenario by creating a simulation-level template and a run from it.
   *
   * @param workflowTemplateScenario the scenario's workflow template
   * @param simulation the simulation to attach the run to
   * @return the created workflow run
   */
  public Workflow launchWorkflowScenario(Workflow workflowTemplateScenario, Exercise simulation) {
    // Copy workflow TEMPLATE (scenario) to a new workflow TEMPLATE (simulation)
    Workflow workflowTemplateSimulation =
        copyWorkflowTemplateToSimulation(workflowTemplateScenario, simulation);
    workflowTemplateSimulation = saveWorkflowRun(workflowTemplateSimulation);

    // Copy workflow TEMPLATE (simulation) to a new workflow execution RUN (simulation)
    Workflow run = copyWorkflowTemplateToRun(workflowTemplateSimulation);

    return saveWorkflowRun(run);
  }

  /** Increments the version and clears the edited flag when the template has pending runs. */
  private Workflow updateEditedWorkflow(Workflow workflowTemplate) {
    if (workflowTemplate.isEdited() && !workflowTemplate.getWorkflowsExecuted().isEmpty()) {
      workflowTemplate.setEdited(false);
      workflowTemplate.setVersion(workflowTemplate.getVersion() + 1);
      workflowTemplate = workflowRepository.save(workflowTemplate);
    }
    return workflowTemplate;
  }

  /** Creates a RUN workflow by copying configuration and scope rules from a template. */
  private Workflow copyWorkflowTemplateToRun(Workflow workflowTemplateFrom) {
    // Copy workflow TEMPLATE to Workflow RUN (execution)
    Workflow workflowRunTo =
        Workflow.builder()
            .isEdited(false)
            .status(WorkflowStatus.RUN)
            .simulation(workflowTemplateFrom.getSimulation())
            .version(workflowTemplateFrom.getVersion())
            .workflowTemplate(workflowTemplateFrom)
            .rateLimitEnabled(workflowTemplateFrom.isRateLimitEnabled())
            .maxAttempts(workflowTemplateFrom.getMaxAttempts())
            .maxTemporalRateSeconds(workflowTemplateFrom.getMaxTemporalRateSeconds())
            .timeoutEnabled(workflowTemplateFrom.isTimeoutEnabled())
            .timeoutSeconds(workflowTemplateFrom.getTimeoutSeconds())
            .safeModeEnabled(workflowTemplateFrom.isSafeModeEnabled())
            .build();
    copyScopeRules(workflowTemplateFrom, workflowRunTo);
    copyScopeVariables(workflowTemplateFrom, workflowRunTo);
    return workflowRunTo;
  }

  private Workflow copyWorkflowTemplateToScenario(
      Workflow workflowTemplateScenarioFrom, Scenario scenarioTo) {
    // Copy WORKFLOW TEMPLATE to a new Workflow TEMPLATE for a scenario
    Workflow template =
        Workflow.builder()
            .isEdited(false)
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .scenario(scenarioTo)
            .rateLimitEnabled(workflowTemplateScenarioFrom.isRateLimitEnabled())
            .maxAttempts(workflowTemplateScenarioFrom.getMaxAttempts())
            .maxTemporalRateSeconds(workflowTemplateScenarioFrom.getMaxTemporalRateSeconds())
            .timeoutEnabled(workflowTemplateScenarioFrom.isTimeoutEnabled())
            .timeoutSeconds(workflowTemplateScenarioFrom.getTimeoutSeconds())
            .safeModeEnabled(workflowTemplateScenarioFrom.isSafeModeEnabled())
            .build();
    copyScopeRules(workflowTemplateScenarioFrom, template);
    copyScopeVariables(workflowTemplateScenarioFrom, template);

    return template;
  }

  private Workflow copyWorkflowTemplateToSimulation(
      Workflow workflowTemplateFrom, Exercise simulationTo) {
    // COPY WORKFLOW TEMPLATE to a new Workflow TEMPLATE for a simulation
    Workflow template =
        Workflow.builder()
            .isEdited(false)
            .status(WorkflowStatus.TEMPLATE)
            .version(0)
            .simulation(simulationTo)
            .rateLimitEnabled(workflowTemplateFrom.isRateLimitEnabled())
            .maxAttempts(workflowTemplateFrom.getMaxAttempts())
            .maxTemporalRateSeconds(workflowTemplateFrom.getMaxTemporalRateSeconds())
            .timeoutEnabled(workflowTemplateFrom.isTimeoutEnabled())
            .timeoutSeconds(workflowTemplateFrom.getTimeoutSeconds())
            .safeModeEnabled(workflowTemplateFrom.isSafeModeEnabled())
            .build();
    copyScopeRules(workflowTemplateFrom, template);
    copyScopeVariables(workflowTemplateFrom, template);
    return template;
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
   * Copies scope variables from a source workflow to a target workflow, creating fresh entities so
   * each workflow owns its own variable rows.
   */
  private void copyScopeVariables(Workflow source, Workflow target) {
    List<ScopeVariable> sourceVariables =
        scopeVariableRepository.findAllByWorkflowId(source.getId());

    if (CollectionUtils.isEmpty(sourceVariables)) {
      return;
    }

    target
        .getWorkflowScopeVariables()
        .addAll(
            sourceVariables.stream()
                .map(variable -> ScopeVariable.copyOf(variable, target))
                .toList());
  }

  /**
   * Reconciles the workflow's scope-variable collection against the provided inputs: removes
   * variables not present in the input, adds new ones, and updates changed ones in-place.
   *
   * @return {@code true} if the collection was modified
   */
  private boolean applyScopeVariables(List<ScopeVariableInput> variableInputs, Workflow workflow) {
    List<ScopeVariable> existing = workflow.getWorkflowScopeVariables();

    if (CollectionUtils.isEmpty(variableInputs) && CollectionUtils.isEmpty(existing)) {
      return false;
    }
    if (CollectionUtils.isEmpty(variableInputs)) {
      existing.clear();
      return true;
    }

    Set<String> inputIds =
        variableInputs.stream()
            .map(ScopeVariableInput::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<String, ScopeVariable> existingById =
        existing.stream().collect(Collectors.toMap(ScopeVariable::getId, v -> v));

    boolean changed = existing.removeIf(v -> !inputIds.contains(v.getId()));

    for (ScopeVariableInput input : variableInputs) {
      if (input.getId() == null) {
        existing.add(buildScopeVariable(input, workflow));
        changed = true;
      } else {
        ScopeVariable existingVar = existingById.get(input.getId());
        if (existingVar != null && hasVariableChanged(existingVar, input)) {
          updateScopeVariable(existingVar, input);
          changed = true;
        }
      }
    }
    return changed;
  }

  private boolean hasVariableChanged(ScopeVariable existing, ScopeVariableInput input) {
    return !Objects.equals(existing.getKey(), input.getKey())
        || !Objects.equals(existing.getType(), input.getType())
        || !Objects.equals(existing.getValue(), input.getValue())
        || !Objects.equals(existing.getDescription(), input.getDescription());
  }

  private void updateScopeVariable(ScopeVariable existing, ScopeVariableInput input) {
    existing.setKey(input.getKey());
    existing.setType(input.getType());
    existing.setValue(input.getValue());
    existing.setDescription(input.getDescription());
  }

  private ScopeVariable buildScopeVariable(ScopeVariableInput input, Workflow workflow) {
    return ScopeVariable.builder()
        .key(input.getKey())
        .type(input.getType())
        .value(input.getValue())
        .description(input.getDescription())
        .workflow(workflow)
        .build();
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
   * Checks if a scenario has workflow chaining enabled.
   *
   * @param scenarioId the ID of the scenario to check
   * @return true if the scenario has one workflow, false otherwise
   */
  public boolean isScenarioChaining(String scenarioId) {
    List<Workflow> workflows =
        this.workflowRepository.findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE);
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
   * Finds workflows executed for a simulation.
   *
   * @param simulationId the ID of the simulation
   * @return a list of workflow executed (status = RUN)
   */
  public List<Workflow> findWorkflowRunBySimulationId(String simulationId) {
    return this.workflowRepository.findAllBySimulation_IdAndStatus(
        simulationId, WorkflowStatus.RUN);
  }

  /**
   * Finds the workflow template for a scenario.
   *
   * @param scenarioId the ID of the scenario
   * @return the workflow template, or null if not found
   */
  public Optional<Workflow> findWorkflowTemplateByScenarioId(String scenarioId)
      throws ChainingException {
    List<Workflow> workflows =
        this.workflowRepository.findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE);
    if (workflows.size() > 1)
      throw new ChainingException(
          "Error Model DB - Many Workflow TEMPLATE for the same scenario ID : " + scenarioId);
    if (workflows.isEmpty()) return Optional.empty();
    return Optional.ofNullable(workflows.get(0));
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
    boolean rulesChanged = applyScopeRules(input.getWorkflowScopeRules(), workflow);
    boolean variablesChanged = applyScopeVariables(input.getWorkflowScopeVariables(), workflow);
    return rulesChanged || variablesChanged || changed;
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

  /** Persists a list of workflows in batch. */
  public void saveAll(List<Workflow> workflows) {
    workflowRepository.saveAll(workflows);
  }

  /**
   * Duplicates a scenario's workflow template to a new scenario.
   *
   * @param scenarioIdFrom source scenario ID
   * @param scenarioTo target scenario entity
   * @return the new workflow template, or null if the source has no workflow
   */
  public Workflow duplicateScenario(@NotBlank String scenarioIdFrom, @NotBlank Scenario scenarioTo)
      throws ChainingException {

    Optional<Workflow> oldWorkflowOpt = findWorkflowTemplateByScenarioId(scenarioIdFrom);
    if (oldWorkflowOpt.isEmpty()) return null;
    Workflow oldWorkflowTemplateScenario = oldWorkflowOpt.get();

    Workflow newWorkflowTemplateScenario =
        copyWorkflowTemplateToScenario(oldWorkflowTemplateScenario, scenarioTo);
    return workflowRepository.save(newWorkflowTemplateScenario);
  }

  /**
   * Duplicates a simulation's workflow template to a new simulation.
   *
   * @param simulationIdFrom source simulation ID
   * @param simulationTo target simulation entity
   * @return the new workflow template, or null if the source has no workflow
   */
  public Workflow duplicateSimulation(
      @NotBlank String simulationIdFrom, @NotBlank Exercise simulationTo) {

    Optional<Workflow> oldWorkflowOpt = findWorkflowTemplateBySimulationId(simulationIdFrom);
    if (oldWorkflowOpt.isEmpty()) return null;
    Workflow oldWorkflowTemplateSimulation = oldWorkflowOpt.get();

    Workflow newWorkflowTemplateScenario =
        copyWorkflowTemplateToSimulation(oldWorkflowTemplateSimulation, simulationTo);
    return workflowRepository.save(newWorkflowTemplateScenario);
  }

  /**
   * Throws if the chaining preview feature is not enabled.
   *
   * @throws ChainingException when the feature flag is disabled
   */
  public void isPreviewFeatureChainingEnable() throws ChainingException {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
      throw new ChainingException("Feature chaining is not enabled");
  }

  /**
   * Start workflow for given simulation
   *
   * @param simulationId id of the simulation to start
   */
  @Transactional(rollbackFor = Exception.class)
  public void startWorkflowBySimulationId(String simulationId) throws ChainingException {
    Workflow workflowTemplate =
        findWorkflowTemplateBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Simulation ID: " + simulationId));
    Workflow workflowRun = launchWorkflowSimulation(workflowTemplate);
    startWorkflow(workflowRun);
  }

  /**
   * Start workflow for given scenario
   *
   * @param scenarioId id of the scenario to start
   */
  @Transactional(rollbackFor = Exception.class)
  public void startWorkflowByScenarioIdAndSimulation(String scenarioId, Exercise simulation)
      throws ChainingException {
    Workflow workflowTemplateScenario =
        findWorkflowTemplateByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Scenario ID: " + scenarioId));

    Workflow workflowRun = launchWorkflowScenario(workflowTemplateScenario, simulation);
    Workflow workflowTemplateSimulation = workflowRun.getWorkflowTemplate();
    stepService.copyStepTemplate(workflowTemplateScenario, workflowTemplateSimulation);

    startWorkflow(workflowRun);
  }

  /**
   * Starts workflow evaluation: seeds global state from allowlist scope rules, evaluates step
   * progress, and saves the workflow run.
   *
   * @param workflowRun the workflow run to start
   */
  @Transactional(rollbackFor = Exception.class)
  public void startWorkflow(Workflow workflowRun) throws ChainingException {

    Map<String, ContractOutputType> fieldTypeMap =
        java.util.Arrays.stream(ContractOutputType.values())
            .collect(Collectors.toMap(ContractOutputType::name, type -> type));

    Map<String, List<String>> scopeData = extractScopeData(workflowRun);

    // Sync global state and define next steps to be executed
    workflowStateService.syncState(GSON.toJsonTree(scopeData), fieldTypeMap, workflowRun);
    stepService.evaluateWorkflowProgress(workflowRun);

    saveWorkflowRun(workflowRun);
  }

  private Map<String, List<String>> extractScopeData(Workflow workflowRun) {
    if (workflowRun.getAllowlist() == null) {
      return Collections.emptyMap();
    }
    return workflowRun.getAllowlist().stream()
        .collect(
            Collectors.groupingBy(
                rule -> rule.getValueType().getContractOutputType(),
                Collectors.mapping(WorkflowScopeRule::getRuleValue, Collectors.toList())));
  }
}
