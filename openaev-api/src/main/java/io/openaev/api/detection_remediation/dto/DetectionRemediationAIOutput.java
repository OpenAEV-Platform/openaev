package io.openaev.api.detection_remediation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DetectionRemediationAIOutput {
  String rules;
}
