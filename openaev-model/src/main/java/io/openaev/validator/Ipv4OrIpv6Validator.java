package io.openaev.validator;

import io.openaev.annotation.Ipv4OrIpv6Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import org.apache.commons.validator.routines.InetAddressValidator;

public class Ipv4OrIpv6Validator implements ConstraintValidator<Ipv4OrIpv6Constraint, String[]> {

  @Override
  public boolean isValid(final String[] ips, final ConstraintValidatorContext cxt) {
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return Arrays.stream(ips).allMatch(validator::isValid);
  }

  public static boolean isIpv4(final String ip) {
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return validator.isValidInet4Address(ip);
  }

  public static boolean isIpv6(final String ip) {
    InetAddressValidator validator = InetAddressValidator.getInstance();
    return validator.isValidInet6Address(ip);
  }
}
