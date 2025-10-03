package io.openaev.service.detection_remediation;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.collector.service.CollectorService;
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

  private final DetectionRemediationRepository detectionRemediationRepository;
  private final CollectorService collectorService;

  public String getRulesDetectionRemediationAI(PayloadInput input, String collector) {

    List<AttackPattern> attackPatterns =
        attackPatternService.getAttackPattern(input.getAttackPatternsIds());

    // GET rules from webservice
    DetectionRemediationRequest request = new DetectionRemediationRequest(input, attackPatterns);
    DetectionRemediationAIResponse rules =
        detectionRemediationAIService.callRemediationDetectionAIWebservice(request, collector);

    return rules.formateRules();
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    return detectionRemediationAIService.checkHealthWebservice();
  }

  public DetectionRemediation createDetectionRemediation(Payload payload, String collectorType) {
    Collector collector = collectorService.collectorByType(collectorType);
    return DetectionRemediation.builder().payload(payload).collector(collector).build();
  }

  public DetectionRemediation saveDetectionRemediationRulesByAI(
      DetectionRemediation detectionRemediation, DetectionRemediationAIResponse rules) {
    detectionRemediation.setValues(rules.formateRules());
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.AI);

    return detectionRemediationRepository.save(detectionRemediation);
  }

  public DetectionRemediation getOrCreateDetectionRemediationWithAIRulesByCollector(
      List<DetectionRemediation> detectionRemediations, Payload payload, String collectorType) {
    // GET or Create Detection remediation linked to selected payload and EDR/SIEM
    DetectionRemediation detectionRemediation =
        this.getOrCreateDetectionRemediationByCollector(
            collectorType, detectionRemediations, payload);

    // GET AI rules from webservice
    DetectionRemediationRequest request = new DetectionRemediationRequest(payload);
    DetectionRemediationAIResponse rules =
        detectionRemediationAIService.callRemediationDetectionAIWebservice(request, collectorType);

    return this.saveDetectionRemediationRulesByAI(detectionRemediation, rules);
  }

  private DetectionRemediation getOrCreateDetectionRemediationByCollector(
      String collectorType, List<DetectionRemediation> detectionRemediations, Payload payload) {
    DetectionRemediation detectionRemediation =
        detectionRemediations.stream()
            .filter(remediation -> remediation.getCollector().getType().equals(collectorType))
            .findFirst()
            .orElse(null);

    if (detectionRemediation == null) {
      detectionRemediation = this.createDetectionRemediation(payload, collectorType);
    } else if (!detectionRemediation.getValues().isEmpty()) {
      throw new IllegalStateException("AI Webservice available only for empty content");
    }
    return detectionRemediation;
  }
}
