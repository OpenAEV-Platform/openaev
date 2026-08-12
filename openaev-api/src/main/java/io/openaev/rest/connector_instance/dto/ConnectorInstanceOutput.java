package io.openaev.rest.connector_instance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConnectorInstance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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

  @JsonProperty("connector_instance_restart_count")
  @Schema(description = "Connector instance restart count")
  private Integer restartCount;

  @JsonProperty("connector_instance_started_at")
  @Schema(description = "Last time the connector instance container started")
  private Instant startedAt;

  @JsonProperty("connector_instance_is_in_reboot_loop")
  @Schema(description = "True when the connector instance keeps restarting without staying up")
  private boolean isInRebootLoop;
}
