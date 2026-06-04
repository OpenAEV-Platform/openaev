package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PREVENTION")
public class PreventionInjectExpectation extends TechnicalInjectExpectation {

  public PreventionInjectExpectation() {
    setType(EXPECTATION_TYPE.PREVENTION);
  }

  @Override
  public String getSuccessLabel() {
    return "Prevented";
  }

  @Override
  public String getFailureLabel() {
    return "Not Prevented";
  }
}
