package io.openaev.rest.inject.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InjectExecutionInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty(
      "execution_message") // FIXME: should be changed to execution_raw_output in implant repo
  private String message;

  @JsonProperty("execution_output_structured")
  private String outputStructured;

  @JsonProperty("execution_output_raw")
  private String outputRaw;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("execution_status")
  private String status;

  @Schema(description = "Duration of the execution in miliseconds")
  @JsonProperty("execution_duration")
  private int duration;

  @JsonProperty("execution_action")
  private InjectExecutionAction action;
}
