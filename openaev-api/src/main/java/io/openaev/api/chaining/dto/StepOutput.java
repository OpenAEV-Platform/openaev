package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.StepStatus;
import java.time.Instant;
import java.util.List;
import lombok.*;

/** Output DTO for Step template CRUD operations. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepOutput {

  @JsonProperty("step_id")
  private String id;

  @JsonProperty("step_status")
  private StepStatus status;

  @JsonProperty("step_condition_key_types")
  private List<ConditionKeyType> conditionKeyTypes;

  @JsonProperty("step_data")
  private JsonNode data;

  @JsonProperty("step_created_at")
  private Instant createdAt;

  @JsonProperty("step_updated_at")
  private Instant updatedAt;
}
