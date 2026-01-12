package io.openaev.validator;

import io.openaev.annotation.Ipv4OrIpv6Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import org.apache.commons.validator.routines.InetAddressValidator;

/**
 * Constraint validator for the {@link Ipv4OrIpv6Constraint} annotation.
 *
 * <p>This validator checks that all strings in an array are valid IPv4 or IPv6 addresses. It uses
 * Apache Commons Validator's {@link InetAddressValidator} for the actual validation logic.
 *
 * <p>Validation behavior:
 *
 * <ul>
 *   <li>Null or empty arrays are considered valid (use {@code @NotEmpty} if required)
 *   <li>All elements must be valid IPv4 or IPv6 addresses for the array to be valid
 *   <li>Any invalid address causes the entire validation to fail
 * </ul>
 *
 * @see Ipv4OrIpv6Constraint
 */
public class Ipv4OrIpv6Validator implements ConstraintValidator<Ipv4OrIpv6Constraint, String[]> {

  /**
   * Validates that all strings in the array are valid IP addresses.
   *
   * @param ips the array of IP addresses to validate
   * @param cxt the constraint validator context
   * @return {@code true} if all addresses are valid (or array is null/empty), {@code false}
   *     otherwise
   */
  @Override
  public boolean isValid(final String[] ips, final ConstraintValidatorContext cxt) {
    if (ips == null || ips.length == 0) {
      return true;
    }
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return Arrays.stream(ips).allMatch(validator::isValid);
  }

  /**
   * Checks if a string is a valid IPv4 address.
   *
   * @param ip the string to validate
   * @return {@code true} if the string is a valid IPv4 address, {@code false} otherwise
   */
  public static boolean isIpv4(final String ip) {
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return validator.isValidInet4Address(ip);
  }

  /**
   * Checks if a string is a valid IPv6 address.
   *
   * @param ip the string to validate
   * @return {@code true} if the string is a valid IPv6 address, {@code false} otherwise
   */
  public static boolean isIpv6(final String ip) {
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return validator.isValidInet6Address(ip);
  }
}
