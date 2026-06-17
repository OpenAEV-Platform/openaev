package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.ExpectationTypeString.DOCUMENT)
public class DocumentInjectExpectation extends BaseInjectExpectation {

  public DocumentInjectExpectation() {
    setType(EXPECTATION_TYPE.DOCUMENT);
  }
}
