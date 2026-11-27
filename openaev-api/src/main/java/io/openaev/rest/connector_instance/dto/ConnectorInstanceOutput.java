package io.openaev.rest.connector_instance.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
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

  @Schema(
      description =
          "Container image the composer deploys (image:version), absent for a built-in instance")
  @JsonProperty("connector_image")
  @JsonInclude(NON_NULL)
  private String image;

  @Schema(description = "When the container last started, as reported by the composer")
  @JsonProperty("connector_instance_started_at")
  @JsonInclude(NON_NULL)
  private Instant startedAt;

  @Schema(description = "Container restart count, as reported by the composer")
  @JsonProperty("connector_instance_restart_count")
  @JsonInclude(NON_NULL)
  private Integer restartCount;

  @Schema(description = "Whether the container is in a reboot loop")
  @JsonProperty("connector_instance_is_in_reboot_loop")
  @JsonInclude(NON_NULL)
  private Boolean inRebootLoop;

  @Schema(
      description =
          "Digest of the image the container runs (algo:hex), as last reported by the composer;"
              + " absent when unknown")
  @JsonProperty("connector_instance_image_digest")
  @JsonInclude(NON_NULL)
  private String imageDigest;
}
