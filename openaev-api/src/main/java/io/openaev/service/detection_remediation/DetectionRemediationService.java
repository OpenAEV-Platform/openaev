package io.openaev.service.detection_remediation;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationService {
  private final DetectionRemediationAIService detectionRemediationAIService;
  private final AttackPatternService attackPatternService;
  private final XtmOneConfig xtmOneConfig;
  private final XtmOneClient xtmOneClient;

  private final DetectionRemediationRepository detectionRemediationRepository;
  private final CollectorTypeRepository collectorTypeRepository;
  private final AiMetricCollector aiMetricCollector;

  private @NotNull String getDetectionRemediationAIResponse(
      String collectorType, String agentSlug, DetectionRemediationRequest request) {
    // Telemetry: counted before the routing branch (backend-agnostic).
    aiMetricCollector.recordDetectionRemediation(collectorType);
    if (xtmOneConfig.isConfigured()) {
      return generateRulesViaXtmOne(request, collectorType, agentSlug).formateRules();
    } else { // Legacy webservice path
      return detectionRemediationAIService
          .callRemediationDetectionAIWebservice(request, collectorType)
          .formateRules();
    }
  }

  public String getRulesDetectionRemediationAI(
      PayloadInput input, String collector, String agentSlug) {
    List<AttackPattern> attackPatterns =
        attackPatternService.getAttackPattern(input.getAttackPatternsIds());
    DetectionRemediationRequest request = new DetectionRemediationRequest(input, attackPatterns);
    return getDetectionRemediationAIResponse(collector, agentSlug, request);
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    return detectionRemediationAIService.checkHealthWebservice();
  }

  public DetectionRemediation createDetectionRemediation(Payload payload, String collectorType) {
    CollectorType type =
        collectorTypeRepository
            .findByName(collectorType)
            .orElseThrow(
                () -> new ElementNotFoundException("Collector type not found: " + collectorType));
    return DetectionRemediation.builder().payload(payload).collectorType(type).build();
  }

  public DetectionRemediation getOrCreateDetectionRemediationWithAIRulesByCollector(
      List<DetectionRemediation> detectionRemediations,
      Payload payload,
      String collectorType,
      List<AttackPattern> attackPatterns,
      String agentSlug) {
    // GET or Create Detection remediation linked to selected payload and EDR/SIEM
    DetectionRemediation detectionRemediation =
        this.getOrCreateDetectionRemediationByCollector(
            collectorType, detectionRemediations, payload);
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.AI);
    DetectionRemediationRequest request = new DetectionRemediationRequest(payload, attackPatterns);
    detectionRemediation.setValues(
        getDetectionRemediationAIResponse(collectorType, agentSlug, request));
    return detectionRemediationRepository.save(detectionRemediation);
  }

  private DetectionRemediationAIResponse generateRulesViaXtmOne(
      DetectionRemediationRequest request, String collectorType, String agentSlug) {
    if (agentSlug == null || agentSlug.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent slug is required when XTM One is configured");
    }
    String prompt =
        "Generate "
            + collectorType
            + " detection and remediation rules for the following payload context:\n\n"
            + request.getPayload();
    String raw = xtmOneClient.callAgentSync(agentSlug, prompt, null);
    if (raw == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "XTM One AI service is unavailable or returned no result");
    }
    Pair<String, ? extends Class<? extends DetectionRemediationAIResponse>> detectionInfo =
        detectionRemediationAIService.inferUrlAndType(collectorType);
    return detectionRemediationAIService.getDetectionRemediationAIResponse(
        raw,
        detectionInfo.getRight(),
        "Failed to parse XTM One response for collector type " + collectorType);
  }

  private DetectionRemediation getOrCreateDetectionRemediationByCollector(
      String collectorType, List<DetectionRemediation> detectionRemediations, Payload payload) {
    DetectionRemediation detectionRemediation =
        detectionRemediations.stream()
            .filter(remediation -> remediation.getCollectorType().getName().equals(collectorType))
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
