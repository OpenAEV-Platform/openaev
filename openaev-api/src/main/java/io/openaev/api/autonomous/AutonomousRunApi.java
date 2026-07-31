package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.autonomous.dto.AutonomousDirectiveInput;
import io.openaev.api.autonomous.dto.AutonomousEventInput;
import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.autonomous.dto.AutonomousStatusUpdateInput;
import io.openaev.api.autonomous.dto.CapabilityQueryInput;
import io.openaev.api.autonomous.dto.CapabilityReport;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
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

  // endregion
}
