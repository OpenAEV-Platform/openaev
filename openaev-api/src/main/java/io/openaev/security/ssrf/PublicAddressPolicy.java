package io.openaev.security.ssrf;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Shared classification of "internal" (private/loopback/link-local/reserved) vs. public network
 * addresses, used by every SSRF guard in the platform: {@link
 * io.openaev.notification.engine.WebhookTargetValidator} (webhook notifier targets, with an
 * explicit on-premise opt-out) and the report-render egress guard (tenant-controlled asset URLs
 * fetched by the headless render browser, with no opt-out).
 *
 * <p>Classification only - callers decide how and when to resolve a host and what to do with an
 * internal or unresolvable result.
 */
public final class PublicAddressPolicy {

  private PublicAddressPolicy() {}

  /**
   * Whether an address is internal. Loopback, link-local, RFC1918 site-local, wildcard, multicast
   * and IPv6 unique-local (fc00::/7) come from the JDK, extended with the special-purpose IPv4
   * ranges of {@link #isReservedIpv4}. When an IPv6 address carries an IPv4 address inside it, the
   * embedded address decides.
   */
  public static boolean isInternal(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    if (address instanceof Inet6Address ipv6) {
      if ((ipv6.getAddress()[0] & 0xFE) == 0xFC) {
        return true;
      }
      Inet4Address embedded = embeddedIpv4(ipv6);
      return embedded != null && isInternal(embedded);
    }
    // Any other address family is a four-byte IPv4 address.
    return isReservedIpv4(address.getAddress());
  }

  /**
   * IANA special-purpose IPv4 ranges the JDK does not classify: {@code 0.0.0.0/8} (this network),
   * {@code 100.64.0.0/10} (RFC 6598 shared address space), {@code 192.0.0.0/24} (RFC 6890 protocol
   * assignments), {@code 198.18.0.0/15} (RFC 2544 benchmarking) and {@code 240.0.0.0/4} (reserved).
   * None of them is a valid public destination.
   */
  private static boolean isReservedIpv4(byte[] bytes) {
    int first = bytes[0] & 0xFF;
    int second = bytes[1] & 0xFF;
    return first == 0
        || (first == 100 && second >= 64 && second <= 127)
        || (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0)
        || (first == 198 && (second == 18 || second == 19))
        || first >= 240;
  }

  /**
   * Returns the IPv4 address carried by an IPv6 address, or null when it carries none.
   *
   * <p>Recognises the IPv4-compatible and 6to4 forms, the Teredo client address, ISATAP, and NAT64
   * addresses of the form {@code 64:ff9b:X::a.b.c.d}, that is a prefix inside {@code 64:ff9b::/32}
   * followed by zeroes and the IPv4 in the last four bytes. That is the layout RFC 6052 defines for
   * a /96 prefix and the one DNS64 resolvers produce.
   */
  private static Inet4Address embeddedIpv4(Inet6Address address) {
    if (InetAddresses.hasEmbeddedIPv4ClientAddress(address)) {
      return InetAddresses.getEmbeddedIPv4ClientAddress(address);
    }
    if (InetAddresses.isIsatapAddress(address)) {
      return InetAddresses.getIsatapIPv4Address(address);
    }
    return nat64Ipv4(address.getAddress());
  }

  private static Inet4Address nat64Ipv4(byte[] bytes) {
    boolean nat64Range =
        bytes[0] == 0x00 && bytes[1] == 0x64 && bytes[2] == (byte) 0xFF && bytes[3] == (byte) 0x9B;
    if (!nat64Range) {
      return null;
    }
    for (int i = 6; i < 12; i++) {
      if (bytes[i] != 0x00) {
        return null;
      }
    }
    int embedded =
        (bytes[12] & 0xFF) << 24
            | (bytes[13] & 0xFF) << 16
            | (bytes[14] & 0xFF) << 8
            | (bytes[15] & 0xFF);
    return InetAddresses.fromInteger(embedded);
  }
}
