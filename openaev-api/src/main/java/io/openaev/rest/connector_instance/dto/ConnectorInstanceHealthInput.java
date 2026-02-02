package io.openaev.rest.connector_instance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectorInstanceHealthInput {

  @Schema(description = "Connector instance restart count")
  @JsonProperty("connector_instance_restart_count")
  private Integer restartCount;

  @Schema(description = "The connector instance id")
  @JsonProperty("connector_instance_started_at")
  private Instant startedAt;

  @Schema(description = "The connector instance id")
  @JsonProperty("connector_instance_is_in_reboot_loop")
  private boolean isInRebootLoop;
}
