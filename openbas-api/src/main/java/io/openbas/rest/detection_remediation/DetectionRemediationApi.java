package io.openbas.rest.detection_remediation;

import static io.openbas.rest.detection_remediation.DetectionRemediationApi.DETECTION_REMEDIATION_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.rest.detection_remediation.form.DetectionRemediationHealthWebService;
import io.openbas.rest.detection_remediation.service.DetectionRemediationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(DETECTION_REMEDIATION_URI)
@RequiredArgsConstructor
public class DetectionRemediationApi {

  private final DetectionRemediationService detectionRemediationService;
  public static final String DETECTION_REMEDIATION_URI = "api/detection_remediation";

  @GetMapping("/health")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<DetectionRemediationHealthWebService> checkHealth() {
    return ResponseEntity.ok(
        this.detectionRemediationService.checkRemediationDetectionAIWebService());
  }
}
