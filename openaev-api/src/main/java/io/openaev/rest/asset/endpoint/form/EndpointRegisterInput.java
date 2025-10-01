package io.openaev.rest.asset.endpoint.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Agent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EndpointRegisterInput extends EndpointInput {

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("asset_external_reference")
  private String externalReference;

  @JsonProperty("agent_is_service")
  private boolean isService = true;

  @JsonProperty("agent_is_elevated")
  private boolean isElevated = true;

  @JsonProperty("agent_executed_by_user")
  private String executedByUser = Agent.ADMIN_SYSTEM_WINDOWS;

  @JsonProperty("agent_installation_mode")
  private String installationMode;

  @JsonProperty("agent_installation_directory")
  private String installationDirectory;

  @JsonProperty("agent_service_name")
  private String serviceName;

  private String seenIp;
}
