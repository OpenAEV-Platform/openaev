package io.openaev.rest.connector_instance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public class ConnectorInstanceOutput {
  @JsonProperty("connector_instance_id")
  @NotBlank
  private String id;

  @JsonProperty("connector_instance_current_status")
  @NotNull
  private ConnectorInstance.CURRENT_STATUS_TYPE currentStatus;

  @JsonProperty("connector_instance_requested_status")
  private ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus;
}
