package io.openaev.validator;

import io.openaev.annotation.DomainConstraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DomainValidator  implements ConstraintValidator<DomainConstraint, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return org.apache.commons.validator.routines.DomainValidator.getInstance().isValid(value);
    }
}
