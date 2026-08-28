package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The id of the step template just authored on a live autonomous attack path. The orchestrator
 * feeds it back as {@code parent_step_template_id} to chain the next step onto it.
 */
@Getter
@AllArgsConstructor
@Schema(description = "Result of appending a chained attack-path step")
public class AutonomousAttackPathStepResult {

  @JsonProperty("step_template_id")
  @Schema(description = "Id of the created step template")
  private String stepTemplateId;
}
