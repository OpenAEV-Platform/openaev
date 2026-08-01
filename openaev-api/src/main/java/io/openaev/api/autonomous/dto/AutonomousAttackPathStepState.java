package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A live snapshot of one step already authored on an autonomous attack path: the inject, its current
 * execution status, and its execution traces. The orchestrator reads the full list every decision
 * cycle so it can see what it has ALREADY built (and thus avoid authoring the same inject twice) and
 * what each step actually did (its traces) before deciding the next move.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Live state of one authored attack-path step")
public class AutonomousAttackPathStepState {

  @JsonProperty("inject_id")
  @Schema(description = "Id of the inject backing this step")
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
