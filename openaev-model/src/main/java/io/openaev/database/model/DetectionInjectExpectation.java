package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("DETECTION")
public class DetectionInjectExpectation extends TechnicalInjectExpectation {

  public DetectionInjectExpectation() {
    setType(EXPECTATION_TYPE.DETECTION);
  }

  @Override
  public String getSuccessLabel() {
    return "Detected";
  }

  @Override
  public String getFailureLabel() {
    return "Not Detected";
  }
}
