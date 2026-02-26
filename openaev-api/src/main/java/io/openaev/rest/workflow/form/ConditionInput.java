package io.openaev.rest.workflow.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionType;
import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class ConditionInput {

  @JsonProperty("condition_key")
  @Nullable
  private String key;

  @JsonProperty("condition_value")
  @Nullable
  private String value;

  @JsonProperty("condition_type")
  private ConditionType type;

  @JsonProperty("step_from_id")
  @Nullable
  private String stepFromId;

  @JsonProperty("condition_parent_id")
  @Nullable
  private String conditionParentId;
}
