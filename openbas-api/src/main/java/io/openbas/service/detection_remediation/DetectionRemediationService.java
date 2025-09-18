package io.openbas.service.detection_remediation;

import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.DetectionRemediation;
import io.openbas.database.repository.AttackPatternRepository;
import io.openbas.database.repository.DetectionRemediationRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRemediationService {

  private final DetectionRemediationRepository detectionRemediationRepository;
  private final AttackPatternRepository attackPatternRepository;

  public DetectionRemediation getAndCheckDetectionRemediation(String idDetectionRemediation) {
    DetectionRemediation detectionRemediation =
        detectionRemediationRepository
            .findById(idDetectionRemediation)
            .orElseThrow(
                () -> new ElementNotFoundException("Current detection remediation not found"));

    // AI cannot replace existing content
    if (!detectionRemediation.getValues().isEmpty())
      throw new IllegalStateException("AI Webservice available only for empty content");

    return detectionRemediation;
  }

  public List<AttackPattern> getAttackPattern(List<String> idsAttackPattern) {
    return attackPatternRepository.findAllByIdIn(idsAttackPattern);
  }

  public String saveDetectionRemediationRulesByAI(
      DetectionRemediation detectionRemediation, DetectionRemediationCrowdstrikeResponse rules) {
    detectionRemediation.setValues(rules.formateRules());
    detectionRemediation.setAiRuleCreationDate(new Date().toInstant());

    detectionRemediationRepository.save(detectionRemediation);
    return detectionRemediation.getValues();
  }
}
