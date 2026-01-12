package io.openaev.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.openaev.validator.Ipv4OrIpv6Validator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Validation constraint annotation that ensures a field contains a valid IPv4 or IPv6 address.
 *
 * <p>This constraint uses {@link Ipv4OrIpv6Validator} to validate that the annotated field contains
 * a properly formatted IP address in either IPv4 (e.g., "192.168.1.1") or IPv6 (e.g.,
 * "2001:0db8:85a3:0000:0000:8a2e:0370:7334") format.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Ipv4OrIpv6Constraint
 * @Column(name = "endpoint_ip")
 * private String ipAddress;
 * }</pre>
 *
 * @see Ipv4OrIpv6Validator
 */
@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = Ipv4OrIpv6Validator.class)
@ReportAsSingleViolation
public @interface Ipv4OrIpv6Constraint {

  /**
   * The error message to display when validation fails.
   *
   * @return the error message
   */
  String message() default "must be ipv4 or ipv6";

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
