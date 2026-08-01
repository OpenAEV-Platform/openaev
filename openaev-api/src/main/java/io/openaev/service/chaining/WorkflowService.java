package io.openaev.service.chaining;

import static org.springframework.util.StringUtils.hasText;

import com.google.gson.Gson;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ScopeVariableRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.telemetry.metric_collectors.ChainingSafetyPolicyMetricCollector;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.telemetry.metric_collectors.ScopeMetricCollector;
import io.openaev.utils.IpAddressUtils;
import io.openaev.utils.PrimitiveValueMaskingUtils;
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

  public static final long DEFAULT_TIMEOUT_SECONDS = 3600L;

  private static final Gson GSON = new Gson();

  private final StepService stepService;
  private final PreviewFeatureService previewFeatureService;
  private final WorkflowStateService workflowStateService;
  private final StepDelayQueueService stepDelayQueueService;
  private final SimulationRateLimitService simulationRateLimitService;

  private final WorkflowRepository workflowRepository;
  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;
  private final ScopeVariableRepository scopeVariableRepository;

  private final ScopeMetricCollector scopeMetricCollector;
  private final ChainingSafetyPolicyMetricCollector chainingSafetyPolicyMetricCollector;
  private final ResultsMetricCollector resultsMetricCollector;

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

  public Workflow findById(@NotBlank final String workflowId) {
    return this.workflowRepository
        .findById(workflowId)
        .orElseThrow(
            () -> new ElementNotFoundException("Workflow not found with id: " + workflowId));
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
   * configuration (rate-limit disabled, timeout enabled to 1 hour, safe-mode enabled).
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
            .timeoutEnabled(true)
            .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
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
            .timeoutEnabled(true)
            .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
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
    WorkflowEditability.assertLogicMapEditable(workflow);
    boolean changed = applyConfigurationInput(input, workflow);
    if (changed) {
      boolean workflowExecutedNotEmpty = !workflow.getWorkflowsExecuted().isEmpty();
      workflow.setEdited(workflowExecutedNotEmpty);
      workflowRepository.save(workflow);
    }
    return workflow;
  }

  /**
   * Applies a configuration update directly to the RUN workflow(s) of a simulation, so scope
   * (allow/deny) and rate-limit edits take effect on a live simulation without stopping it.
   *
   * <p>This is the substrate for autonomous-run live steering: the chaining engine reads the
   * denylist from the RUN workflow on every subsequent step evaluation (see {@code
   * PrimitiveValidationContextBuilder} / {@code ScopeService}), so a denylist entry added here
   * walls off the matching assets on the next decision cycle. Only meaningful for autonomous /
   * chained runs; for a normal run there is at most one RUN workflow.
   *
   * @param simulationId the simulation whose RUN workflow(s) should be edited
   * @param input the new scope / rate-limit configuration
   * @return the updated RUN workflows
   */
  @Transactional(rollbackFor = Exception.class)
  public List<Workflow> updateRunWorkflowConfiguration(
      @NotBlank String simulationId, WorkflowConfigurationInput input) {
    List<Workflow> runs = findWorkflowRunBySimulationId(simulationId);
    for (Workflow run : runs) {
      if (applyConfigurationInput(input, run)) {
        workflowRepository.save(run);
      }
    }
    return runs;
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
            .keepAlive(workflowTemplateFrom.isKeepAlive())
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
            .keepAlive(workflowTemplateScenarioFrom.isKeepAlive())
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
            .keepAlive(workflowTemplateFrom.isKeepAlive())
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
    String resolvedValue = resolveScopeVariableValueForPersistence(existing, input);
    return !Objects.equals(existing.getKey(), input.getKey())
        || !Objects.equals(existing.getType(), input.getType())
        || !Objects.equals(existing.getValue(), resolvedValue)
        || !Objects.equals(existing.getDescription(), input.getDescription());
  }

  private void updateScopeVariable(ScopeVariable existing, ScopeVariableInput input) {
    String resolvedValue = resolveScopeVariableValueForPersistence(existing, input);
    existing.setKey(input.getKey());
    existing.setType(input.getType());
    existing.setValue(resolvedValue);
    existing.setDescription(input.getDescription());
  }

  /**
   * Resolves the scope variable value that should be persisted from an update payload.
   *
   * <p>When a sensitive value is masked in API responses, the frontend may send this masked
   * representation back unchanged. In that case we must preserve the existing raw value rather than
   * overwrite it with the masked string.
   */
  private String resolveScopeVariableValueForPersistence(
      ScopeVariable existing, ScopeVariableInput input) {
    if (PrimitiveValueMaskingUtils.isMaskedRepresentationOfCurrentValue(
        existing.getType(), existing.getValue(), input.getValue())) {
      return existing.getValue();
    }
    return input.getValue();
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
   * Finds the workflow template for a scenario without throwing on multiple workflows. Used for
   * export where we simply want the template if it exists.
   *
   * @param scenarioId the ID of the scenario
   * @return the workflow template wrapped in an Optional, or empty if not found
   */
  @Transactional(readOnly = true)
  public Optional<Workflow> findWorkflowTemplateByScenarioIdForExport(String scenarioId) {
    List<Workflow> workflows =
        this.workflowRepository.findByScenario_IdAndStatus(scenarioId, WorkflowStatus.TEMPLATE);
    if (workflows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(workflows.getFirst());
  }

  /**
   * Finds the workflow template for a simulation without throwing. Used for export.
   *
   * @param simulationId the ID of the simulation
   * @return the workflow template wrapped in an Optional, or empty if not found
   */
  @Transactional(readOnly = true)
  public Optional<Workflow> findWorkflowTemplateBySimulationIdForExport(String simulationId) {
    List<Workflow> workflows =
        this.workflowRepository.findAllBySimulation_IdAndStatus(
            simulationId, WorkflowStatus.TEMPLATE);
    if (workflows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(workflows.getFirst());
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
    if (workflows.size() > 1) {
      throw new ChainingException(
          "Error Model DB - Many Workflow TEMPLATE for the same scenario ID : " + scenarioId);
    }
    if (workflows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(workflows.getFirst());
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
   * Deletes all workflow states associated with workflows of the given simulation.
   *
   * @param simulationId the ID of the simulation whose workflow states should be cleared
   */
  public void deleteWorkflowStatesBySimulationId(String simulationId) {
    workflowStateService.deleteAllBySimulationId(simulationId);
  }

  // -- Configuration Update --

  /**
   * Copies all fields from {@code input} onto {@code workflow} and returns {@code true} when at
   * least one value changed.
   */
  private boolean applyConfigurationInput(WorkflowConfigurationInput input, Workflow workflow) {
    boolean changed = false;
    boolean rateLimitChanged = false;
    boolean timeoutChanged = false;

    if (workflow.isRateLimitEnabled() != input.isRateLimitEnabled()) {
      workflow.setRateLimitEnabled(input.isRateLimitEnabled());
      changed = true;
      rateLimitChanged = true;
    }
    if (!Objects.equals(workflow.getMaxAttempts(), input.getMaxAttempts())) {
      workflow.setMaxAttempts(input.getMaxAttempts());
      changed = true;
      rateLimitChanged = true;
    }
    if (!Objects.equals(workflow.getMaxTemporalRateSeconds(), input.getMaxTemporalRateSeconds())) {
      workflow.setMaxTemporalRateSeconds(input.getMaxTemporalRateSeconds());
      changed = true;
      rateLimitChanged = true;
    }
    if (workflow.isTimeoutEnabled() != input.isTimeoutEnabled()) {
      workflow.setTimeoutEnabled(input.isTimeoutEnabled());
      changed = true;
      timeoutChanged = true;
    }
    if (!Objects.equals(workflow.getTimeoutSeconds(), input.getTimeoutSeconds())) {
      workflow.setTimeoutSeconds(input.getTimeoutSeconds());
      changed = true;
      timeoutChanged = true;
    }
    if (workflow.isSafeModeEnabled() != input.isSafeModeEnabled()) {
      workflow.setSafeModeEnabled(input.isSafeModeEnabled());
      changed = true;
    }
    boolean rulesChanged = applyScopeRules(input.getWorkflowScopeRules(), workflow);
    boolean variablesChanged = applyScopeVariables(input.getWorkflowScopeVariables(), workflow);

    if (timeoutChanged) {
      long timeoutSec = input.getTimeoutSeconds() != null ? input.getTimeoutSeconds() : 0L;
      chainingSafetyPolicyMetricCollector.recordTimeoutConfigured(
          timeoutSec / 3600, (timeoutSec % 3600) / 60, timeoutSec == DEFAULT_TIMEOUT_SECONDS);
    }
    if (rateLimitChanged) {
      int attempts = input.getMaxAttempts() != null ? input.getMaxAttempts() : 0;
      long seconds =
          input.getMaxTemporalRateSeconds() != null ? input.getMaxTemporalRateSeconds() : 0L;
      chainingSafetyPolicyMetricCollector.recordRateLimitConfigured(
          attempts, seconds, !input.isRateLimitEnabled());
    }

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

    // Build new rules from inputs without an ID
    List<WorkflowScopeRule> newRules =
        deduplicated.stream()
            .filter(r -> r.getId() == null)
            .map(r -> buildScopeRule(r, workflow))
            .toList();

    if (!newRules.isEmpty()) {
      existing.addAll(newRules);
      changed = true;

      trackScopeMetrics(workflow, newRules);
    }

    // Update existing rules that have changed
    Set<String> processedIds = new HashSet<>();
    for (WorkflowScopeRuleInput ruleInput : deduplicated) {
      String ruleId = ruleInput.getId();
      if (ruleId != null && processedIds.add(ruleId)) {
        WorkflowScopeRule existingRule = existingById.get(ruleId);
        if (existingRule != null && hasRuleChanged(existingRule, ruleInput)) {
          updateScopeRule(existingRule, ruleInput);
          changed = true;
        }
      }
    }

    return changed;
  }

  /**
   * Tracks metrics related to scope rules added in a workflow configuration, including the volume
   * of new rules by mode/type/source and the usage of CSV vs Manual sources.
   */
  private void trackScopeMetrics(Workflow workflow, List<WorkflowScopeRule> newRules) {
    if (CollectionUtils.isEmpty(newRules)) return;

    Map<String, Integer> modeCounts = new HashMap<>();
    Map<String, Integer> typeSourceCounts = new HashMap<>();
    Set<String> uniqueSources = new HashSet<>();

    for (WorkflowScopeRule rule : newRules) {
      String mode = rule.getSelectedMode().name();
      String typeSourceKey = rule.getValueType().name() + "|" + rule.getRuleSource().name();
      String source = rule.getRuleSource().name();

      modeCounts.merge(mode, 1, Integer::sum);
      typeSourceCounts.merge(typeSourceKey, 1, Integer::sum);
      uniqueSources.add(source);
    }

    // KPI. Record Creation Patterns (Allowlist/Denylist)
    modeCounts.forEach(scopeMetricCollector::recordScopeCreated);

    // KPI. Record Type and Source Patterns (e.g. DOMAIN|CSV, IP|Manual)
    typeSourceCounts.forEach(
        (key, count) -> {
          String[] parts = key.split("\\|");
          scopeMetricCollector.recordEntryAdded(parts[0], parts[1], count);
        });

    // KPI. Record Source Usage (CSV vs Manual only — ignore asset-based sources)
    uniqueSources.stream()
        .filter(
            source ->
                ScopeRuleSource.CSV.name().equals(source)
                    || ScopeRuleSource.MANUAL.name().equals(source))
        .forEach(source -> scopeMetricCollector.recordUsage(workflow.getId(), source));
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
    if (oldWorkflowOpt.isEmpty()) {
      return null;
    }
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
    if (oldWorkflowOpt.isEmpty()) {
      return null;
    }
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
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)) {
      throw new ChainingException("Feature chaining is not enabled");
    }
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
   * Starts workflow evaluation: seeds global state from allowlist scope rules and scope variables,
   * evaluates step progress, and saves the workflow run.
   *
   * @param workflowRun the workflow run to start
   */
  @Transactional(rollbackFor = Exception.class)
  public void startWorkflow(Workflow workflowRun) throws ChainingException {
    // Telemetry: one chaining workflow run started.
    resultsMetricCollector.recordWorkflowRun();

    ScopeStateSeed scopeStateSeed = extractScopeStateSeed(workflowRun);

    // Sync global state and define next steps to be executed
    workflowStateService.syncState(
        GSON.toJsonTree(scopeStateSeed.scopeData()),
        scopeStateSeed.scopeTypeMappings(),
        workflowRun);
    this.evaluateWorkflowProgress(workflowRun);

    saveWorkflowRun(workflowRun);
  }

  /**
   * Builds the initial global state seed for a workflow run from two sources:
   *
   * <ul>
   *   <li>Scope allowlist rules (asset/IP/subnet/domain) restricting execution targets.
   *   <li>Scope variables (e.g. a default "Username") manually defined on the Scope page.
   * </ul>
   *
   * <p>Both sources are merged under the same key convention (the {@link PrimitiveType} name) so
   * that a MAPPER condition looking for a GLOBAL value of a given type (e.g. {@code Username}) can
   * be satisfied by either an allowlist rule or a scope variable. Without this, steps whose first
   * condition requires a GLOBAL value that is only ever provided via a scope variable (there is no
   * other producer for it) could never become READY.
   */
  private ScopeStateSeed extractScopeStateSeed(Workflow workflowRun) {
    Map<String, List<String>> scopeData = new HashMap<>();
    Map<String, ChainingMappedType> typeMappings = new HashMap<>();

    if (workflowRun.getAllowlist() != null) {
      for (WorkflowScopeRule rule : workflowRun.getAllowlist()) {
        String key = rule.getValueType().name();
        scopeData.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule.getRuleValue());
        typeMappings.putIfAbsent(
            key, ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(rule.getValueType()));

        if (ScopeRuleValueType.IP_SUBNET.equals(rule.getValueType())) {
          IpAddressUtils.ExpandedSubnetHosts expanded =
              IpAddressUtils.expandSubnetToHostsByFamily(rule.getRuleValue());
          if (!expanded.ipv4Hosts().isEmpty()) {
            scopeData
                .computeIfAbsent(PrimitiveType.IPv4.name(), ignored -> new ArrayList<>())
                .addAll(expanded.ipv4Hosts());
            typeMappings.putIfAbsent(
                PrimitiveType.IPv4.name(),
                ChainingMappedType.primitive(List.of(PrimitiveType.IPv4)));
          }
          if (!expanded.ipv6Hosts().isEmpty()) {
            scopeData
                .computeIfAbsent(PrimitiveType.IPv6.name(), ignored -> new ArrayList<>())
                .addAll(expanded.ipv6Hosts());
            typeMappings.putIfAbsent(
                PrimitiveType.IPv6.name(),
                ChainingMappedType.primitive(List.of(PrimitiveType.IPv6)));
          }
        }
      }
    }

    if (workflowRun.getWorkflowScopeVariables() != null) {
      for (ScopeVariable variable : workflowRun.getWorkflowScopeVariables()) {
        if (variable.getValue() == null || variable.getValue().isBlank()) {
          continue;
        }
        String key = variable.getType().name();
        scopeData.computeIfAbsent(key, ignored -> new ArrayList<>()).add(variable.getValue());
        typeMappings.putIfAbsent(key, ChainingMappedType.primitive(variable.getType()));
      }
    }

    return new ScopeStateSeed(scopeData, typeMappings);
  }

  private record ScopeStateSeed(
      Map<String, List<String>> scopeData, Map<String, ChainingMappedType> scopeTypeMappings) {}

  // -- Timeout --

  /**
   * Checks if a workflow has ended by reading the current status from the database.
   *
   * @param workflowId the workflow ID to check
   * @return true if the workflow status is END, false otherwise or if not found
   */
  @Transactional(readOnly = true)
  public boolean isWorkflowEnded(String workflowId) {
    return workflowRepository.existsByIdAndStatus(workflowId, WorkflowStatus.END);
  }

  /**
   * Finds all RUN workflows whose timeout has expired.
   *
   * @return list of expired workflows
   */
  public List<Workflow> findAllExpiredRunWorkflows() {
    return workflowRepository.findAllExpiredRunWorkflows();
  }

  /**
   * Sets the workflow status to END and persists it.
   *
   * @param workflowRun the running workflow to end
   */
  public void endWorkflow(Workflow workflowRun) {
    workflowRun.setStatus(WorkflowStatus.END);
    workflowRepository.save(workflowRun);
  }

  /**
   * Evaluates workflow progress by checking all step templates for valid conditions and creating
   * READY steps. Sets workflow to END if no steps are ready and no delayed steps remain.
   *
   * @param workflowRun the running workflow to evaluate
   * @return the updated workflow (may have status END)
   */
  @Transactional(rollbackFor = Exception.class)
  public Workflow evaluateWorkflowProgress(Workflow workflowRun) throws ChainingException {
    // Reload within the current transaction: the caller may pass a detached entity (e.g. from a
    // queue job whose transaction has already committed). Re-fetching attaches it to the current
    // session so that lazy proxies (workflowTemplate, step collections) are accessible without
    // LazyInitializationException.
    final String workflowRunId = workflowRun.getId();
    workflowRun =
        workflowRepository
            .findById(workflowRunId)
            .orElseThrow(
                () -> new ElementNotFoundException("Workflow run not found: " + workflowRunId));

    if (workflowRun.getWorkflowTemplate() == null) {
      log.warn("Workflow run {} has no template, cannot evaluate progress.", workflowRun.getId());
      return workflowRun;
    }
    String workflowTemplateId = workflowRun.getWorkflowTemplate().getId();

    // Guard: ignore if workflow run has already ended (e.g. timeout).
    if (this.isWorkflowEnded(workflowRun.getId())) {
      log.info(
          "[Chaining] Ignoring evaluation because workflow run {} has ended.", workflowRun.getId());
    }

    // Get all step template
    List<Step> stepsTemplate = stepService.findAllStepTemplateByWorkflow(workflowTemplateId);

    if (stepsTemplate.isEmpty()) {
      // Autonomous (keep-alive) runs provision an EMPTY workflow and let the AI orchestrator author
      // steps incrementally. Ending it here would kill the run at launch, before a single step is
      // built - the exact "attack path is empty / agent fell back to atomic tests" failure. Park it
      // in RUN instead; the orchestrator's next attack-path/steps + attack-path/evaluate call will
      // find it live and ready the new template.
      if (workflowRun.isKeepAlive()) {
        log.info(
            "[Chaining] Autonomous workflow {} has no step template yet; keeping it alive (RUN) "
                + "awaiting the orchestrator.",
            workflowRun.getId());
        return workflowRun;
      }
      log.info(
          "[Chaining] No step template for workflow template {}. End running {}",
          workflowTemplateId,
          workflowRun.getId());
      workflowRun.setStatus(WorkflowStatus.END);
      return workflowRun;
    }

    // At least one template generated one or more ready execution steps.
    boolean hasActiveSteps = stepService.countActiveSteps(workflowRun.getId()) > 0;
    int pendingCount = 0;

    for (Step step : stepsTemplate) {
      List<Step> stepReadys = stepService.createReadySteps(step, workflowRun, null, pendingCount);
      if (!stepReadys.isEmpty()) {
        hasActiveSteps = true;
        pendingCount += stepReadys.size();
        stepService.enqueueReadySteps(stepReadys, workflowRun);
      }
    }

    // If none step TEMPLATE with valid conditions && no step template delayed update workflow with
    // status END - unless this is an autonomous keep-alive workflow, which must stay parked in RUN
    // between decision cycles so the orchestrator can keep appending chained steps to a live run.
    if (!hasActiveSteps
        && !workflowRun.isKeepAlive()
        && stepDelayQueueService.findAllByWorkflowRun(workflowRun).isEmpty()) {
      workflowRun.setStatus(WorkflowStatus.END);
    }

    return workflowRun;
  }

  // -- Autonomous authoring facade --

  /**
   * Marks the scenario's TEMPLATE workflow as keep-alive and disables its timeout, so an autonomous
   * (AI-driven) run built on top of it survives an empty launch and long idle gaps between decision
   * cycles. The flag propagates to the simulation TEMPLATE and RUN workflows through the standard
   * copy chain at launch, so this must be called BEFORE {@link
   * #startWorkflowByScenarioIdAndSimulation}.
   *
   * @param scenarioId the autonomous scenario whose workflow should be kept alive
   */
  @Transactional(rollbackFor = Exception.class)
  public void markScenarioWorkflowKeepAlive(String scenarioId) throws ChainingException {
    Workflow template =
        findWorkflowTemplateByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Scenario ID: " + scenarioId));
    if (!template.isKeepAlive() || template.isTimeoutEnabled()) {
      template.setKeepAlive(true);
      // A long-lived incremental build must not be force-ended by WorkflowTimeoutJob.
      template.setTimeoutEnabled(false);
      workflowRepository.save(template);
    }
  }

  /**
   * Appends a chained inject step to a live autonomous run and (optionally) makes it depend on a
   * previously authored step, then returns the created step template id so the orchestrator can
   * chain the next step onto it.
   *
   * <p>The step template is created on the simulation-level TEMPLATE workflow that the RUN workflow
   * links to - the only place the engine re-reads templates from on re-evaluation. When {@code
   * parentStepTemplateId} is provided, a {@code DEPEND_ON} condition is attached so the new step
   * only readies once the parent step template has executed at least once in the run, giving the
   * attack-path map its kill-chain ordering. A root step (no parent) has no condition and readies
   * immediately against the run's scope on the next evaluation.
   *
   * @param simulationId the run's live simulation
   * @param injectInput the inject to wrap as a chained step
   * @param parentStepTemplateId optional step template id this step depends on (null for a root)
   * @return the id of the created step template
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStep(
      String simulationId, InjectInput injectInput, String parentStepTemplateId)
      throws ChainingException {
    Workflow simulationTemplate =
        findWorkflowTemplateBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Simulation ID: " + simulationId));

    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    if (hasText(parentStepTemplateId)) {
      ConditionCreateInput dependOn =
          ConditionCreateInput.builder()
              .type(ConditionType.DEPEND_ON)
              .value(parentStepTemplateId)
              .build();
      stepInput.setConditions(List.of(dependOn));
    }

    Step created = stepService.createStepTemplate(simulationTemplate, stepInput);
    return created.getId();
  }

  /**
   * Re-evaluates the RUN workflow(s) of a simulation so newly authored step templates ready and
   * execute now instead of waiting for an in-flight step to complete. This is the explicit "run the
   * pending steps" trigger the autonomous orchestrator calls after appending steps; without it a
   * keep-alive workflow with no active step would never re-scan its templates.
   *
   * @param simulationId the run's live simulation
   */
  @Transactional(rollbackFor = Exception.class)
  public void triggerEvaluation(String simulationId) throws ChainingException {
    List<Workflow> runs = findWorkflowRunBySimulationId(simulationId);
    for (Workflow run : runs) {
      Workflow evaluated = evaluateWorkflowProgress(run);
      saveWorkflowRun(evaluated);
    }
  }
}
