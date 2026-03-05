package io.openaev.api.chaining.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.openaev.api.chaining.validator.TimeOutDurationValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Class-level constraint that ensures hours and minutes cannot both be zero when the timeout
 * feature is enabled.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @ValidTimeOutDuration
 * public class ChainingTimeOutInput { ... }
 * }</pre>
 *
 * @see TimeOutDurationValidator
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = TimeOutDurationValidator.class)
@ReportAsSingleViolation
public @interface ValidTimeOutDuration {

  String message() default
      "Timeout hours and minutes cannot both be zero when the timeout is enabled";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
