package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.*;

/**
 * Output DTO representing a persisted event (root condition + its child conditions).
 *
 * <p>The root condition carries {@code name} and {@code description}; all conditions share the same
 * {@code workflow_id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventOutput {
  @JsonProperty("event_id")
  @NotBlank
  private String id;

  @JsonProperty("event_name")
  @NotBlank
  private String name;

  @JsonProperty("event_description")
  private String description;

  @JsonProperty("event_workflow_id")
  @NotBlank
  private String workflowId;

  @JsonProperty("event_conditions")
  private List<ConditionOutput> conditions;

  @JsonProperty("event_created_at")
  private Instant createdAt;

  @JsonProperty("event_updated_at")
  private Instant updatedAt;
}
