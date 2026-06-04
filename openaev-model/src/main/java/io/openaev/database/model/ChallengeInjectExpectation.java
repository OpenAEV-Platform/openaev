package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CHALLENGE")
public class ChallengeInjectExpectation extends TableTopInjectExpectation {

  public ChallengeInjectExpectation() {
    setType(EXPECTATION_TYPE.CHALLENGE);
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
