package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("DOCUMENT")
public class DocumentInjectExpectation extends InjectExpectation {

  public DocumentInjectExpectation() {
    setType(EXPECTATION_TYPE.DOCUMENT);
  }
}
