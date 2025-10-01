package io.openaev.service.detection_remediation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Health check response of the detection/remediation service.")
public class DetectionRemediationHealthResponse {
  @Schema(
      description = "Status of the web service. Only one possible value: \"healthy\"",
      example = "healthy")
  String status;

  @Schema(description = "Timestamp of the request", example = "2025-09-09T12:08:07.489773Z")
  String timestamp;

  @Schema(description = "Name of the service", example = "remediation-detection-webservice")
  String service;

  @Schema(description = "Version of the service", example = "0.1.0")
  String version;

  @JsonProperty("up_time")
  @Schema(
      description =
          "Elapsed time between request initiation and service start. (format HH:MM:SS.ffffff,)",
      example = "2:07:39.269613")
  String upTime;
}
