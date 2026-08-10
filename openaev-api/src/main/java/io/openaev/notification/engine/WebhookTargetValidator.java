package io.openaev.notification.engine;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards webhook notifiers against SSRF: only http(s) URLs targeting public addresses are allowed,
 * with a small allow-list of HTTP verbs. Applied both when a notifier is saved and again at
 * dispatch time (the configuration is raw JSON and could bypass the API-time check).
 *
 * <p>On-premise deployments that legitimately post to internal endpoints can opt out with {@code
 * openaev.notification.webhook-allow-internal-targets}. That switch disables target checking
 * entirely, not only the RFC1918 ranges.
 */
@Component
public class WebhookTargetValidator {

  private static final Set<String> ALLOWED_VERBS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

  private final boolean allowInternalTargets;

  public WebhookTargetValidator(
      @Value("${openaev.notification.webhook-allow-internal-targets:false}")
          boolean allowInternalTargets) {
    this.allowInternalTargets = allowInternalTargets;
  }

  /** Validates a webhook URL, throwing {@link IllegalArgumentException} when it is not allowed. */
  public URI validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Webhook notifier url is not a valid URI");
    }
    String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Webhook notifier url must be http(s)");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Webhook notifier url must have a host");
    }
    if (!allowInternalTargets) {
      requirePublicTarget(host);
    }
    return uri;
  }

  /** Validates the webhook HTTP verb, defaulting to POST when blank. */
  public String validateVerb(String verb) {
    String normalized =
        verb == null || verb.isBlank() ? "POST" : verb.trim().toUpperCase(Locale.ROOT);
    if (!ALLOWED_VERBS.contains(normalized)) {
      throw new IllegalArgumentException("Webhook notifier verb must be one of " + ALLOWED_VERBS);
    }
    return normalized;
  }

  private void requirePublicTarget(String host) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      // An unresolvable host cannot be requested at all - let the dispatch fail naturally.
      return;
    }
    for (InetAddress address : addresses) {
      if (isInternal(address)) {
        throw new IllegalArgumentException(
            "Webhook notifier url resolves to a private or internal address, which is not"
                + " allowed on this platform");
      }
    }
  }

  /**
   * Whether an address is internal. Loopback, link-local, RFC1918 site-local, wildcard, multicast
   * and IPv6 unique-local (fc00::/7) come from the JDK, extended with the special-purpose IPv4
   * ranges of {@link #isReservedIpv4}. When an IPv6 address carries an IPv4 address inside it, the
   * embedded address decides.
   */
  private static boolean isInternal(InetAddress address) {
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
   * None of them is a valid public webhook destination.
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
   * inside {@code 64:ff9b::/32} when the IPv4 sits in the last four bytes, which is the layout RFC
   * 6052 defines for a /96 prefix and the one DNS64 resolvers produce in practice.
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
