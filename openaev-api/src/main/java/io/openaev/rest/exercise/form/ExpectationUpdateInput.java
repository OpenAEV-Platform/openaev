package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ExpectationUpdateInput {
  @JsonProperty("source_id")
  @NotNull
  private String sourceId;

  @JsonProperty("source_type")
  @NotNull
  private String sourceType;

  @JsonProperty("source_name")
  @NotNull
  private String sourceName;

  @JsonProperty("expectation_score")
  @NotNull
  private Double score;
}
