package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.LOGGING_VALUE)
public class LoggingInjectExpectation extends TechnicalInjectExpectation {

  public LoggingInjectExpectation() {
    setSuccessLabel("Logged");
    setFailureLabel("Not Logged");
  }
}
