package io.openaev.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Utility class providing IP address validation helpers for the OpenAEV platform.
 *
 * <p>Supports both IPv4 and IPv6 addresses as well as their respective CIDR subnet notations. All
 * methods are thread-safe and stateless.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class IpAddressUtils {

  private IpAddressUtils() {}

  /**
   * Lightweight numeric-only guard for IPv4 addresses. Ensures the string contains only digits and
   * dots in four-octet form before delegating to {@link InetAddress}, preventing DNS lookups on
   * hostnames (e.g. {@code example.org}).
   */
  private static final Pattern IPV4_NUMERIC = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  /**
   * Returns {@code true} if {@code value} is a valid IPv4 address (e.g. {@code 192.168.1.1}).
   *
   * @param value the string to test, may be {@code null}
   */
  public static boolean isIpv4Address(String value) {
    if (value == null || !IPV4_NUMERIC.matcher(value).matches()) {
      return false;
    }
    try {
      return InetAddress.getByName(value) instanceof Inet4Address;
    } catch (UnknownHostException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} if {@code value} is a valid IPv4 CIDR subnet (e.g. {@code 192.168.1.0/24},
   * prefix 0–32).
   *
   * @param value the string to test, may be {@code null}
   */
  public static boolean isIpv4Subnet(String value) {
    if (value == null) {
      return false;
    }
    int slashIndex = value.lastIndexOf('/');
    if (slashIndex < 0) {
      return false;
    }
    String addressPart = value.substring(0, slashIndex);
    String prefixPart = value.substring(slashIndex + 1);
    if (!IPV4_NUMERIC.matcher(addressPart).matches()) {
      return false;
    }
    try {
      int prefixLength = Integer.parseInt(prefixPart);
      return prefixLength >= 0
          && prefixLength <= 32
          && InetAddress.getByName(addressPart) instanceof Inet4Address;
    } catch (NumberFormatException | UnknownHostException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} if {@code value} is a valid IPv6 address (e.g. {@code 2001:db8::1}).
   *
   * @param value the string to test, may be {@code null}
   */
  public static boolean isIpv6Address(String value) {
    if (value == null || !value.contains(":")) {
      return false;
    }
    try {
      return InetAddress.getByName(value) instanceof Inet6Address;
    } catch (UnknownHostException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} if {@code value} is a valid IPv6 CIDR subnet (e.g. {@code 2001:db8::/32},
   * prefix 0–128).
   *
   * @param value the string to test, may be {@code null}
   */
  public static boolean isIpv6Subnet(String value) {
    if (value == null || !value.contains(":")) {
      return false;
    }
    int slashIndex = value.lastIndexOf('/');
    if (slashIndex < 0) {
      return false;
    }
    String addressPart = value.substring(0, slashIndex);
    String prefixPart = value.substring(slashIndex + 1);
    try {
      int prefixLength = Integer.parseInt(prefixPart);
      return prefixLength >= 0
          && prefixLength <= 128
          && InetAddress.getByName(addressPart) instanceof Inet6Address;
    } catch (NumberFormatException | UnknownHostException e) {
      return false;
    }
  }
}
