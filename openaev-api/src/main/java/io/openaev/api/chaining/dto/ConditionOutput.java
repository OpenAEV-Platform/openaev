package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nested output DTO for a single condition inside an event. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConditionOutput {
  @JsonProperty("condition_id")
  private String id;

  @JsonProperty("condition_key_type")
  private String keyType;

  @JsonProperty("condition_type")
  private String type;

  @JsonProperty("condition_value")
  private String value;

  @JsonProperty("condition_parent_id")
  private String conditionParentId;
}
