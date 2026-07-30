package io.openaev.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.openaev.validator.DomainValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validation constraint annotation that ensures a field contains a valid RFC 1034 domain name.
 *
 * <p>This constraint uses {@link DomainValidator} to validate that the annotated field contains a
 * properly formatted RFC 1034 domain name format.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @DomainConstraint
 * @Column(name = "asset_hostname")
 * private String asset_hostname;
 * }</pre>
 *
 * @see DomainValidator
 */
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = DomainValidator.class)
@ReportAsSingleViolation
public @interface DomainConstraint {

  /**
   * The error message to display when validation fails.
   *
   * @return the error message
   */
  String message() default "must be a valid domain name according to RFC 1034";

  /**
   * Validation groups this constraint belongs to.
   *
   * @return the validation groups
   */
  Class<?>[] groups() default {};

  /**
   * Payload for clients to associate metadata with the constraint.
   *
   * @return the payload classes
   */
  Class<? extends Payload>[] payload() default {};
}
