package io.openaev.utils;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class providing IP address validation helpers for the OpenAEV platform.
 *
 * <p>Supports both IPv4 and IPv6 addresses as well as their respective CIDR subnet notations. All
 * methods are thread-safe and stateless.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
@Slf4j
public class IpAddressUtils {

  /**
   * Lightweight numeric-only guard for IPv4 addresses. Ensures the string contains only digits and
   * dots in four-octet form before delegating to {@link InetAddress}, preventing DNS lookups on
   * hostnames (e.g. {@code example.org}).
   */
  private static final Pattern IPV4_NUMERIC = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  /** Safety limit for subnet expansion to keep chaining scope resolution bounded. */
  private static final int MAX_EXPANDED_HOSTS = 4096;

  private IpAddressUtils() {}

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

  /**
   * Returns {@code true} if {@code ip} falls within the CIDR {@code subnet}.
   *
   * <p>Supports both IPv4 (e.g. {@code 192.168.1.0/24}) and IPv6 (e.g. {@code 2001:db8::/32}).
   * Returns {@code false} when the address families differ or parsing fails.
   *
   * @param ip the IP address to test
   * @param cidr the CIDR subnet to test against
   */
  public static boolean isIpInSubnet(String ip, String cidr) {
    try {
      int slashIndex = cidr.lastIndexOf('/');
      String subnetAddress = cidr.substring(0, slashIndex);
      int prefixLength = Integer.parseInt(cidr.substring(slashIndex + 1));

      byte[] ipBytes = InetAddress.getByName(ip).getAddress();
      byte[] subnetBytes = InetAddress.getByName(subnetAddress).getAddress();

      if (ipBytes.length != subnetBytes.length) {
        return false;
      }

      int fullBytes = prefixLength / 8;
      int remainingBits = prefixLength % 8;

      for (int i = 0; i < fullBytes; i++) {
        if (ipBytes[i] != subnetBytes[i]) {
          return false;
        }
      }
      if (remainingBits > 0) {
        int mask = 0xFF & (0xFF << (8 - remainingBits));
        if ((ipBytes[fullBytes] & mask) != (subnetBytes[fullBytes] & mask)) {
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      log.warn("Failed to evaluate IP {} against subnet {}: {}", ip, cidr, e.getMessage());
      return false;
    }
  }

  /**
   * Expands a CIDR subnet to individual host IP addresses.
   *
   * <p>For IPv4, network and broadcast addresses are excluded when the subnet has dedicated host
   * addresses (prefix <= 30). For IPv6, all addresses in range are considered host addresses.
   *
   * @param cidr subnet in CIDR notation
   * @return expanded host IP addresses or an empty list if CIDR is invalid/unsupported
   */
  public static List<String> expandSubnetToHostIps(String cidr) {
    if (!(isIpv4Subnet(cidr) || isIpv6Subnet(cidr))) {
      return List.of();
    }
    try {
      int slashIndex = cidr.lastIndexOf('/');
      String subnetAddress = cidr.substring(0, slashIndex);
      int prefixLength = Integer.parseInt(cidr.substring(slashIndex + 1));

      byte[] subnetBytes = InetAddress.getByName(subnetAddress).getAddress();
      boolean ipv4 = subnetBytes.length == 4;
      int addressBitLength = subnetBytes.length * 8;
      int hostBits = addressBitLength - prefixLength;
      if (hostBits < 0) {
        return List.of();
      }

      BigInteger subnetInt = new BigInteger(1, subnetBytes);
      BigInteger mask =
          BigInteger.ONE
              .shiftLeft(addressBitLength)
              .subtract(BigInteger.ONE)
              .shiftRight(hostBits)
              .shiftLeft(hostBits);
      BigInteger network = subnetInt.and(mask);
      BigInteger rangeSize = BigInteger.ONE.shiftLeft(hostBits);
      BigInteger rangeEnd = network.add(rangeSize).subtract(BigInteger.ONE);

      BigInteger start = network;
      BigInteger end = rangeEnd;
      if (ipv4 && prefixLength <= 30 && rangeSize.compareTo(BigInteger.TWO) > 0) {
        start = network.add(BigInteger.ONE);
        end = rangeEnd.subtract(BigInteger.ONE);
      }
      if (start.compareTo(end) > 0) {
        return List.of();
      }

      BigInteger hostCount = end.subtract(start).add(BigInteger.ONE);
      if (hostCount.compareTo(BigInteger.valueOf(MAX_EXPANDED_HOSTS)) > 0) {
        log.warn(
            "Subnet {} expansion skipped: {} hosts exceeds limit {}",
            cidr,
            hostCount,
            MAX_EXPANDED_HOSTS);
        return List.of();
      }

      List<String> hosts = new ArrayList<>(hostCount.intValue());
      for (BigInteger current = start;
          current.compareTo(end) <= 0;
          current = current.add(BigInteger.ONE)) {
        byte[] addressBytes = toFixedLengthBytes(current, subnetBytes.length);
        hosts.add(InetAddress.getByAddress(addressBytes).getHostAddress());
      }
      return hosts;
    } catch (Exception e) {
      log.warn("Failed to expand subnet {}: {}", cidr, e.getMessage());
      return List.of();
    }
  }

  private static byte[] toFixedLengthBytes(BigInteger value, int length) {
    byte[] raw = value.toByteArray();
    if (raw.length == length) {
      return raw;
    }
    byte[] fixed = new byte[length];
    if (raw.length > length) {
      System.arraycopy(raw, raw.length - length, fixed, 0, length);
      return fixed;
    }
    System.arraycopy(raw, 0, fixed, length - raw.length, raw.length);
    return fixed;
  }
}
