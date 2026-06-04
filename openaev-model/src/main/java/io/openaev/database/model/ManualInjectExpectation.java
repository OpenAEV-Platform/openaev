package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("MANUAL")
public class ManualInjectExpectation extends TableTopInjectExpectation {

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
