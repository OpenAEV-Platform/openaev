package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.ExpectationTypeString.MANUAL)
public class ManualInjectExpectation extends TechnicalInjectExpectation {

  public ManualInjectExpectation() {
    setType(EXPECTATION_TYPE.MANUAL);
  }

  @Override
  public String getSuccessLabel() {
    return "Successful";
  }

  @Override
  public String getFailureLabel() {
    return "Failed";
  }
}
