package io.openaev.utils.fixtures;

import io.openaev.database.model.DetectionRemediation;

public class DetectionRemediationFixture {

  public static DetectionRemediation createDefaultDetectionRemediation() {
    DetectionRemediation detectionRemediation = new DetectionRemediation();
    detectionRemediation.setValues("I have a rule");
    detectionRemediation.setAuthorRule(DetectionRemediation.AUTHOR_RULE.HUMAN);
    return detectionRemediation;
  }
}
