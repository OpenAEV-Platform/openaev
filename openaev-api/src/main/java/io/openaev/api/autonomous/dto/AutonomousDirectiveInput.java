package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * A real-time steering instruction injected into a live autonomous run. Consumed by the
 * orchestrator at the start of its next decision cycle, so it takes effect without stopping the
 * run.
 */
@Getter
@Setter
@Schema(description = "Operator steering directive for a live autonomous run")
public class AutonomousDirectiveInput {

  @NotBlank
  @JsonProperty("content")
  @Schema(description = "Free-text steering instruction (focus, avoid host, change tactic...)")
  private String content;
}
