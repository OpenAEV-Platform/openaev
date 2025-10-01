package io.openaev.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.openaev.validator.Ipv4OrIpv6Validator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = Ipv4OrIpv6Validator.class)
@ReportAsSingleViolation
public @interface Ipv4OrIpv6Constraint {
  String message() default "must be ipv4 or ipv6";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
