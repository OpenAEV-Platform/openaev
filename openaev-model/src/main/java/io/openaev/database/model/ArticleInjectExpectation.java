package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ARTICLE")
public class ArticleInjectExpectation extends TableTopInjectExpectation {

  public ArticleInjectExpectation() {
    setType(EXPECTATION_TYPE.ARTICLE);
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
