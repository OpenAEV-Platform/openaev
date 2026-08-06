package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A live snapshot of one step already authored on an autonomous attack path: its stable authoring
 * handle (the step template id), its kill-chain parent (the DEPEND_ON step it runs after), its
 * resolved target, its backing inject, its current execution status, and its execution traces. The
 * orchestrator reads the full list every decision cycle so it can reconstruct the exact chain it
 * has ALREADY built - by {@code step_template_id} and {@code parent_step_template_id}, not by
 * guesswork - and therefore chain onto or UPDATE an existing step instead of re-authoring a
 * duplicate, and see what each step actually did (its traces) before deciding the next move.
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
          "The step template id this step runs AFTER (its DEPEND_ON parent), or null for a root "
              + "step. Together with step_template_id this reconstructs the attack-path graph.")
  private String parentStepTemplateId;

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
