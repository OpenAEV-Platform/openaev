package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

  @JsonProperty("step_limit_execution")
  private int limitExecution;

  @JsonProperty("step_status")
  private String status;

  @JsonProperty("step_data")
  private String data;

  @JsonProperty("step_created_at")
  private Instant createdAt;

  @JsonProperty("step_updated_at")
  private Instant updatedAt;
}
