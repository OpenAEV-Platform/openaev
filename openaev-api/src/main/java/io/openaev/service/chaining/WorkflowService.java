package io.openaev.service.chaining;

import static org.springframework.util.StringUtils.hasText;

import com.google.gson.Gson;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.ScopeVariableInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.rest.exception.AlreadyExistingException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowService {

  public static final long DEFAULT_TIMEOUT_SECONDS = 3600L;

  static final String DUPLICATE_SCOPE_VARIABLE_MESSAGE =
      "A variable with this key and type already exists. Please change the name or the type.";

  /** Unique constraint on (key, type, workflow) */
  static final String UK_SCOPE_VARIABLE_KEY_TYPE_WORKFLOW = "uk_scope_variable_key_type_workflow";

  private static final Gson GSON = new Gson();

  private final StepService stepService;
  private final ConditionService conditionService;
  private final WorkflowStateService workflowStateService;
  private final StepDelayQueueService stepDelayQueueService;
  private final ScopeSnapshotService scopeSnapshotService;
  private final ScopeService scopeService;

  private final WorkflowRepository workflowRepository;
  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;
  private final ScopeVariableRepository scopeVariableRepository;
  private final AssetRepository assetRepository;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final WorkflowEndService workflowEndService;

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
    Workflow template = getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);
    // A launched simulation is read from its RUN, which carries the frozen snapshots; draft
    // simulations and scenarios keep reading the (live-resolved) template. See ADR-006.
    Workflow source = resolveConfigurationSource(template);
    Hibernate.initialize(source.getWorkflowScopeRules());
    Hibernate.initialize(source.getWorkflowScopeVariables());
    return source;
  }

  /**
   * Returns the workflow whose scope rules should be displayed: the RUN (frozen snapshots) for a
   * launched simulation, otherwise the TEMPLATE (draft simulation or scenario, resolved live).
   */
  private Workflow resolveConfigurationSource(Workflow template) {
    Exercise simulation = template.getSimulation();
    if (simulation == null || ExerciseStatus.SCHEDULED.equals(simulation.getStatus())) {
      return template;
    }
    // Latest RUN: a simulation may own several RUN rows across reset/relaunch cycles. See ADR-006.
    return workflowRepository
        .findFirstBySimulation_IdAndStatusInOrderByWorkflowCreatedAtDesc(
            simulation.getId(),
            List.of(WorkflowStatus.RUN, WorkflowStatus.END, WorkflowStatus.STOP))
        .orElse(template);
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
    ConfigurationChange change = applyConfigurationInput(input, workflow);
    if (change.changed()) {
      boolean workflowExecutedNotEmpty = !workflow.getWorkflowsExecuted().isEmpty();
      workflow.setEdited(workflowExecutedNotEmpty);
      saveConfiguration(workflow);
    }
    if (change.scopeRulesChanged()) {
      realignTemplateActionTargets(workflow);
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
      if (applyConfigurationInput(input, run).changed()) {
        workflowRepository.save(run);
      }
    }
    return runs;
  }

  /**
   * Writes ALLOWLIST scope rules onto a scenario's TEMPLATE workflow and its RUN workflow(s)
   * without touching the rest of the configuration (timeout / rate-limit / keep-alive / denylist),
   * so an autonomous run can persist its resolved scope onto the workflow it drives. Unlike {@link
   * #updateWorkflowConfiguration}, this never resets the other config fields to their input
   * defaults - it only writes the allow-list, which is what seeding / setting a scope requires.
   *
   * <p>When {@code replaceExisting} is {@code true} the current ALLOWLIST rules are cleared first
   * (used when the orchestrator SETS a freshly resolved scope); when {@code false} the rules are
   * appended idempotently (used to seed a preselected scope at provisioning). Denylist rules are
   * always preserved. An empty input with {@code replaceExisting=false} is a no-op.
   *
   * @param scenarioId the scenario whose TEMPLATE workflow should carry the scope
   * @param simulationId the simulation whose RUN workflow(s) should carry the scope
   * @param allowlistRules the allow-list rules to write (each with source + value set)
   * @param replaceExisting whether to replace the current allow-list or append to it
   */
  @Transactional(rollbackFor = Exception.class)
  public void writeAllowlistScope(
      String scenarioId,
      String simulationId,
      List<WorkflowScopeRuleInput> allowlistRules,
      boolean replaceExisting) {
    doWriteAllowlistScope(scenarioId, simulationId, allowlistRules, replaceExisting);
  }

  /**
   * Transaction-isolated variant of {@link #writeAllowlistScope} for the autonomous orchestrator's
   * scope callback. Runs in its OWN transaction ({@link Propagation#REQUIRES_NEW}) so that a
   * failure while mirroring the resolved scope onto the scenario template / live simulation
   * workflow(s) rolls back only this mirror and can NEVER mark the caller's transaction
   * rollback-only. The caller records the resolved scope on the run authoritatively first and
   * treats this workflow mirror as a secondary projection - a mirror failure must not fail (500)
   * the callback and stall the run. See {@code AutonomousRunService#setRunScope}.
   *
   * <p>Both public entry points delegate to the same private, non-transactional body: a same-class
   * call to the {@code @Transactional} sibling would bypass the Spring proxy (see {@code
   * TenantBackgroundTransactionArchTest#no_transactional_self_invocation}).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void writeAllowlistScopeIsolated(
      String scenarioId,
      String simulationId,
      List<WorkflowScopeRuleInput> allowlistRules,
      boolean replaceExisting) {
    doWriteAllowlistScope(scenarioId, simulationId, allowlistRules, replaceExisting);
  }

  private void doWriteAllowlistScope(
      String scenarioId,
      String simulationId,
      List<WorkflowScopeRuleInput> allowlistRules,
      boolean replaceExisting) {
    if (!replaceExisting && (allowlistRules == null || allowlistRules.isEmpty())) {
      return;
    }
    List<WorkflowScopeRuleInput> rules = allowlistRules != null ? allowlistRules : List.of();
    if (hasText(scenarioId)) {
      try {
        findWorkflowTemplateByScenarioId(scenarioId)
            .ifPresent(
                w -> {
                  if (writeAllowlistRules(w, rules, replaceExisting)) {
                    realignTemplateActionTargets(w);
                  }
                });
      } catch (ChainingException e) {
        log.warn(
            "[Chaining] Could not write scope on scenario {} template workflow", scenarioId, e);
      }
    }
    if (hasText(simulationId)) {
      findWorkflowRunBySimulationId(simulationId)
          .forEach(w -> writeAllowlistRules(w, rules, replaceExisting));
    }
  }

  /**
   * Removes ghost ASSET / ASSET_GROUP rules (referencing a deleted entity) from a simulation's
   * TEMPLATE workflow, so a reset simulation does not relaunch with unresolvable scope entries.
   * Only allow/deny rules are considered; the referenced entity is probed with the same current
   * resolution used by the snapshot diff (null = no longer exists).
   */
  @Transactional(rollbackFor = Exception.class)
  public void cleanScopeRulesSimulation(@NotBlank String simulationId) {
    Workflow template =
        workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.TEMPLATE);
    if (template != null) {

      List<WorkflowScopeRule> rulesToRemove = new ArrayList<>();
      for (WorkflowScopeRule rule : template.getWorkflowScopeRules()) {
        if (rule.getSelectedMode() != null
            && (ScopeRuleSource.ASSET.equals(rule.getRuleSource())
                || ScopeRuleSource.ASSET_GROUP.equals(rule.getRuleSource()))) {
          ScopeRuleSnapshot current = scopeSnapshotService.buildCurrentSnapshot(rule);
          if (current == null) rulesToRemove.add(rule);
        }
      }
      if (!rulesToRemove.isEmpty()) {
        template.getWorkflowScopeRules().removeAll(rulesToRemove);
        workflowRepository.save(template);
        realignTemplateActionTargets(template);
      }
    }
  }

  /**
   * Seeds a full scope definition (ALLOWLIST and/or DENYLIST rules, any source) onto a run's
   * scenario template and live simulation workflows. Used at autonomous-run creation to mirror the
   * scope the operator picked in the launch stepper onto the auto-provisioned workflow, so it is
   * enforced and shown in the Scope tab exactly like a manually chained scenario. Rules are
   * appended and de-duplicated by (mode, source, value); an empty list is a no-op. Unlike {@link
   * #writeAllowlistScope} this never clears existing rules and is mode-agnostic.
   */
  public void writeScopeRules(
      String scenarioId, String simulationId, List<WorkflowScopeRuleInput> rules) {
    if (rules == null || rules.isEmpty()) {
      return;
    }
    if (hasText(scenarioId)) {
      try {
        findWorkflowTemplateByScenarioId(scenarioId)
            .ifPresent(
                w -> {
                  if (appendScopeRules(w, rules)) {
                    realignTemplateActionTargets(w);
                  }
                });
      } catch (ChainingException e) {
        log.warn("[Chaining] Could not seed scope on scenario {} template workflow", scenarioId, e);
      }
    }
    if (hasText(simulationId)) {
      findWorkflowRunBySimulationId(simulationId).forEach(w -> appendScopeRules(w, rules));
    }
  }

  /**
   * Whether a simulation's RUN workflow(s) carry at least one ALLOWLIST scope rule, i.e. an actual
   * attack perimeter. The autonomous launch uses it to detect a run that has no scope at all (the
   * launch stepper left both lists empty AND the scenario carried none), so it can ask the operator
   * for one instead of driving an unscoped run.
   *
   * @param simulationId the ID of the simulation
   * @return true when any RUN workflow of the simulation has a non-empty allow-list
   */
  @Transactional(readOnly = true)
  public boolean hasAllowlistScopeForSimulation(String simulationId) {
    if (!hasText(simulationId)) {
      return false;
    }
    return findWorkflowRunBySimulationId(simulationId).stream()
        .anyMatch(workflow -> !workflow.getAllowlist().isEmpty());
  }

  private boolean appendScopeRules(Workflow workflow, List<WorkflowScopeRuleInput> ruleInputs) {
    List<WorkflowScopeRule> existing = workflow.getWorkflowScopeRules();
    Set<String> existingKeys =
        existing.stream()
            .map(r -> r.getSelectedMode() + "|" + r.getRuleSource() + "|" + r.getRuleValue())
            .collect(Collectors.toSet());
    boolean changed = false;
    for (WorkflowScopeRuleInput in : ruleInputs) {
      if (in == null || in.getSelectedMode() == null || in.getRuleSource() == null) {
        continue;
      }
      String key = in.getSelectedMode() + "|" + in.getRuleSource() + "|" + in.getRuleValue();
      if (existingKeys.add(key)) {
        existing.add(buildScopeRule(in, workflow));
        changed = true;
      }
    }
    if (changed) {
      workflowRepository.save(workflow);
    }
    return changed;
  }

  private boolean writeAllowlistRules(
      Workflow workflow, List<WorkflowScopeRuleInput> ruleInputs, boolean replaceExisting) {
    List<WorkflowScopeRule> existing = workflow.getWorkflowScopeRules();
    boolean changed = false;
    if (replaceExisting) {
      changed = existing.removeIf(r -> ScopeRuleSelectedMode.ALLOWLIST.equals(r.getSelectedMode()));
    }
    Set<String> existingKeys =
        existing.stream()
            .map(r -> r.getSelectedMode() + "|" + r.getRuleSource() + "|" + r.getRuleValue())
            .collect(Collectors.toSet());
    for (WorkflowScopeRuleInput in : ruleInputs) {
      String key =
          ScopeRuleSelectedMode.ALLOWLIST + "|" + in.getRuleSource() + "|" + in.getRuleValue();
      if (existingKeys.add(key)) {
        existing.add(buildScopeRule(in, workflow));
        changed = true;
      }
    }
    if (changed) {
      workflowRepository.save(workflow);
    }
    return changed;
  }

  /**
   * Re-aligns all asset-centric step templates with the workflow's current scope assets.
   *
   * <p>ScopeService resolves from persisted rules, so pending workflow updates must be flushed
   * first.
   */
  private void realignTemplateActionTargets(Workflow workflow) {
    if (workflow == null || !WorkflowStatus.TEMPLATE.equals(workflow.getStatus())) {
      return;
    }
    workflowRepository.flush();
    List<String> scopedAssetIds =
        Optional.ofNullable(scopeService.getValidAssets(workflow.getId()))
            .orElse(List.of())
            .stream()
            .map(Asset::getId)
            .toList();
    stepService.syncScopeAssetsOnStepTemplates(workflow, scopedAssetIds);
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
    // Freeze the launch snapshot only on the RUN copy (never on TEMPLATE copies). See ADR-006.
    Exercise simulation = workflowTemplateFrom.getSimulation();
    if (simulation != null && simulation.getTenant() != null) {
      scopeSnapshotService.freezeLaunch(workflowRunTo, simulation.getTenant().getId());
    }
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

  /**
   * Persists the configuration and translates the scope-variable uniqueness breach reported by the
   * database into an actionable business error.
   *
   * <p>The write is flushed explicitly: with the default deferred flush the {@code
   * uk_scope_variable_key_type_workflow} violation would only be raised at commit, outside this
   * method, and would reach the client as a raw integrity violation naming the constraint - which
   * is neither actionable nor safe to display.
   *
   * @param workflow the workflow carrying the new configuration
   * @throws AlreadyExistingException if two scope variables share the same key and type
   */
  private void saveConfiguration(Workflow workflow) {
    try {
      workflowRepository.save(workflow);
      workflowRepository.flush();
    } catch (DataIntegrityViolationException e) {
      if (e.getCause() instanceof ConstraintViolationException violation
          && UK_SCOPE_VARIABLE_KEY_TYPE_WORKFLOW.equalsIgnoreCase(violation.getConstraintName())) {
        throw new AlreadyExistingException(DUPLICATE_SCOPE_VARIABLE_MESSAGE);
      }
      throw e;
    }
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

  public boolean existsBySimulationId(String simulationId) {
    return this.workflowRepository.existsBySimulationId(simulationId);
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
   * Clears the ENTIRE logic map of a scenario's chaining workflow - every step template AND every
   * condition (event/trigger trees included) - keeping the (now empty) workflow row itself.
   *
   * <p>The deterministic reset used before an autonomous run is restarted, a plan is promoted to a
   * real run, or the AI builder re-plans (rebuilds) the scenario. The scenario workflow doubles as
   * the seed a fresh simulation is copied from, so anything left on it re-seeds that simulation -
   * leaving the Logic tab and the attack-path map populated right after a reset the operator
   * expected to wipe them. Two past regressions shaped this method: (1) the reset once only removed
   * the steps named in the run's best-effort {@code stepMirror}, so a single un-mirrored step
   * survived and duplicated the attack path; (2) deleting the steps alone leaves the event/trigger
   * condition trees behind, because per-step condition cleanup only deletes a condition once it has
   * no more step links AND no children - a root condition with children (exactly what an authored
   * event is) always survived as an orphan on the logic map. An AI-rebuilt scenario starts from an
   * empty logic map, so clearing everything is both safe and the behaviour the operator wants:
   * "build (or launch for real) fully recreates everything". The workflow row is preserved so the
   * simulation is still recognised as chaining and keep-alive holds.
   *
   * @param scenarioId the autonomous run's scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteAllScenarioSteps(String scenarioId) {
    if (!hasText(scenarioId)) {
      return;
    }
    Optional<Workflow> template;
    try {
      template = findWorkflowTemplateByScenarioId(scenarioId);
    } catch (ChainingException e) {
      log.warn(
          "Failed to resolve scenario {} workflow for a full step reset: {}",
          scenarioId,
          e.getMessage());
      return;
    }
    if (template.isEmpty()) {
      return;
    }
    for (Step step : stepService.findAllStepTemplateByWorkflow(template.get().getId())) {
      try {
        stepService.deleteStepTemplate(step.getId());
      } catch (Exception e) {
        log.warn(
            "Failed to delete scenario step {} during autonomous reset: {}",
            step.getId(),
            e.getMessage());
      }
    }
    // Sweep the conditions AFTER the steps: per-step cleanup only removed conditions left with no
    // step links and no children, so event/trigger trees survive it by construction.
    try {
      conditionService.deleteAllConditionsByWorkflowId(template.get().getId());
    } catch (Exception e) {
      log.warn(
          "Failed to delete scenario {} workflow conditions during autonomous reset: {}",
          scenarioId,
          e.getMessage());
    }
  }

  /**
   * Turns OFF keep-alive on a scenario's chaining workflow template (and re-enables the normal
   * timeout), making it behave like a hand-built chained scenario again.
   *
   * <p>Keep-alive (and {@code timeoutEnabled = false}) now lives on the launched SIMULATION, not
   * the scenario template ({@link #markSimulationWorkflowKeepAlive}), so a fresh autonomous
   * scenario no longer carries it. This method remains the safe healer for LEGACY scenarios whose
   * template was marked keep-alive before that change: taking such a scenario manual would
   * otherwise leave a launched run hanging open forever (no orchestrator ever appends steps or ends
   * it). Called when dropping the {@code autonomous_runs} row (in-place conversion) or copying an
   * autonomous workflow into a fresh manual scenario (duplicate). A no-op when the scenario has no
   * workflow template, or when the template is already clean.
   *
   * @param scenarioId the scenario whose workflow should stop keeping itself alive
   */
  @Transactional(rollbackFor = Exception.class)
  public void clearScenarioWorkflowKeepAlive(String scenarioId) {
    if (!hasText(scenarioId)) {
      return;
    }
    Optional<Workflow> template;
    try {
      template = findWorkflowTemplateByScenarioId(scenarioId);
    } catch (ChainingException e) {
      log.warn(
          "Failed to resolve scenario {} workflow to clear keep-alive: {}",
          scenarioId,
          e.getMessage());
      return;
    }
    if (template.isEmpty()) {
      return;
    }
    Workflow workflow = template.get();
    if (workflow.isKeepAlive() || !workflow.isTimeoutEnabled()) {
      // Restore a run-and-end manual chained workflow: timeout watchdog back on.
      workflow.setKeepAlive(false);
      workflow.setTimeoutEnabled(true);
      workflowRepository.save(workflow);
    }
  }

  /**
   * Copies an existing scenario's chaining workflow TEMPLATE (configuration, scope rules and every
   * step template) onto {@code scenarioTo} as a plain <b>manual</b> chained workflow: keep-alive is
   * forced OFF so the copy runs-and-ends like a hand-built scenario rather than parking forever for
   * an orchestrator. Used to duplicate an autonomous scenario into a fresh, editable manual chained
   * scenario without touching the original AI run. A no-op (returns null) when the source has no
   * workflow template.
   *
   * @param scenarioIdFrom source scenario whose workflow (steps + config) is copied
   * @param scenarioTo already-persisted destination scenario to attach the copied workflow to
   * @return the new manual workflow template, or null if the source has no workflow
   */
  @Transactional(rollbackFor = Exception.class)
  public Workflow copyScenarioChainingWorkflowAsManual(
      @NotBlank String scenarioIdFrom, @NotBlank Scenario scenarioTo) throws ChainingException {
    Optional<Workflow> sourceOpt = findWorkflowTemplateByScenarioId(scenarioIdFrom);
    if (sourceOpt.isEmpty()) {
      return null;
    }
    Workflow source = sourceOpt.get();
    Workflow copy = copyWorkflowTemplateToScenario(source, scenarioTo);
    // A duplicated autonomous workflow must never inherit the "park forever" contract
    // (keepAlive on, timeout watchdog off) that a legacy autonomous scenario template may carry.
    copy.setKeepAlive(false);
    copy.setTimeoutEnabled(true);
    copy = workflowRepository.save(copy);
    stepService.copyStepTemplate(source, copy);
    return copy;
  }

  @Transactional(rollbackFor = Exception.class)
  public void cancelSimulationEndWorkflowRun(List<Workflow> workflows) {
    List<Step> stepsToUpdate = new ArrayList<>();
    List<String> injectsIds = new ArrayList<>();
    workflows.forEach(
        workflow -> {
          // Workflow -> END transition (also freezes the end scope snapshot - ADR-006):
          endWorkflow(workflow, WorkflowEndService.WORKFLOW_END_CAUSE.CANCELED);

          // Step delay queue -> DELETE
          stepDelayQueueService.deleteAllByWorkflowRun(workflow);

          // Steps active -> END  active and get inject ids for remove asset agent jobs
          List<Step> steps = stepService.findAllStepActiveByWorkflowRunId(workflow.getId());
          steps.forEach(
              step -> {
                String injectId =
                    step.getData() != null
                        ? StepService.getField(step.getData(), "inject_id")
                        : null;
                if (injectId != null) injectsIds.add(injectId);
                step.setStatus(StepStatus.END);
              });

          stepsToUpdate.addAll(steps);

          // Workflow States -> DELETE  (only use for execution)
          deleteWorkflowStatesBySimulationId(workflow.getSimulation().getId());
        });

    // Asset agent jobs -> DELETE all by inject id
    deleteAllAssetAgentJobs(injectsIds, TenantContext.getCurrentTenant());

    stepService.saveSteps(stepsToUpdate);
  }

  private void deleteAllAssetAgentJobs(List<String> injectsIds, String tenantId) {
    if (CollectionUtils.isEmpty(injectsIds)) return;
    assetAgentJobRepository.deleteAllByInjectIdsAndTenantId(injectsIds, tenantId);
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
   * Copies all fields from {@code input} onto {@code workflow} and reports what actually changed.
   */
  private ConfigurationChange applyConfigurationInput(
      WorkflowConfigurationInput input, Workflow workflow) {
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

    return new ConfigurationChange(rulesChanged || variablesChanged || changed, rulesChanged);
  }

  /**
   * Outcome of a configuration update: whether anything changed at all, and whether the scope rules
   * specifically changed (which requires realigning the step templates' asset perimeter).
   */
  private record ConfigurationChange(boolean changed, boolean scopeRulesChanged) {}

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
    // SECURITY_PLATFORM rows are engine-written snapshot rows (frozen at launch, see ADR-006),
    // never a legitimate configuration input, so the reconciliation below must neither remove nor
    // create nor mutate them: a live-steering update (updateRunWorkflowConfiguration) or an
    // emptied scope would otherwise silently destroy the frozen security-platform photos of a RUN
    // workflow, and a crafted input could mint or overwrite protected rows.
    if (CollectionUtils.isEmpty(ruleInputs)) {
      return existing.removeIf(r -> r.getRuleSource() != ScopeRuleSource.SECURITY_PLATFORM);
    }

    List<WorkflowScopeRuleInput> deduplicated =
        deduplicateRules(
            ruleInputs.stream()
                .filter(r -> r.getRuleSource() != ScopeRuleSource.SECURITY_PLATFORM)
                .toList());

    Set<String> inputIds =
        deduplicated.stream()
            .map(WorkflowScopeRuleInput::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<String, WorkflowScopeRule> existingById =
        existing.stream().collect(Collectors.toMap(WorkflowScopeRule::getId, r -> r));

    boolean changed =
        existing.removeIf(
            r ->
                r.getRuleSource() != ScopeRuleSource.SECURITY_PLATFORM
                    && !inputIds.contains(r.getId()));

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

    // Update existing rules that have changed (never an engine-written SECURITY_PLATFORM row,
    // even when an input smuggles its id).
    Set<String> processedIds = new HashSet<>();
    for (WorkflowScopeRuleInput ruleInput : deduplicated) {
      String ruleId = ruleInput.getId();
      if (ruleId != null && processedIds.add(ruleId)) {
        WorkflowScopeRule existingRule = existingById.get(ruleId);
        if (existingRule != null
            && existingRule.getRuleSource() != ScopeRuleSource.SECURITY_PLATFORM
            && hasRuleChanged(existingRule, ruleInput)) {
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

    // KPI. Record Source Usage (CSV vs Manual only - ignore asset-based sources)
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
    // Refresh the label snapshot when the referenced asset / group still resolves (keeps up with
    // renames), but never wipe a previously captured label: if the asset was deleted (resolution
    // returns null), an unrelated field change (e.g. toggling allow/deny mode) must keep the last
    // known name so the deleted-asset UX fallback still works.
    String resolvedLabel = resolveValueLabel(input);
    if (resolvedLabel != null) {
      existing.setRuleValueLabel(resolvedLabel);
    }
  }

  private WorkflowScopeRule buildScopeRule(WorkflowScopeRuleInput input, Workflow workflow) {
    return WorkflowScopeRule.builder()
        .selectedMode(input.getSelectedMode())
        .ruleSource(input.getRuleSource())
        .ruleValue(input.getRuleValue())
        .ruleValueLabel(resolveValueLabel(input))
        .valueType(detectValueType(input))
        .workflow(workflow)
        .build();
  }

  /**
   * Snapshots the display name of the entity referenced by an ASSET / ASSET_GROUP / TEAM / PLAYER
   * scope rule (asset / group name, team name, or player name-or-email), so a past run's scope
   * stays readable after the referenced object is deleted.
   *
   * <p>The lookup is tenant-scoped on purpose: Hibernate's {@code tenantFilter} does not apply to
   * primary-key loads, so a plain {@code findById} on a user-supplied id could snapshot (and later
   * expose) another tenant's name. Ids that do not resolve within the caller's tenant - or MANUAL /
   * CSV rules - stay {@code null}.
   */
  private String resolveValueLabel(WorkflowScopeRuleInput input) {
    if (input.getRuleSource() == null || !hasText(input.getRuleValue())) {
      return null;
    }
    String tenantId = TenantContext.getCurrentTenant();
    if (!hasText(tenantId)) {
      return null;
    }
    return switch (input.getRuleSource()) {
      case ASSET ->
          assetRepository
              .findByIdAndTenantId(input.getRuleValue(), tenantId)
              .map(Asset::getName)
              .orElse(null);
      case ASSET_GROUP ->
          assetGroupRepository
              .findByIdAndTenantId(input.getRuleValue(), tenantId)
              .map(AssetGroup::getName)
              .orElse(null);
      case TEAM ->
          teamRepository
              .findByIdAndTenantId(input.getRuleValue(), tenantId)
              .map(Team::getName)
              .orElse(null);
      case PLAYER ->
          userRepository.findAllByIdInAndTenantId(List.of(input.getRuleValue()), tenantId).stream()
              .findFirst()
              .map(User::getNameOrEmail)
              .orElse(null);
      default -> null;
    };
  }

  private ScopeRuleValueType detectValueType(WorkflowScopeRuleInput input) {
    if (input.getRuleSource() != null) {
      return switch (input.getRuleSource()) {
        case ASSET -> ScopeRuleValueType.ASSET_ID;
        case ASSET_GROUP -> ScopeRuleValueType.ASSET_GROUP_ID;
        case TEAM -> ScopeRuleValueType.TEAM_ID;
        case PLAYER -> ScopeRuleValueType.PLAYER_ID;
        // Engine-written rows only (rejected from configuration inputs by applyScopeRules); the
        // explicit mapping keeps internal writers from ever mislabeling one as IP / domain.
        case SECURITY_PLATFORM -> ScopeRuleValueType.SECURITY_PLATFORM_ID;
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
   * Provisions ONLY the simulation-scoped TEMPLATE workflow (with its step templates) from the
   * scenario template, WITHOUT creating or starting a RUN workflow. This is the dry-run / plan-mode
   * counterpart of {@link #startWorkflowByScenarioIdAndSimulation}: a plan must have a real
   * simulation TEMPLATE workflow to author steps into (otherwise every author call fails with
   * "Workflow (TEMPLATE) not found") and to mark the simulation as chaining (so the auto-closing
   * job does not finish the empty simulation out from under the orchestrator). Because no RUN
   * workflow is created, the chaining engine has nothing to ready or dispatch, so the plan is
   * designed without ever executing an inject.
   *
   * @param scenarioId the autonomous scenario whose TEMPLATE workflow is the design source
   * @param simulation the plan simulation to attach the TEMPLATE workflow to
   * @return the created simulation TEMPLATE workflow
   */
  @Transactional(rollbackFor = Exception.class)
  public Workflow provisionSimulationTemplateWorkflow(String scenarioId, Exercise simulation)
      throws ChainingException {
    Workflow workflowTemplateScenario =
        findWorkflowTemplateByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Scenario ID: " + scenarioId));

    Workflow workflowTemplateSimulation =
        saveWorkflowRun(copyWorkflowTemplateToSimulation(workflowTemplateScenario, simulation));
    stepService.copyStepTemplate(workflowTemplateScenario, workflowTemplateSimulation);
    return workflowTemplateSimulation;
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
    return workflowEndService.findAllExpiredRunWorkflows();
  }

  /**
   * Sets the workflow status to END and persists it.
   *
   * @param workflowRun the running workflow to end
   */
  public void endWorkflow(Workflow workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE cause) {
    workflowEndService.endWorkflow(workflowRun, cause);
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

    // Guard: ignore if workflow run has already ended (e.g. timeout). The early return is load-
    // bearing: without it an ended workflow still fell through to
    // createReadySteps/enqueueReadySteps
    // below, re-readying and re-enqueuing steps on a terminated run (churn, and a possible re-fire
    // after a timeout settle).
    if (this.isWorkflowEnded(workflowRun.getId())) {
      log.info(
          "[Chaining] Ignoring evaluation because workflow run {} has ended.", workflowRun.getId());
      return workflowRun;
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
      workflowEndService.markWorkflowEnded(
          workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE.NO_MORE_PROGRESS);
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
      workflowEndService.markWorkflowEnded(
          workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE.NO_MORE_PROGRESS);
    }

    return workflowRun;
  }

  // -- Autonomous authoring facade --

  /**
   * Marks a launched SIMULATION's chaining workflows (its TEMPLATE and every RUN) as keep-alive and
   * disables their timeout, so an autonomous simulation parks in RUN awaiting the orchestrator
   * between decision cycles instead of ending when it runs out of ready steps, and {@code
   * WorkflowTimeoutJob} never force-ends it (the autonomous run's own OpenAEV-owned deadline, 24h
   * by default, hard-stops a live run; a plan substrate is untimed).
   *
   * <p>Applied to the SIMULATION - never to the reusable scenario TEMPLATE - so building or
   * launching an autonomous run never mutates the scenario's own "Simulation time out" config: a
   * chained scenario keeps its default 1h expiration (editable in the Scope tab), and the very same
   * scenario can be relaunched in normal mode and run-and-end normally. A no-op when the simulation
   * has no workflow yet.
   *
   * <p>Must be called at launch time, in the SAME transaction as the launch, on a freshly
   * provisioned simulation. The initial evaluation inside {@code startWorkflow} ENDs an empty
   * non-keep-alive run on the spot - and an autonomous run always launches empty - so the freshly
   * ended run is invisible to the RUN-status finder. This method therefore also picks up the
   * simulation's END runs and restores them to RUN: on a fresh simulation the only possible END run
   * is the one the launch itself just ended (nothing ever executed), so the restore can never
   * resurrect a legitimately finished run.
   *
   * @param simulationId the launched simulation whose workflows should keep themselves alive
   */
  @Transactional(rollbackFor = Exception.class)
  public void markSimulationWorkflowKeepAlive(String simulationId) {
    if (!hasText(simulationId)) {
      return;
    }
    List<Workflow> workflows = new ArrayList<>();
    findWorkflowTemplateBySimulationId(simulationId).ifPresent(workflows::add);
    workflows.addAll(findWorkflowRunBySimulationId(simulationId));
    // Recover the empty run the launch evaluation just ended (see javadoc): parked back in RUN, it
    // awaits the orchestrator's first authored step instead of staying terminally closed.
    workflows.addAll(
        workflowRepository.findAllBySimulation_IdAndStatus(simulationId, WorkflowStatus.END));
    for (Workflow workflow : workflows) {
      boolean dirty = false;
      if (workflow.getStatus() == WorkflowStatus.END) {
        workflow.setStatus(WorkflowStatus.RUN);
        // The launch evaluation provisionally ended this empty run and froze its end scope
        // snapshot; reopening it must clear that photo or the live autonomous run would
        // misclassify every later drift as after-execution. See ADR-006.
        scopeSnapshotService.clearEnd(workflow);
        dirty = true;
      }
      if (!workflow.isKeepAlive() || workflow.isTimeoutEnabled()) {
        workflow.setKeepAlive(true);
        workflow.setTimeoutEnabled(false);
        dirty = true;
      }
      if (dirty) {
        workflowRepository.save(workflow);
      }
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
    return doAppendChainedStep(
        simulationId, injectInput, parentStepTemplateId, List.of(), List.of());
  }

  /**
   * Finding-driven overload of {@link #appendChainedStep(String, InjectInput, String)}. In addition
   * to the optional {@code DEPEND_ON} parent, the step carries {@code triggerConditions} - a
   * finding-trigger filter tree and/or {@code MAPPER} bindings - so it readies off findings and
   * consumes their values (the way a hand-built chained scenario works). A step with no trigger and
   * no parent is a SEED that readies immediately against the run scope. The engine already persists
   * arbitrary condition trees, so this simply merges the provided conditions with the DEPEND_ON.
   *
   * @param triggerConditions finding-trigger + mapper conditions (empty for a seed /
   *     DEPEND_ON-only)
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStep(
      String simulationId,
      InjectInput injectInput,
      String parentStepTemplateId,
      List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    return doAppendChainedStep(
        simulationId, injectInput, parentStepTemplateId, triggerConditions, List.of());
  }

  /**
   * Existing-event overload of {@link #appendChainedStep(String, InjectInput, String, List)}. In
   * addition to any inline {@code triggerConditions} (typically just MAPPER bindings), the step is
   * LINKED to one or more EXISTING event roots by id ({@code existingEventConditionIds}) instead of
   * minting a fresh finding-trigger tree - so several actions can fire off the SAME event rather
   * than each duplicating it. The engine's step-create already re-links existing condition roots
   * via {@code condition_ids}, exactly like the manual UI does. An empty list behaves like the
   * plain finding-driven overload.
   *
   * @param existingEventConditionIds ids of existing event roots (finding-trigger roots) to attach
   *     this step to; empty to create a new event from {@code triggerConditions}
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStep(
      String simulationId,
      InjectInput injectInput,
      String parentStepTemplateId,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    return doAppendChainedStep(
        simulationId,
        injectInput,
        parentStepTemplateId,
        triggerConditions,
        existingEventConditionIds);
  }

  // Shared body for the appendChainedStep overloads. Private and non-transactional on purpose: the
  // public overloads are the @Transactional entry points, and each simply widens its arguments and
  // delegates here. Delegating to a plain helper (instead of one overload self-invoking the other)
  // keeps the transactional boundary on the proxied public method - an intra-class call to a
  // @Transactional method would bypass the Spring proxy (no transaction, no tenant scope).
  private String doAppendChainedStep(
      String simulationId,
      InjectInput injectInput,
      String parentStepTemplateId,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    Workflow simulationTemplate =
        findWorkflowTemplateBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Simulation ID: " + simulationId));
    // Defence in depth: the link channel below (stepInput.conditionIds -> findConditionRootById)
    // only checks root-ness, so enforce the FULL event invariant at this service boundary - each
    // reused id must be an AND/OR finding-event root on THIS simulation's own workflow - instead of
    // trusting the caller's earlier validation. A no-op for the validated autonomous caller; it
    // closes the boundary against a cross-workflow or non-event link from any other caller.
    assertEventRootsOnWorkflow(simulationTemplate.getId(), existingEventConditionIds);

    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    List<ConditionCreateInput> conditions = new ArrayList<>();
    if (triggerConditions != null) {
      conditions.addAll(triggerConditions);
    }
    if (hasText(parentStepTemplateId)) {
      conditions.add(
          ConditionCreateInput.builder()
              .type(ConditionType.DEPEND_ON)
              .value(parentStepTemplateId)
              .build());
    }
    if (!conditions.isEmpty()) {
      stepInput.setConditions(conditions);
    }
    // Link EXISTING event roots (finding-trigger roots the orchestrator chose to reuse) by id, the
    // same condition_ids channel the manual UI uses, so multiple actions share one event instead of
    // duplicating it. Never a fresh event tree - that is what triggerConditions above is for.
    if (existingEventConditionIds != null && !existingEventConditionIds.isEmpty()) {
      stepInput.setConditionIds(existingEventConditionIds);
    }

    // Idempotent authoring: a retried/replayed orchestrator call for the SAME inject + same parent
    // reuses the existing template instead of minting a duplicate that would materialise as yet
    // another inject on the next evaluation (root cause of the duplicate-inject storm).
    Step created =
        stepService.createInjectStepTemplateIdempotent(
            simulationTemplate, stepInput, parentStepTemplateId);
    return created.getId();
  }

  /**
   * Mirrors an authored attack-path step onto a SCENARIO workflow template, so the scenario carries
   * the same attack path the orchestrator built on the simulation and can be exported/reproduced.
   *
   * <p>This is the scenario-side twin of {@link #appendChainedStep}: an autonomous run authors its
   * steps on the simulation template (the only tree the engine executes), but the run's scenario
   * template stays empty otherwise, which is why an exported autonomous scenario would carry no
   * attack path. The mirrored step never executes (nothing runs a scenario TEMPLATE), so this is
   * purely for export/display. {@code parentScenarioStepTemplateId} is the scenario twin of the
   * simulation parent (resolved by the caller from its sim->scenario mapping), so the {@code
   * DEPEND_ON} kill-chain ordering is preserved on the scenario side.
   *
   * @param scenarioId the run's scenario
   * @param injectInput the same inject that was authored on the simulation
   * @param parentScenarioStepTemplateId optional scenario step id this step depends on (null root)
   * @return the id of the created scenario step template
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStepToScenario(
      String scenarioId, InjectInput injectInput, String parentScenarioStepTemplateId)
      throws ChainingException {
    return doAppendChainedStepToScenario(
        scenarioId, injectInput, parentScenarioStepTemplateId, List.of(), List.of());
  }

  /**
   * Finding-driven overload of {@link #appendChainedStepToScenario(String, InjectInput, String)}.
   * The scenario twin carries the same finding-trigger + mapper conditions as its simulation
   * original (they are parent-independent, so they copy verbatim), keeping the exported scenario a
   * faithful reproduction of the finding-driven attack path.
   *
   * @param triggerConditions the same finding-trigger + mapper conditions authored on the
   *     simulation
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStepToScenario(
      String scenarioId,
      InjectInput injectInput,
      String parentScenarioStepTemplateId,
      List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    return doAppendChainedStepToScenario(
        scenarioId, injectInput, parentScenarioStepTemplateId, triggerConditions, List.of());
  }

  /**
   * Existing-event overload of {@link #appendChainedStepToScenario(String, InjectInput, String,
   * List)}: links the scenario step to EXISTING scenario event roots by id instead of minting a new
   * event tree, the scenario-side twin of {@link #appendChainedStep(String, InjectInput, String,
   * List, List)}. Used when the orchestrator authors a step directly onto the scenario (no
   * simulation) and reuses an event it already authored there.
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendChainedStepToScenario(
      String scenarioId,
      InjectInput injectInput,
      String parentScenarioStepTemplateId,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    return doAppendChainedStepToScenario(
        scenarioId,
        injectInput,
        parentScenarioStepTemplateId,
        triggerConditions,
        existingEventConditionIds);
  }

  /**
   * Transaction-isolated variant of {@link #appendChainedStepToScenario(String, InjectInput,
   * String, List)} for the autonomous orchestrator's step-authoring callback. Runs in its OWN
   * transaction ({@link Propagation#REQUIRES_NEW}) so a scenario-mirror failure (the scenario
   * template invisible under the callback thread's tenant, or a concurrent author racing the same
   * scenario workflow) rolls back only this twin and can NEVER mark the caller's authoring
   * transaction rollback-only. The executing simulation step is authored authoritatively first and
   * this scenario mirror is a secondary projection - a mirror failure must not fail (500) the
   * author callback and lose the executing step at commit. See {@code
   * AutonomousRunService#mirrorStepOntoScenario}.
   *
   * <p>Both public entry points delegate to the same private, non-transactional body: a same-class
   * call to a {@code @Transactional} sibling would bypass the Spring proxy (see {@code
   * TenantBackgroundTransactionArchTest#no_transactional_self_invocation}).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public String appendChainedStepToScenarioIsolated(
      String scenarioId,
      InjectInput injectInput,
      String parentScenarioStepTemplateId,
      List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    return doAppendChainedStepToScenario(
        scenarioId, injectInput, parentScenarioStepTemplateId, triggerConditions, List.of());
  }

  /**
   * Existing-event variant of {@link #appendChainedStepToScenarioIsolated(String, InjectInput,
   * String, List)}: links the mirrored scenario twin to an EXISTING scenario event root by id
   * (resolved by the caller from its sim-&gt;scenario event mapping) so the exported scenario
   * shares one event across the actions that reuse it, exactly like the executing simulation side.
   * Still REQUIRES_NEW and best-effort - the mirror must never fail the author callback.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public String appendChainedStepToScenarioIsolated(
      String scenarioId,
      InjectInput injectInput,
      String parentScenarioStepTemplateId,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    return doAppendChainedStepToScenario(
        scenarioId,
        injectInput,
        parentScenarioStepTemplateId,
        triggerConditions,
        existingEventConditionIds);
  }

  // Shared body for the appendChainedStepToScenario overloads. See doAppendChainedStep for why this
  // is a private, non-transactional helper the public @Transactional overloads delegate to.
  private String doAppendChainedStepToScenario(
      String scenarioId,
      InjectInput injectInput,
      String parentScenarioStepTemplateId,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    Workflow scenarioTemplate =
        findWorkflowTemplateByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Scenario ID: " + scenarioId));
    // Defence in depth (same as doAppendChainedStep): validate each reused id is an AND/OR
    // finding-event root on THIS scenario's own workflow before linking, so the isolated mirror
    // path - which links a recorded eventMirror twin without re-validating - can never cross-link
    // workflows on a stale or incorrect entry. A no-op for a valid twin; a stale entry throws and
    // is swallowed by the best-effort mirror exactly like a missing id already was.
    assertEventRootsOnWorkflow(scenarioTemplate.getId(), existingEventConditionIds);

    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    List<ConditionCreateInput> conditions = new ArrayList<>();
    if (triggerConditions != null) {
      conditions.addAll(triggerConditions);
    }
    if (hasText(parentScenarioStepTemplateId)) {
      conditions.add(
          ConditionCreateInput.builder()
              .type(ConditionType.DEPEND_ON)
              .value(parentScenarioStepTemplateId)
              .build());
    }
    if (!conditions.isEmpty()) {
      stepInput.setConditions(conditions);
    }
    // Link an EXISTING scenario event root by id (the scenario twin of a reused simulation event)
    // so the exported scenario shares the event instead of duplicating it, mirroring the executing
    // simulation side. Empty means a fresh event copy, the historical mirror behaviour.
    if (existingEventConditionIds != null && !existingEventConditionIds.isEmpty()) {
      stepInput.setConditionIds(existingEventConditionIds);
    }

    // Idempotent mirror: keep the scenario twin in lock-step with the (now idempotent) simulation
    // side so a replayed author call never doubles the exported attack path either.
    Step created =
        stepService.createInjectStepTemplateIdempotent(
            scenarioTemplate, stepInput, parentScenarioStepTemplateId);
    return created.getId();
  }

  /** The event-root condition types: an AND / OR node is a finding EVENT the engine fires on. */
  private static final Set<ConditionType> EVENT_ROOT_TYPES =
      EnumSet.of(ConditionType.AND, ConditionType.OR);

  /**
   * Validates that a caller-supplied {@code eventId} is an EXISTING finding-event root on the
   * simulation's template workflow, so a step can be linked to it instead of minting a duplicate
   * event. Throws {@link ChainingException} (surfaced as a 400) with a precise reason when the id
   * is unknown, is a child condition rather than a root, is not an AND/OR event, or belongs to a
   * different workflow - never a silent mislink.
   *
   * @param simulationId the run's live simulation
   * @param eventId the event root id the orchestrator asked to reuse
   */
  @Transactional(readOnly = true)
  public void assertEventRootOnSimulationWorkflow(String simulationId, String eventId)
      throws ChainingException {
    Workflow template =
        findWorkflowTemplateBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ChainingException(
                        "Workflow (TEMPLATE) not found. Simulation ID: " + simulationId));
    assertEventRootOnWorkflow(template.getId(), eventId);
  }

  /**
   * Scenario-side twin of {@link #assertEventRootOnSimulationWorkflow}: validates {@code eventId}
   * is an existing finding-event root on the scenario's template workflow (author-scenario mode).
   */
  @Transactional(readOnly = true)
  public void assertEventRootOnScenarioWorkflow(String scenarioId, String eventId)
      throws ChainingException {
    Workflow template =
        findWorkflowTemplateByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ChainingException(
                        "Workflow (TEMPLATE) not found. Scenario ID: " + scenarioId));
    assertEventRootOnWorkflow(template.getId(), eventId);
  }

  /**
   * Validates every non-blank id in {@code eventConditionIds} is an AND/OR finding-event root on
   * {@code workflowId}. The service-boundary guard for the reused-event link channel (which itself
   * only checks root-ness): callers pass the reused ids straight to {@code
   * stepInput.setConditionIds}, so this is what keeps a cross-workflow or non-event id from being
   * linked. No-op for a null/empty list.
   */
  private void assertEventRootsOnWorkflow(String workflowId, List<String> eventConditionIds)
      throws ChainingException {
    if (eventConditionIds == null) {
      return;
    }
    for (String eventConditionId : eventConditionIds) {
      if (hasText(eventConditionId)) {
        assertEventRootOnWorkflow(workflowId, eventConditionId);
      }
    }
  }

  private void assertEventRootOnWorkflow(String workflowId, String eventId)
      throws ChainingException {
    Condition condition = conditionService.findConditionByIdOrNull(eventId);
    if (condition == null) {
      throw new ChainingException(
          "event_id '"
              + eventId
              + "' does not exist. Read a step's event_id from the attack-path state and pass"
              + " exactly that, or omit event_id to create a new event.");
    }
    if (condition.getConditionParent() != null) {
      throw new ChainingException(
          "event_id '"
              + eventId
              + "' is not an event root (it is a child condition). Pass the event's root id from a"
              + " step's event_id.");
    }
    if (!EVENT_ROOT_TYPES.contains(condition.getType())) {
      throw new ChainingException(
          "event_id '"
              + eventId
              + "' is not a finding EVENT (type "
              + condition.getType()
              + ", expected AND/OR). Only finding events can be shared across steps.");
    }
    if (!Objects.equals(condition.getWorkflowId(), workflowId)) {
      throw new ChainingException(
          "event_id '"
              + eventId
              + "' belongs to a different workflow. An event can only be reused within the same"
              + " run's attack path.");
    }
  }

  /**
   * The AND/OR finding-event ROOT id currently linked to a step template, or {@code null} when the
   * step has no finding event (a seed, standalone, or pure DEPEND_ON step). This is the id the
   * attack-path state surfaces as {@code event_id} and the caller records in the run's sim-&gt;
   * scenario event mapping so a reused event mirrors to the same scenario event.
   *
   * <p>Filters on both the AND/OR type AND root-ness ({@code conditionParent == null}): a step's
   * linked conditions include an event's leaf children (they carry the step link too), and a
   * manually authored event MAY nest AND/OR groups, so a type-only match could return a nested
   * child group instead of the root. The returned id must be a true root because {@link
   * #assertEventRootOnWorkflow} rejects non-roots when the caller reuses it, and the sim-&gt;
   * scenario event mirror is keyed on root ids. Autonomous events are flat (one AND/OR root + leaf
   * filters), so this only hardens the read against manually edited or future nested trees.
   *
   * @param stepTemplateId the step template to inspect
   * @return the linked event root id, or {@code null}
   */
  @Transactional(readOnly = true)
  public String findStepTriggerEventRootId(String stepTemplateId) {
    return conditionService.findAllConditionsByStepId(stepTemplateId).stream()
        .filter(condition -> condition.getConditionParent() == null)
        .filter(condition -> EVENT_ROOT_TYPES.contains(condition.getType()))
        .map(Condition::getId)
        .findFirst()
        .orElse(null);
  }

  /**
   * Resolves an existing finding-event root into a fresh set of {@link ConditionCreateInput}s (the
   * AND/OR root plus its WHOLE non-MAPPER subtree) so a faithful COPY of the event can be created
   * on another workflow. Used by the scenario mirror as a fallback: when the run has no recorded
   * sim-&gt;scenario twin for a reused simulation event, the mirror re-creates the event on the
   * scenario so the exported step is never left event-less. MAPPER children are excluded - the
   * caller supplies the step's own mappers. Returns an empty list when {@code eventId} is not a
   * resolvable event root.
   *
   * <p>Copies the subtree to FULL depth (not just the root's direct children): an event authored in
   * the manual logic map MAY nest AND/OR condition groups, and a shallow copy would silently drop
   * grandchildren, mirroring a structurally incorrect / partial event onto the scenario. The walk
   * mirrors {@link StepService#copyStepConditionTemplate} - group children by parent id, then BFS
   * from the root re-parenting each copied node by temporary id. Autonomous events are flat, so
   * this only hardens the fallback against manually edited or future nested trees.
   *
   * @param eventId the existing event root id to copy
   * @return the root + full-subtree inputs, or an empty list
   */
  @Transactional(readOnly = true)
  public List<ConditionCreateInput> resolveEventRootAsInputs(String eventId) {
    Condition root = conditionService.findConditionByIdOrNull(eventId);
    if (root == null
        || root.getConditionParent() != null
        || !EVENT_ROOT_TYPES.contains(root.getType())) {
      return List.of();
    }
    // Index the event's whole subtree by parent id so nested groups are copied to full depth.
    Map<String, List<Condition>> childrenByParentId =
        conditionService.findAllNonMapperConditionsByWorkflowId(root.getWorkflowId()).stream()
            .filter(condition -> condition.getConditionParent() != null)
            .collect(Collectors.groupingBy(condition -> condition.getConditionParent().getId()));

    List<ConditionCreateInput> inputs = new ArrayList<>();
    String rootTmpId = UUID.randomUUID().toString();
    inputs.add(
        ConditionCreateInput.builder()
            .temporaryId(rootTmpId)
            .type(root.getType())
            .name(root.getName())
            .build());
    // BFS from the root carrying each source node's assigned temporary id so children re-parent
    // onto their copied parent. The visited set guards against a corrupted parent chain cycling
    // (same guard as ConditionService#isPreserved).
    Set<String> visited = new HashSet<>();
    visited.add(root.getId());
    Queue<Map.Entry<String, String>> queue = new LinkedList<>();
    queue.add(Map.entry(root.getId(), rootTmpId));
    while (!queue.isEmpty()) {
      Map.Entry<String, String> current = queue.poll();
      for (Condition child : childrenByParentId.getOrDefault(current.getKey(), List.of())) {
        if (child.getId() == null || !visited.add(child.getId())) {
          continue;
        }
        String childTmpId = UUID.randomUUID().toString();
        inputs.add(
            ConditionCreateInput.builder()
                .temporaryId(childTmpId)
                .temporaryIdConditionParent(current.getValue())
                .type(child.getType())
                .keyTypes(child.getKeyTypes())
                .value(child.getValue())
                .caseSensitive(child.isCaseSensitive())
                .name(child.getName())
                .build());
        queue.add(Map.entry(child.getId(), childTmpId));
      }
    }
    return inputs;
  }

  /**
   * Reads the authored attack path of an autonomous run's simulation as an ordered list of its
   * INJECT_EXECUTION step templates - the STABLE authoring handles the orchestrator built - each
   * with its DEPEND_ON parent, its baked inject definition, and the inject ids of the run steps it
   * has spawned. This is the source of the enriched attack-path state read: it lets the caller
   * surface step_template_id + parent + target + live status so the orchestrator can chain onto or
   * update an existing step by id instead of re-authoring a duplicate.
   *
   * @param simulationId the autonomous run's live simulation
   * @return the authored steps in creation order (empty when there is no template workflow yet)
   */
  @Transactional(readOnly = true)
  public List<AuthoredAttackStep> readAuthoredAttackPath(String simulationId) {
    return readAuthoredAttackPathFromTemplate(findWorkflowTemplateBySimulationId(simulationId));
  }

  /**
   * Scenario-side twin of {@link #readAuthoredAttackPath(String)} for author-scenario (AI planning)
   * runs: reads the steps authored directly onto the scenario's workflow TEMPLATE, since a plan run
   * has no simulation. Run inject ids are always empty here - a plan never executes.
   */
  @Transactional(readOnly = true)
  public List<AuthoredAttackStep> readAuthoredAttackPathForScenario(String scenarioId) {
    try {
      return readAuthoredAttackPathFromTemplate(findWorkflowTemplateByScenarioId(scenarioId));
    } catch (ChainingException e) {
      log.warn("[Chaining] Could not read authored attack path for scenario {}", scenarioId, e);
      return List.of();
    }
  }

  private List<AuthoredAttackStep> readAuthoredAttackPathFromTemplate(Optional<Workflow> template) {
    if (template.isEmpty()) {
      return List.of();
    }
    Workflow workflow = template.get();
    List<Step> steps =
        stepService.findAllStepTemplateByWorkflow(workflow.getId()).stream()
            .filter(s -> StepActionClass.INJECT_EXECUTION.equals(s.getStepAction()))
            .sorted(
                Comparator.comparing(
                    Step::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    if (steps.isEmpty()) {
      return List.of();
    }
    // Batch every step's LINKED root conditions in one query - a step links only its condition
    // ROOTS (the DEPEND_ON parent, the finding-trigger event root, and any MAPPER roots) - and the
    // workflow's leaf filter conditions once, grouped by parent id. This replaces a per-step
    // condition read (the old dependOnParentTemplateId call) plus a per-step trigger walk with two
    // batched reads, so the orchestrator's per-cycle attack-path poll stays flat instead of N+1 in
    // the step count.
    Set<String> stepIds = steps.stream().map(Step::getId).collect(Collectors.toSet());
    Map<String, List<Condition>> rootsByStep = conditionService.findAllConditionsByStepIds(stepIds);
    Map<String, List<Condition>> filterLeavesByParentId =
        conditionService.findAllNonMapperConditionsByWorkflowId(workflow.getId()).stream()
            .filter(condition -> condition.getConditionParent() != null)
            .collect(Collectors.groupingBy(condition -> condition.getConditionParent().getId()));
    List<AuthoredAttackStep> authored = new ArrayList<>();
    for (Step step : steps) {
      List<String> runInjectIds =
          step.getStepsExecuted().stream()
              .map(runStep -> StepService.getField(runStep.getData(), "inject_id"))
              .filter(id -> id != null && !id.isBlank())
              .distinct()
              .toList();
      List<Condition> roots = rootsByStep.getOrDefault(step.getId(), List.of());
      authored.add(
          new AuthoredAttackStep(
              step.getId(),
              dependOnParentFromRoots(roots),
              step.getData(),
              runInjectIds,
              triggerRootId(roots),
              triggerEventName(roots),
              triggerFilters(roots, filterLeavesByParentId),
              triggerMappings(roots)));
    }
    return authored;
  }

  /** The DEPEND_ON parent step template id among a step's linked root conditions, or null. */
  private static String dependOnParentFromRoots(List<Condition> roots) {
    return roots.stream()
        .filter(condition -> condition.getType() == ConditionType.DEPEND_ON)
        .map(Condition::getValue)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null);
  }

  /**
   * The finding-trigger event ROOT (an AND / OR node with no parent) among a step's linked
   * conditions, or null. Filters on root-ness (conditionParent == null) as well as the AND/OR type:
   * a step links its event's leaf children too, and an event may nest AND/OR groups, so a type-only
   * match could return a nested child group. This value is surfaced to the orchestrator as {@code
   * event_id} and passed back to reuse the event, where {@link #assertEventRootOnWorkflow} rejects
   * anything that is not a true root - so it must be the root here.
   */
  private static Condition triggerRoot(List<Condition> roots) {
    return roots.stream()
        .filter(condition -> condition.getConditionParent() == null)
        .filter(
            condition ->
                condition.getType() == ConditionType.AND || condition.getType() == ConditionType.OR)
        .findFirst()
        .orElse(null);
  }

  /**
   * Stable id of the step's finding EVENT (the trigger root), or null when it has none. This is the
   * handle the orchestrator passes back as a trigger's {@code event_id} to attach another step to
   * the SAME event instead of duplicating it.
   */
  private static String triggerRootId(List<Condition> roots) {
    Condition root = triggerRoot(roots);
    return root != null ? root.getId() : null;
  }

  /** Human name of the step's finding EVENT (the trigger root's name), or null when it has none. */
  private static String triggerEventName(List<Condition> roots) {
    Condition root = triggerRoot(roots);
    return root != null && hasText(root.getName()) ? root.getName().trim() : null;
  }

  /**
   * The step's finding predicates rendered as "&lt;key&gt; &lt;operator&gt; &lt;value&gt;" (value
   * omitted for a valueless operator such as IS_NOT_NULL), read back from the trigger root's leaf
   * children so the caller sees exactly what the step fires on - the finding-driven wiring, not an
   * inferred linear chain.
   */
  private static List<String> triggerFilters(
      List<Condition> roots, Map<String, List<Condition>> filterLeavesByParentId) {
    Condition root = triggerRoot(roots);
    if (root == null) {
      return List.of();
    }
    return filterLeavesByParentId.getOrDefault(root.getId(), List.of()).stream()
        .map(WorkflowService::formatTriggerFilter)
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * The step's finding-value bindings rendered as "&lt;key&gt; -&gt; &lt;input&gt;", from MAPPERs.
   */
  private static List<String> triggerMappings(List<Condition> roots) {
    return roots.stream()
        .filter(condition -> condition.getType() == ConditionType.MAPPER)
        .map(WorkflowService::formatTriggerMapping)
        .filter(Objects::nonNull)
        .toList();
  }

  private static String formatTriggerFilter(Condition condition) {
    String key = keyTypeLabels(condition.getKeyTypes());
    if (key == null) {
      return null;
    }
    String operator = condition.getType() != null ? condition.getType().name() : "";
    String value = condition.getValue();
    String base = hasText(operator) ? key + " " + operator : key;
    return hasText(value) ? base + " " + value.trim() : base;
  }

  private static String formatTriggerMapping(Condition condition) {
    String key = keyTypeLabels(condition.getKeyTypes());
    String input = condition.getKey();
    if (key == null || !hasText(input)) {
      return null;
    }
    return key + " -> " + input.trim();
  }

  /** Joins a condition's key types by their primitive label (e.g. "port", "ipv4"), or null. */
  private static String keyTypeLabels(List<PrimitiveType> keyTypes) {
    if (keyTypes == null || keyTypes.isEmpty()) {
      return null;
    }
    String joined =
        keyTypes.stream()
            .filter(Objects::nonNull)
            .map(keyType -> keyType.label)
            .collect(Collectors.joining("/"));
    return hasText(joined) ? joined : null;
  }

  /**
   * Updates an existing chained step's inject definition IN PLACE (same step template id, same
   * DEPEND_ON parent), so the orchestrator can edit a step it already authored instead of minting a
   * duplicate. Works for both the simulation template step and its scenario mirror twin - the
   * caller passes whichever step template id it wants to update.
   *
   * @param stepTemplateId the id of the step template to update
   * @param injectInput the new inject definition
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateChainedStep(String stepTemplateId, InjectInput injectInput)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, null);
  }

  /**
   * Trigger-aware overload of {@link #updateChainedStep(String, InjectInput)}: in addition to the
   * inject data, it replaces the step's finding-trigger conditions with {@code triggerConditions}
   * (preserving any DEPEND_ON ordering parent), so the orchestrator can CORRECT a mis-wired
   * finding-driven step in place. A {@code null} {@code triggerConditions} keeps the existing
   * conditions untouched (data-only), exactly like the two-argument overload; an empty list clears
   * the trigger while keeping the DEPEND_ON parent.
   *
   * @param triggerConditions the finding-trigger + mapper conditions to install, or {@code null} to
   *     leave the step's conditions untouched
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateChainedStep(
      String stepTemplateId, InjectInput injectInput, List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, triggerConditions);
  }

  /**
   * Existing-event overload of {@link #updateChainedStep(String, InjectInput, List)}: rebuilds the
   * step's finding trigger from {@code triggerConditions} (typically MAPPERs only) AND links it to
   * EXISTING event roots by id, so the orchestrator can CORRECT a step to fire on an event that
   * already exists instead of minting a duplicate. Preserves the DEPEND_ON ordering parent and the
   * reused event subtree across the rebuild.
   *
   * @param existingEventConditionIds ids of existing event roots to attach this step to (empty to
   *     rebuild a fresh trigger with no reuse)
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateChainedStep(
      String stepTemplateId,
      InjectInput injectInput,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, triggerConditions, existingEventConditionIds);
  }

  /**
   * Transaction-isolated variant of {@link #updateChainedStep} for the autonomous orchestrator's
   * step-update callback, used to keep the scenario mirror twin in lock-step. Runs in its OWN
   * transaction ({@link Propagation#REQUIRES_NEW}) so a twin-update failure rolls back only itself
   * and can NEVER mark the caller's update transaction rollback-only. The executing simulation step
   * is updated authoritatively first; the scenario mirror is a secondary projection - a twin
   * failure must not fail (500) the update callback. See {@code
   * AutonomousRunService#updateAttackPathStep}.
   *
   * <p>Delegates to the same private, non-transactional body as {@link #updateChainedStep}: a
   * same-class call to a {@code @Transactional} sibling would bypass the Spring proxy (see {@code
   * TenantBackgroundTransactionArchTest#no_transactional_self_invocation}).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void updateChainedStepIsolated(String stepTemplateId, InjectInput injectInput)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, null);
  }

  /**
   * Trigger-aware, transaction-isolated variant of {@link #updateChainedStepIsolated(String,
   * InjectInput)} used to keep the scenario mirror twin's finding trigger in lock-step with the
   * corrected simulation step. Same {@link Propagation#REQUIRES_NEW} best-effort isolation.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void updateChainedStepIsolated(
      String stepTemplateId, InjectInput injectInput, List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, triggerConditions);
  }

  /**
   * Existing-event, transaction-isolated variant used to keep the scenario mirror twin's event
   * linkage in lock-step when the corrected simulation step reuses an existing event. Same {@link
   * Propagation#REQUIRES_NEW} best-effort isolation as the other isolated update overloads.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void updateChainedStepIsolated(
      String stepTemplateId,
      InjectInput injectInput,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, triggerConditions, existingEventConditionIds);
  }

  /**
   * Deletes a chained step template (and its conditions) on behalf of the autonomous orchestrator,
   * so it can PRUNE a mis-authored finding-driven step. Bypasses the manual editability guard for
   * the same reason the author path does (the orchestrator owns the run's workflow).
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteChainedStep(String stepTemplateId) throws ChainingException {
    stepService.deleteInjectStepTemplate(stepTemplateId);
  }

  /**
   * Transaction-isolated variant of {@link #deleteChainedStep} for pruning the scenario mirror twin
   * in lock-step. Runs in its OWN transaction ({@link Propagation#REQUIRES_NEW}) so a twin-delete
   * failure rolls back only itself and can NEVER mark the caller's delete transaction
   * rollback-only.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void deleteChainedStepIsolated(String stepTemplateId) throws ChainingException {
    stepService.deleteInjectStepTemplate(stepTemplateId);
  }

  // Shared body for the update overloads. Private and non-transactional: the public overloads are
  // the @Transactional proxy entry points and each delegates here (an intra-class call to a
  // @Transactional sibling would bypass the proxy). A null triggerConditions means "data-only,
  // keep the existing conditions"; a non-null list rebuilds the finding trigger while preserving
  // the DEPEND_ON ordering parent.
  private void doUpdateChainedStep(
      String stepTemplateId, InjectInput injectInput, List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    doUpdateChainedStep(stepTemplateId, injectInput, triggerConditions, List.of());
  }

  // Existing-event variant of the update body: in addition to rebuilding the trigger it can LINK
  // the step to EXISTING event roots by id ({@code existingEventConditionIds}), so a corrected step
  // attaches to an event that already exists instead of duplicating it - the update-side twin of
  // doAppendChainedStep's condition_ids channel. Reused ids are validated at this service boundary
  // exactly like the append paths. The step service preserves those roots (subtree included)
  // across the condition rebuild so a shared event is never dropped, then links them.
  private void doUpdateChainedStep(
      String stepTemplateId,
      InjectInput injectInput,
      List<ConditionCreateInput> triggerConditions,
      List<String> existingEventConditionIds)
      throws ChainingException {
    // Defence in depth (same boundary as the append paths): the link channel below only checks
    // root-ness, so validate every reused id is an AND/OR finding-event root on the step's OWN
    // workflow before the rebuild links it. Resolved lazily - the step lookup only happens when a
    // reused id is actually present, so the common no-reuse update pays nothing. A no-op for the
    // validated autonomous caller; a stale scenario-mirror twin id throws and is swallowed by the
    // best-effort mirror exactly like on the append side.
    if (existingEventConditionIds != null
        && existingEventConditionIds.stream().anyMatch(id -> hasText(id))) {
      Workflow stepWorkflow = stepService.findStepTemplateById(stepTemplateId).getWorkflow();
      assertEventRootsOnWorkflow(
          stepWorkflow == null ? null : stepWorkflow.getId(), existingEventConditionIds);
    }
    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    if (existingEventConditionIds != null && !existingEventConditionIds.isEmpty()) {
      stepInput.setConditionIds(existingEventConditionIds);
    }
    Step updated =
        triggerConditions == null
            ? stepService.updateInjectStepTemplateData(stepTemplateId, stepInput)
            : stepService.updateInjectStepTemplateDataAndTrigger(
                stepTemplateId, stepInput, triggerConditions);
    rearmStepForReExecution(updated);
  }

  /**
   * Re-arms an in-place-updated step so its corrected definition re-executes on the next {@link
   * #evaluateWorkflowProgress}. The data swap alone never re-runs an already-executed step: its
   * committed execution hashes still mark it fired, so the engine skips it. Clearing those hashes
   * on the step's live RUN workflow(s) lets it ready again - this is what makes the autonomous
   * "update a step, evaluate, re-run the corrected version" loop actually re-fire.
   *
   * <p>Simulation-scoped by construction: re-fire state lives only on RUN workflows, which exist
   * only on the simulation. A scenario-owned template (e.g. the autonomous scenario mirror twin,
   * updated in lock-step) has no simulation and no RUN workflow, so this is a no-op for it -
   * exactly right, since the mirror never executes.
   */
  private void rearmStepForReExecution(Step stepTemplate) {
    Workflow template = stepTemplate.getWorkflow();
    if (template == null || template.getSimulation() == null) {
      return;
    }
    for (Workflow runWorkflow : findWorkflowRunBySimulationId(template.getSimulation().getId())) {
      workflowStateService.clearExecutionHashes(stepTemplate, runWorkflow);
    }
  }

  /**
   * One authored attack-path step: its stable template id, its DEPEND_ON ordering parent (null when
   * it is a seed or wired finding-driven), the baked inject JSON ({@code step_data}), the inject
   * ids of every run step it has spawned, and the read-back of its finding TRIGGER - the event
   * name, the filter predicates it fires on, and the finding-value input bindings it consumes - so
   * a reader reconstructs the finding-driven wiring rather than inferring a linear DEPEND_ON chain.
   */
  public record AuthoredAttackStep(
      String stepTemplateId,
      String parentStepTemplateId,
      String injectDataJson,
      List<String> runInjectIds,
      String eventId,
      String eventName,
      List<String> triggerFilters,
      List<String> triggerMappings) {}
}
