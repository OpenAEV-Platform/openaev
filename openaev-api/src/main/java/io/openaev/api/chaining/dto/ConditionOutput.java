package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionKeySubtype;
import io.openaev.database.model.ConditionKeyType;
import lombok.*;

/** Nested output DTO for a single condition inside an event. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConditionOutput {
  @JsonProperty("condition_id")
  private String id;

  @JsonProperty("condition_key_type")
  private ConditionKeyType keyType;

  @JsonProperty("condition_key_subtype")
  private ConditionKeySubtype keySubtype;

  @JsonProperty("condition_type")
  private String type;

  @JsonProperty("condition_value")
  private String value;

  @JsonProperty("condition_parent_id")
  private String conditionParentId;
}
