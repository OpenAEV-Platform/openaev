package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** A run-status transition pushed by the XTM One orchestrator (running, waiting-input, completed, failed...). */
@Getter
@Setter
@Schema(description = "Run status update pushed by the orchestrator")
public class AutonomousStatusUpdateInput {

  @NotNull
  @JsonProperty("status")
  @Schema(description = "New lifecycle status")
  private AutonomousRunStatus status;

  @JsonProperty("last_error")
  @Schema(description = "Error message when the run failed")
  private String lastError;

  @JsonProperty("title")
  @Schema(description = "Optional short title for the status timeline entry")
  private String title;

  @JsonProperty("content")
  @Schema(description = "Optional narration for the status timeline entry")
  private String content;
}
