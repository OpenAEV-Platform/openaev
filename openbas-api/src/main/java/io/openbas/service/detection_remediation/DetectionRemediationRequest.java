package io.openbas.service.detection_remediation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DetectionRemediationRequest {
  @Schema(
      description =
          "Concatenated payload string containing: Name, Type, optional Hostname/Command details, Description, Platform, Attack patterns, Architecture, Arguments")
  String payload;
}
