package io.openaev.rest.workflow.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepFieldScope;
import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class StepInput {

  @JsonProperty("step_action_class")
  private StepActionClass stepAction;

  @JsonProperty("step_limit_execution")
  private int limitExecution;

  @JsonProperty("step_data")
  @Nullable
  private String data;

  @JsonProperty("step_output_parser")
  @Nullable
  private String outputParser;

  @JsonProperty("step_field_scope")
  @Nullable
  private StepFieldScope fieldScope;
}
