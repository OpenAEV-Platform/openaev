package io.openaev.service.detection_remediation;

import jakarta.validation.constraints.NotNull;

public interface DetectionRemediationAIResponse {
  @NotNull
  String formateRules();
}
