package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION_VALUE)
public class PreventionInjectExpectation extends TechnicalInjectExpectation {

  public PreventionInjectExpectation() {
    setSuccessLabel("Prevented");
    setFailureLabel("Not Prevented");
  }
}
