package io.openaev.rest.asset.endpoint.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@JsonInclude(NON_NULL)
public class AgentOutput {

  @Schema(description = "Agent id")
  @JsonProperty("agent_id")
  @NotBlank
  private String id;

  @Schema(description = "Agent privilege")
  @JsonProperty("agent_privilege")
  private Agent.PRIVILEGE privilege;

  @Schema(description = "Agent deployment mode")
  @JsonProperty("agent_deployment_mode")
  private Agent.DEPLOYMENT_MODE deploymentMode;

  @Schema(description = "The user who executed the agent")
  @JsonProperty("agent_executed_by_user")
  private String executedByUser;

  @Schema(description = "Agent executor")
  @JsonProperty("agent_executor")
  private ExecutorOutput executor;

  @Schema(
      description =
          "Indicates whether the endpoint is active. "
              + "The endpoint is considered active if it was seen in the last 3 minutes.")
  @JsonProperty("agent_active")
  private boolean isActive;

  @Schema(description = "Instant when agent was last seen")
  @JsonProperty("agent_last_seen")
  private Instant lastSeen;

  @Schema(description = "The version of the agent")
  @JsonProperty("agent_version")
  private String agentVersion;
}
