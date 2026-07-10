package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION_VALUE)
public class DetectionInjectExpectation extends TechnicalInjectExpectation {

  public DetectionInjectExpectation() {
    setSuccessLabel("Detected");
    setFailureLabel("Not Detected");
  }
}
