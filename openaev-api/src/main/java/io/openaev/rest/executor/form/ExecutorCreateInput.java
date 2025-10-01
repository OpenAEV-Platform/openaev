package io.openaev.rest.executor.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutorCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("executor_id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("executor_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("executor_type")
  private String type;

  @JsonProperty("executor_platforms")
  private String[] platforms;
}
