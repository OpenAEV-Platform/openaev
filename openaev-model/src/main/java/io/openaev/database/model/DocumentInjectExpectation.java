package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.DOCUMENT_VALUE)
public class DocumentInjectExpectation extends BaseInjectExpectation {

  public DocumentInjectExpectation() {}
}
