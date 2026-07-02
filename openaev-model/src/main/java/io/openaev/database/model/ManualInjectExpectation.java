package io.openaev.database.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL_VALUE)
public class ManualInjectExpectation extends TableTopInjectExpectation {}
