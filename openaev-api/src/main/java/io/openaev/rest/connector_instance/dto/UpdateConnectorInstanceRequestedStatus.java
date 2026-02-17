package io.openaev.rest.connector_instance.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateConnectorInstanceRequestedStatus {
  @NotNull(message = MANDATORY_MESSAGE)
  @Schema(description = "The connector instance current status")
  @JsonProperty("connector_instance_requested_status")
  private ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus;
}
