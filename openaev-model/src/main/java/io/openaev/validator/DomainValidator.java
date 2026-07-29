package io.openaev.validator;

import io.micrometer.common.util.StringUtils;
import io.openaev.annotation.DomainConstraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class DomainValidator implements ConstraintValidator<DomainConstraint, String> {
  private static final Pattern PATTERN =
      Pattern.compile(
          "^(([A-Za-z0-9]+[A-Za-z0-9-]+[A-Za-z0-9]+)\\.)*[A-Za-z0-9]+[A-Za-z0-9-]+[A-Za-z0-9]+$");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {

    return StringUtils.isEmpty(value) || PATTERN.matcher(value).matches();
  }
}
