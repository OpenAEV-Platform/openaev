package io.openbas.service.detection_remediation;

import io.openbas.api.detection_remediation.dto.PayloadInput;
import io.openbas.database.model.AttackPattern;
import io.openbas.rest.attack_pattern.service.AttackPatternService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationService {
  private final DetectionRemediationAIService detectionRemediationAIService;
  private final AttackPatternService attackPatternService;

  public String getRulesDetectionRemediationCrowdstrike(PayloadInput input) {

    List<AttackPattern> attackPatterns =
        attackPatternService.getAttackPattern(input.getAttackPatternsIds());

    // GET rules from webservice
    DetectionRemediationRequest request = new DetectionRemediationRequest(input, attackPatterns);
    DetectionRemediationCrowdstrikeResponse rules =
        detectionRemediationAIService.callRemediationDetectionAIWebservice(request);
    return rules.formateRules();
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    return detectionRemediationAIService.checkHealthWebservice();
  }
}
