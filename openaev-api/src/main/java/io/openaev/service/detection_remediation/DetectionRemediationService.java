package io.openaev.service.detection_remediation;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.collectors.utils.CollectorsUtils;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationService {
  private final DetectionRemediationAIService detectionRemediationAIService;
  private final AttackPatternService attackPatternService;

  private final DetectionRemediationRepository detectionRemediationRepository;
  private final CollectorService collectorService;

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

  public DetectionRemediation createDetectionRemediation(Payload payload, String collectorType) {
    Collector collector = collectorService.collectorByType(collectorType);
    return DetectionRemediation.builder().payload(payload).collector(collector).build();
  }

  public DetectionRemediation saveDetectionRemediationRulesByAI(
      DetectionRemediation detectionRemediation, DetectionRemediationCrowdstrikeResponse rules) {
    detectionRemediation.setValues(rules.formateRules());
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.AI);

    return detectionRemediationRepository.save(detectionRemediation);
  }

  public DetectionRemediation getOrCreateDetectionRemediationWithAIRulesByCollector(
      List<DetectionRemediation> detectionRemediations, Payload payload, String collectorType) {

    return switch (collectorType) {
      case CollectorsUtils.CROWDSTRIKE -> {
        // GET or Create Detection remediation linked to selected payload and EDR/SIEM
        DetectionRemediation detectionRemediation =
            this.getOrCreateDetectionRemediationByCollector(
                CollectorsUtils.CROWDSTRIKE, detectionRemediations, payload);

        // GET AI rules from webservice
        DetectionRemediationRequest request = new DetectionRemediationRequest(payload);
        DetectionRemediationCrowdstrikeResponse rules =
            detectionRemediationAIService.callRemediationDetectionAIWebservice(request);

        yield this.saveDetectionRemediationRulesByAI(detectionRemediation, rules);
      }
      case CollectorsUtils.MICROSOFT_DEFENDER ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft defender not implemented");

      case CollectorsUtils.MICROSOFT_SENTINEL ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft sentinel not implemented");
      default ->
          throw new IllegalStateException("Collector :\"" + collectorType + "\" unsupported");
    };
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
