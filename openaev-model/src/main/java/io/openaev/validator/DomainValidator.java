package io.openaev.validator;

import io.openaev.annotation.DomainConstraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DomainValidator implements ConstraintValidator<DomainConstraint, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || value.matches("^([A-Za-z0-9\\-]+\\.)*[A-Za-z0-9\\-]+$");
  }
}
