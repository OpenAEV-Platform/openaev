package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.autonomous.dto.AutonomousAttackPathStepInput;
import io.openaev.api.autonomous.dto.AutonomousAttackPathStepResult;
import io.openaev.api.autonomous.dto.AutonomousAttackPathStepState;
import io.openaev.api.autonomous.dto.AutonomousConvertToManualInput;
import io.openaev.api.autonomous.dto.AutonomousDefaultAgentsInput;
import io.openaev.api.autonomous.dto.AutonomousDefaultAgentsOutput;
import io.openaev.api.autonomous.dto.AutonomousDirectiveInput;
import io.openaev.api.autonomous.dto.AutonomousEventInput;
import io.openaev.api.autonomous.dto.AutonomousPromotedAssetResult;
import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.autonomous.dto.AutonomousScopeUpdateInput;
import io.openaev.api.autonomous.dto.AutonomousScopeView;
import io.openaev.api.autonomous.dto.AutonomousStatusUpdateInput;
import io.openaev.api.autonomous.dto.AutonomousTargetTeamInput;
import io.openaev.api.autonomous.dto.AutonomousTargetTeamResult;
import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.api.autonomous.dto.CapabilityReport;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.autonomous.CapabilityResolverService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autonomous (AI-driven) attack-path run endpoints. Two independent gates apply to every method:
 *
 * <ul>
 *   <li>the {@code AUTONOMOUS_ATTACK_PATH} preview feature (which itself requires {@code
 *       ATTACK_PATH} + {@code INJECT_CHAINING}), resolved inside {@link AutonomousRunService},
 *       returning 404 when the feature is off - the same convention the attack-path and chaining
 *       APIs use; and
 *   <li>the Enterprise Edition license, enforced declaratively by {@code @AccessControl(...,
 *       isEnterpriseEdition = true)}. This is an AI feature, so it is EE-only exactly like every
 *       other AI capability (remediation generation, XTM One chat); the aspect enforces the EE gate
 *       even though RBAC is skipped.
 * </ul>
 *
 * <p>The controller is deliberately thin: all lifecycle, callback, steering, and read logic lives
 * in {@link AutonomousRunService}. Tenant isolation is enforced by the statement inspector on the
 * {@code autonomous_*} tables; RBAC is skipped at the annotation level because the run's authority
 * derives from its bound simulation, checked in-service.
 *
 * <p>Endpoints split into three audiences: the operator UI (create / start / pause / resume /
 * cancel / steer / read), the XTM One orchestrator callbacks (events / status / directive
 * consumption), and the objective-template gallery.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({AutonomousRunApi.AUTONOMOUS_URI, TENANT_PREFIX + "/autonomous-runs"})
public class AutonomousRunApi extends RestBehavior {

  public static final String AUTONOMOUS_URI = "/api/autonomous-runs";

  private final AutonomousRunService autonomousRunService;
  private final CapabilityResolverService capabilityResolverService;

  // region operator UI

  @Operation(summary = "List objective templates for the run-creation gallery")
  @GetMapping("/objective-templates")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousObjectiveTemplate> objectiveTemplates() {
    return autonomousRunService.objectiveTemplates();
  }

  @Operation(
      summary = "List specialist agents the orchestrator can consult",
      description =
          "Sourced from XTM One's aev.attack_path_additional_agent intent catalog. Returns an empty"
              + " list when XTM One is not configured or exposes no such agents, so the UI can show"
              + " a CTA-only state.")
  @GetMapping("/available-agents")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<ChatbotAgentOutput> availableAgents() {
    return autonomousRunService.availableAdditionalAgents();
  }

  @Operation(
      summary = "Get the tenant's default additional agents + per-agent discovery modes",
      description =
          "Returns the agent ids consulted by default and each agent's default discovery mode"
              + " (EXISTING_ONLY / SCOPED / EXPANSIVE).")
  @GetMapping("/default-agents")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousDefaultAgentsOutput defaultAgents() {
    return new AutonomousDefaultAgentsOutput(
        autonomousRunService.defaultAdditionalAgentIds(),
        autonomousRunService.defaultAdditionalAgentModes());
  }

  @Operation(
      summary = "Set the tenant's default additional agents + per-agent discovery modes",
      description = "Persists both the enabled agent ids and each agent's default discovery mode.")
  @PutMapping("/default-agents")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousDefaultAgentsOutput setDefaultAgents(
      @RequestBody AutonomousDefaultAgentsInput input) {
    List<String> ids =
        autonomousRunService.updateDefaultAdditionalAgentIds(
            input != null ? input.getAgentIds() : null);
    Map<String, String> modes =
        autonomousRunService.updateDefaultAdditionalAgentModes(
            input != null ? input.getAgentModes() : null);
    return new AutonomousDefaultAgentsOutput(ids, modes);
  }

  @Operation(
      summary = "Resolve techniques / desired outputs against the installed arsenal + gaps",
      description =
          "Powers the UI capability-gap strip and the orchestrator's openaev_capability_gaps "
              + "tool: for each requested technique or output type, reports the installed "
              + "contracts that satisfy it, or marketplace connectors to install to close the gap.")
  @PostMapping("/capabilities/resolve")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public CapabilityReport resolveCapabilities(@Valid @RequestBody CapabilityQueryInput input) {
    return capabilityResolverService.resolve(input);
  }

  @Operation(summary = "Create an autonomous attack-path run")
  @PostMapping
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun create(@Valid @RequestBody AutonomousRunCreateInput input) {
    return autonomousRunService.create(input);
  }

  @Operation(summary = "List autonomous runs, newest first")
  @GetMapping
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousRun> list() {
    return autonomousRunService.list();
  }

  @Operation(summary = "Get one autonomous run")
  @GetMapping("/{runId}")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun get(@PathVariable String runId) {
    return autonomousRunService.get(runId);
  }

  @Operation(
      summary = "Get the autonomous run driving a given simulation, if any",
      description =
          "Lets the simulation detail page detect an AI-driven run and render the autonomous "
              + "cockpit instead of the manual chaining editor. 404 when the simulation is manual.")
  @GetMapping("/by-simulation/{simulationId}")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun getBySimulation(@PathVariable String simulationId) {
    return autonomousRunService.getBySimulation(simulationId);
  }

  @Operation(
      summary = "Get the autonomous run driving a given scenario, if any",
      description =
          "Scenario-side twin of by-simulation: lets the scenario detail page render the AI-driven"
              + " cockpit and steer the single underlying simulation. 404 when the scenario is"
              + " manual.")
  @GetMapping("/by-scenario/{scenarioId}")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun getByScenario(@PathVariable String scenarioId) {
    return autonomousRunService.getByScenario(scenarioId);
  }

  @Operation(summary = "Engage the orchestrator for a created run")
  @PostMapping("/{runId}/start")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun start(@PathVariable String runId) {
    return autonomousRunService.start(runId);
  }

  @Operation(summary = "Pause a live run and its chained simulation")
  @PostMapping("/{runId}/pause")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun pause(@PathVariable String runId) {
    return autonomousRunService.pause(runId);
  }

  @Operation(summary = "Resume a paused run and its chained simulation")
  @PostMapping("/{runId}/resume")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun resume(@PathVariable String runId) {
    return autonomousRunService.resume(runId);
  }

  @Operation(summary = "Cancel a run and its chained simulation")
  @PostMapping("/{runId}/cancel")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun cancel(@PathVariable String runId) {
    return autonomousRunService.cancel(runId);
  }

  @Operation(
      summary = "Restart a run in place (hard reset, valid from any status)",
      description =
          "Reuses the same scenario: stops the orchestrator, tears the old simulation down,"
              + " provisions a fresh one, resets the run's timeline / directives to CREATED. The"
              + " caller then starts it again. No new scenario is ever created on restart.")
  @PostMapping("/{runId}/restart")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun restart(@PathVariable String runId) {
    return autonomousRunService.restart(runId);
  }

  @Operation(
      summary = "Promote a completed dry-run plan to a real, executing run",
      description =
          "Turns a PLANNED dry-run into a live run in place: tears the non-executing plan"
              + " simulation and the mirrored plan steps down, provisions a fresh executing"
              + " simulation, clears plan mode and keeps the plan summary as guidance. The caller"
              + " then starts it again; the orchestrator follows the plan while adapting to live"
              + " findings.")
  @PostMapping("/{runId}/promote")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun promote(@PathVariable String runId) {
    return autonomousRunService.promoteToRealRun(runId);
  }

  @Operation(
      summary = "Convert an autonomous scenario into a manual chained scenario",
      description =
          "DUPLICATE copies the scenario (metadata + attack-path workflow) into a brand-new manual"
              + " chained scenario and leaves the AI run untouched. IN_PLACE turns this scenario"
              + " manual for good: it halts the orchestration, drops the autonomous run and its"
              + " timeline, and keeps the scenario + its simulation as a normal chained"
              + " scenario/simulation the operator can edit and delete. IN_PLACE is irreversible."
              + " Works whether the run is a dry-run plan or has already executed. Returns the"
              + " resulting manual scenario.")
  @PostMapping("/{runId}/convert-to-manual")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public Scenario convertToManual(
      @PathVariable String runId, @Valid @RequestBody AutonomousConvertToManualInput input) {
    return autonomousRunService.convertToManual(runId, input.getMode());
  }

  @Operation(summary = "Run decision timeline, optionally since a sequence cursor")
  @GetMapping("/{runId}/timeline")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousEvent> timeline(
      @PathVariable String runId, @RequestParam(defaultValue = "0") @Min(0) long since) {
    return autonomousRunService.timeline(runId, since);
  }

  @Operation(summary = "List the run's steering directives")
  @GetMapping("/{runId}/directives")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousDirective> directives(@PathVariable String runId) {
    return autonomousRunService.directives(runId);
  }

  @Operation(summary = "Queue a real-time steering directive for a live run")
  @PostMapping("/{runId}/directives")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousDirective addDirective(
      @PathVariable String runId, @Valid @RequestBody AutonomousDirectiveInput input) {
    return autonomousRunService.addDirective(runId, input.getContent());
  }

  @Operation(summary = "Apply a live scope / rate-limit / safe-mode edit without stopping the run")
  @PutMapping("/{runId}/configuration")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<Workflow> updateConfiguration(
      @PathVariable String runId, @Valid @RequestBody WorkflowConfigurationInput input) {
    return autonomousRunService.applyLiveConfiguration(runId, input);
  }

  @Operation(summary = "Orchestrator: read the run's live, resolved scope (allow-list + deny-list)")
  @GetMapping("/{runId}/scope")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousScopeView getScope(@PathVariable String runId) {
    return autonomousRunService.getRunScopeView(runId);
  }

  @Operation(summary = "Orchestrator: set the run's resolved scope (replaces the allow-list)")
  @PutMapping("/{runId}/scope")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun setScope(
      @PathVariable String runId, @Valid @RequestBody AutonomousScopeUpdateInput input) {
    return autonomousRunService.setRunScope(runId, input.getScope());
  }

  // endregion

  // region orchestrator callbacks

  @Operation(summary = "Orchestrator: append a timeline event")
  @PostMapping("/{runId}/events")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousEvent recordEvent(
      @PathVariable String runId, @Valid @RequestBody AutonomousEventInput input) {
    return autonomousRunService.recordEvent(
        runId, input.getType(), input.getTitle(), input.getContent(), input.getData());
  }

  @Operation(summary = "Orchestrator: update run status")
  @PostMapping("/{runId}/status")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun updateStatus(
      @PathVariable String runId, @Valid @RequestBody AutonomousStatusUpdateInput input) {
    return autonomousRunService.updateStatus(
        runId, input.getStatus(), input.getLastError(), input.getTitle(), input.getContent());
  }

  @Operation(summary = "Orchestrator: fetch and consume pending steering directives")
  @PostMapping("/{runId}/directives/consume")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousDirective> consumeDirectives(@PathVariable String runId) {
    return autonomousRunService.consumePendingDirectives(runId);
  }

  @Operation(
      summary = "Orchestrator: append a chained step to the live attack path",
      description =
          "The ONLY sanctioned way for the AI to build the attack path. Wraps the inject as a"
              + " chained INJECT_EXECUTION step on the run's simulation workflow so it executes"
              + " through the chaining engine and renders in the animated map. Prefer a"
              + " finding-driven 'trigger' (the step fires on a finding and consumes its values, so"
              + " the path draws itself) over a linear 'parent_step_template_id'. A step with"
              + " neither is a seed that readies immediately. Returns the created step template"
              + " id.")
  @PostMapping("/{runId}/attack-path/steps")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousAttackPathStepResult appendAttackPathStep(
      @PathVariable String runId, @Valid @RequestBody AutonomousAttackPathStepInput input) {
    String stepTemplateId =
        autonomousRunService.appendAttackPathStep(
            runId, input.getInject(), input.getParentStepTemplateId(), input.getTrigger());
    return new AutonomousAttackPathStepResult(stepTemplateId);
  }

  @Operation(
      summary = "Orchestrator: update an existing chained step in place",
      description =
          "Edits a step the orchestrator already authored - payload / target / injector contract /"
              + " title - by its step template id (from the attack-path state read), preserving the"
              + " step's id and its DEPEND_ON kill-chain parent. This is how the AI changes"
              + " existing logic instead of re-authoring a duplicate. The parent in the body is"
              + " ignored; the existing dependency is kept.")
  @PutMapping("/{runId}/attack-path/steps/{stepTemplateId}")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousAttackPathStepResult updateAttackPathStep(
      @PathVariable String runId,
      @PathVariable String stepTemplateId,
      @Valid @RequestBody AutonomousAttackPathStepInput input) {
    String updatedId =
        autonomousRunService.updateAttackPathStep(runId, stepTemplateId, input.getInject());
    return new AutonomousAttackPathStepResult(updatedId);
  }

  @Operation(
      summary = "Orchestrator: read the live attack-path state (authored steps + status + traces)",
      description =
          "The dedup + verify read path. Returns every step already authored on the run, each with"
              + " its backing inject, live execution status, and execution traces. The orchestrator"
              + " MUST consult this before authoring anything so it never re-authors an existing"
              + " step and can verify what each step actually did before deciding the next move.")
  @GetMapping("/{runId}/attack-path/state")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public List<AutonomousAttackPathStepState> attackPathState(@PathVariable String runId) {
    return autonomousRunService.attackPathState(runId);
  }

  @Operation(
      summary = "Orchestrator: evaluate the live attack path now",
      description =
          "Re-evaluates the run's workflow so freshly appended steps ready and execute immediately"
              + " instead of waiting for an in-flight step to complete. Called after appending"
              + " steps.")
  @PostMapping("/{runId}/attack-path/evaluate")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousRun evaluateAttackPath(@PathVariable String runId) {
    autonomousRunService.evaluateAttackPath(runId);
    return autonomousRunService.get(runId);
  }

  @Operation(
      summary = "Orchestrator: promote a finding to a targetable asset",
      description =
          "Turns a discovered finding (an IP / hostname / asset-type finding) into a real endpoint"
              + " asset the orchestrator can target with the next chained step - the"
              + " lateral-movement pivot. The ORIGINAL finding is kept and linked to the new asset."
              + " Returns the created asset id to use as the inject target.")
  @PostMapping("/{runId}/findings/{findingId}/promote-to-asset")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousPromotedAssetResult promoteFindingToAsset(
      @PathVariable String runId,
      @PathVariable String findingId,
      @RequestParam(name = "acting_agent_id", required = false) String actingAgentId) {
    return autonomousRunService.promoteFindingToAsset(runId, findingId, actingAgentId);
  }

  @Operation(
      summary = "Orchestrator: ensure a targetable team wrapping one or more persons",
      description =
          "An inject can only target a TEAM whose players are ENABLED on the simulation, so a"
              + " human-in-the-loop technique (phishing, smishing, credential harvesting) cannot"
              + " point at a person directly. This atomically reuses-or-creates a contextual team"
              + " on the run's simulation, sets its members, and enables those players for"
              + " delivery, returning a team id the next chained step targets - eliminating the"
              + " 'Email needs at least one user' failure caused by an unattached or empty wrapper"
              + " team.")
  @PostMapping("/{runId}/target-teams")
  @Transactional
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public AutonomousTargetTeamResult ensureTargetTeam(
      @PathVariable String runId, @Valid @RequestBody AutonomousTargetTeamInput input) {
    return autonomousRunService.ensureTargetTeam(
        runId, input.getPlayerIds(), input.getName(), input.getTeamId(), input.getActingAgentId());
  }

  // endregion
}
