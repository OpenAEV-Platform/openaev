package io.openaev.service.detection_remediation;

import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.DetectionRemediation;
import io.openaev.database.model.Payload;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
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
  private final SecurityPlatformRepository securityPlatformRepository;
  private final AiMetricCollector aiMetricCollector;

  /**
   * Security platforms linked to the detection remediations of a payload (remediation gating).
   *
   * @param payloadId the payload identifier
   * @return the distinct security platforms carrying remediation rules for this payload
   */
  public List<SecurityPlatform> securityPlatformsForPayload(String payloadId) {
    return detectionRemediationRepository.findSecurityPlatformsByPayloadId(payloadId);
  }

  /**
   * Security platforms linked to the detection remediations of an inject's payload (remediation
   * gating).
   *
   * @param injectId the inject identifier
   * @return the distinct security platforms carrying remediation rules for this inject
   */
  public List<SecurityPlatform> securityPlatformsForInject(String injectId) {
    return detectionRemediationRepository.findSecurityPlatformsByInjectId(injectId);
  }

  public SecurityPlatform securityPlatform(@NotNull String securityPlatformId) {
    return securityPlatformRepository
        .findById(securityPlatformId)
        .orElseThrow(
            () ->
                new ElementNotFoundException("Security platform not found: " + securityPlatformId));
  }

  private @NotNull String getDetectionRemediationAIResponse(
      SecurityPlatform securityPlatform, String agentSlug, DetectionRemediationRequest request) {
    // Telemetry: counted before the routing branch (backend-agnostic).
    aiMetricCollector.recordDetectionRemediation(securityPlatform.getName());
    if (xtmOneConfig.isConfigured()) {
      return generateRulesViaXtmOne(request, securityPlatform, agentSlug).formateRules();
    } else { // Legacy webservice path
      return detectionRemediationAIService
          .callRemediationDetectionAIWebservice(request, securityPlatform.getName())
          .formateRules();
    }
  }

  public String getRulesDetectionRemediationAI(
      PayloadInput input, String securityPlatformId, String agentSlug) {
    SecurityPlatform securityPlatform = securityPlatform(securityPlatformId);
    List<AttackPattern> attackPatterns =
        attackPatternService.getAttackPattern(input.getAttackPatternsIds());
    DetectionRemediationRequest request = new DetectionRemediationRequest(input, attackPatterns);
    return getDetectionRemediationAIResponse(securityPlatform, agentSlug, request);
  }

  public DetectionRemediationHealthResponse checkHealthWebservice() {
    return detectionRemediationAIService.checkHealthWebservice();
  }

  public DetectionRemediation createDetectionRemediation(
      Payload payload, String securityPlatformId) {
    SecurityPlatform securityPlatform = securityPlatform(securityPlatformId);
    return DetectionRemediation.builder()
        .payload(payload)
        .securityPlatform(securityPlatform)
        .build();
  }

  public DetectionRemediation getOrCreateDetectionRemediationWithAIRulesBySecurityPlatform(
      List<DetectionRemediation> detectionRemediations,
      Payload payload,
      String securityPlatformId,
      List<AttackPattern> attackPatterns,
      String agentSlug) {
    // GET or Create Detection remediation linked to selected payload and security platform
    DetectionRemediation detectionRemediation =
        this.getOrCreateDetectionRemediationBySecurityPlatform(
            securityPlatformId, detectionRemediations, payload);
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.AI);
    DetectionRemediationRequest request = new DetectionRemediationRequest(payload, attackPatterns);
    detectionRemediation.setValues(
        getDetectionRemediationAIResponse(
            detectionRemediation.getSecurityPlatform(), agentSlug, request));
    return detectionRemediationRepository.save(detectionRemediation);
  }

  private DetectionRemediationAIResponse generateRulesViaXtmOne(
      DetectionRemediationRequest request, SecurityPlatform securityPlatform, String agentSlug) {
    if (agentSlug == null || agentSlug.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent slug is required when XTM One is configured");
    }
    String prompt =
        "Generate "
            + securityPlatform.getName()
            + " detection and remediation rules for the following payload context:\n\n"
            + request.getPayload();
    String raw = xtmOneClient.callAgentSync(agentSlug, prompt, null);
    if (raw == null) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "XTM One AI service is unavailable or returned no result");
    }
    Pair<String, ? extends Class<? extends DetectionRemediationAIResponse>> detectionInfo =
        detectionRemediationAIService.inferUrlAndType(securityPlatform.getName());
    return detectionRemediationAIService.getDetectionRemediationAIResponse(
        raw,
        detectionInfo.getRight(),
        "Failed to parse XTM One response for security platform " + securityPlatform.getName());
  }

  private DetectionRemediation getOrCreateDetectionRemediationBySecurityPlatform(
      String securityPlatformId,
      List<DetectionRemediation> detectionRemediations,
      Payload payload) {
    DetectionRemediation detectionRemediation =
        detectionRemediations.stream()
            .filter(
                remediation -> remediation.getSecurityPlatform().getId().equals(securityPlatformId))
            .findFirst()
            .orElse(null);

    if (detectionRemediation == null) {
      detectionRemediation = this.createDetectionRemediation(payload, securityPlatformId);
    } else if (!detectionRemediation.getValues().isEmpty()) {
      throw new IllegalStateException("AI Webservice available only for empty content");
    }
    return detectionRemediation;
  }
}
