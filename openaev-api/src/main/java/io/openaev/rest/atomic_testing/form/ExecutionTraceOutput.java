package io.openaev.rest.atomic_testing.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.rest.asset.endpoint.form.AgentOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(NON_NULL)
@Builder
@Schema(description = "Represents a single execution trace detail")
public class ExecutionTraceOutput {

  @NotNull
  @JsonProperty("execution_status")
  @Schema(
      description = "The status of the execution trace",
      example = "SUCCESS, ERROR, COMMAND_NOT_FOUND, WARNING, COMMAND_CANNOT_BE_EXECUTED..")
  private ExecutionTraceStatus status;

  @NotNull
  @JsonProperty("execution_time")
  private Instant time;

  @NotNull
  @JsonProperty("execution_message")
  @Schema(description = "A detailed message describing the execution")
  private String message;

  @NotNull
  @JsonProperty("execution_action")
  @Schema(
      description = "The action that created this execution trace",
      example =
          "START, PREREQUISITE_CHECK, PREREQUISITE_EXECUTION, EXECUTION, CLEANUP_EXECUTION or COMPLETE")
  private ExecutionTraceAction action;

  @JsonProperty("execution_agent")
  private AgentOutput agent;
}
