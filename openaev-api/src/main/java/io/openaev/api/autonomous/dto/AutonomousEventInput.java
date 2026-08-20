package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * A timeline event pushed by the XTM One orchestrator into an autonomous run (narration, decision,
 * tool action, capability gap, proof...). Appending nudges the attack-path SSE so the live view
 * animates the graph and the AI decision timeline together.
 */
@Getter
@Setter
@Schema(description = "Timeline event appended by the orchestrator")
public class AutonomousEventInput {

  @NotNull
  @JsonProperty("type")
  @Schema(description = "Kind of timeline entry")
  private AutonomousEventType type;

  @JsonProperty("title")
  @Schema(description = "Short human title")
  private String title;

  @JsonProperty("content")
  @Schema(description = "Human-readable narration / body")
  private String content;

  @JsonProperty("data")
  @Schema(description = "Structured JSON payload (tool i/o, gap suggestions, proof metadata)")
  private String data;
}
