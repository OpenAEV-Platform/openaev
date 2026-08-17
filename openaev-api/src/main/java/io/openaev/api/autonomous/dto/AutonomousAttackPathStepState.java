package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A live snapshot of one step already authored on an autonomous attack path. Every attack path is a
 * finding-driven graph, not a linear chain: a step fires either when an upstream finding matches
 * its {@code trigger} (the preferred wiring - {@code event_name} + {@code trigger_filters} + {@code
 * trigger_mappings}) or, only for pure ordering, when its {@code parent_step_template_id}
 * (DEPEND_ON) step has run. The snapshot carries both so a reader can reconstruct the EXACT wiring
 * it has already built - which findings each step reacts to and which values it consumes, plus the
 * kill-chain parent when one exists - and therefore chain onto, correct, or prune an existing step
 * (by its stable {@code step_template_id}) instead of re-authoring a duplicate or rebuilding the
 * path as a flat DEPEND_ON list. It also carries each step's backing inject, live execution status
 * and execution traces so the reader can see what a step actually did before deciding the next
 * move.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Live state of one authored attack-path step")
public class AutonomousAttackPathStepState {

  @JsonProperty("step_template_id")
  @Schema(
      description =
          "Stable authoring handle for this step (the chaining step template id). Pass it as "
              + "parent_step_template_id to chain a follow-on step onto it, or to update/replace "
              + "this exact step in place instead of re-authoring a duplicate.")
  private String stepTemplateId;

  @JsonProperty("parent_step_template_id")
  @Schema(
      description =
          "Pure-ordering parent: the step template id this step runs AFTER (its DEPEND_ON parent), "
              + "or null when the step is a seed or is wired finding-driven via its trigger. Prefer "
              + "reading the trigger fields below to understand WHY a step fires; this is only the "
              + "ordering fallback, not the primary wiring.")
  private String parentStepTemplateId;

  @JsonProperty("event_id")
  @Schema(
      description =
          "Stable id of the finding EVENT this step fires on (the trigger root), or null for a "
              + "seed / standalone / pure DEPEND_ON step that has no event. Pass it back as a "
              + "trigger's event_id when authoring another step to attach that step to the SAME "
              + "event instead of duplicating it - this is how several actions share one event "
              + "(e.g. one \"SMB service exposed\" event feeding many follow-on actions).")
  private String eventId;

  @JsonProperty("event_name")
  @Schema(
      description =
          "Human-readable name of the finding EVENT this step reacts to (the trigger root's name, "
              + "e.g. \"SMB service exposed\"), or null when the step has no finding trigger (a seed "
              + "or a pure DEPEND_ON step). Mirror of the trigger's event_name on the write side.")
  private String eventName;

  @JsonProperty("trigger_filters")
  @Schema(
      description =
          "The finding predicates that make this step fire, each rendered as "
              + "\"<key> <operator> <value>\" (e.g. \"port EQ 445\", \"service IS_NOT_NULL\"). Empty "
              + "when the step is a seed or a pure DEPEND_ON step. This is the read-back of the "
              + "trigger's filters, so a reader can see - and correct - exactly what the step "
              + "listens for instead of inferring a linear chain.")
  private List<String> triggerFilters;

  @JsonProperty("trigger_mappings")
  @Schema(
      description =
          "The finding values this step binds into its inject inputs, each rendered as "
              + "\"<key> -> <input>\" (e.g. \"ipv4 -> target_host\"). Empty when the step consumes no "
              + "finding values. This is the read-back of the trigger's mappings.")
  private List<String> triggerMappings;

  @JsonProperty("inject_id")
  @Schema(description = "Id of the inject backing this step (empty until the step has executed)")
  private String injectId;

  @JsonProperty("title")
  @Schema(description = "Human-readable step title")
  private String title;

  @JsonProperty("type")
  @Schema(description = "Inject type (injector) of the step")
  private String type;

  @JsonProperty("injector_contract_id")
  @Schema(description = "Id of the injector contract the step runs, when resolvable")
  private String injectorContractId;

  @JsonProperty("target")
  @Schema(
      description =
          "Resolved target of the step (teams / assets / asset groups, or 'inherits run scope' "
              + "when it binds to the run's allow-list).")
  private String target;

  @JsonProperty("status")
  @Schema(
      description =
          "Execution status: PENDING when never started, otherwise the live ExecutionStatus "
              + "(QUEUING, EXECUTING, SUCCESS, ERROR, ...)")
  private String status;

  @JsonProperty("traces")
  @Schema(description = "Execution traces (action/status: message) captured while the step ran")
  private List<String> traces;
}
