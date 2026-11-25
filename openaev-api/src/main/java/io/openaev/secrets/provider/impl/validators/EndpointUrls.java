package io.openaev.secrets.provider.impl.validators;

import io.openaev.security.ssrf.PublicAddressPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Validates the endpoint overrides of a credential before an SDK is built from them.
 *
 * <p>{@link URI#create(String)} accepts a relative reference ({@code /token}) or a scheme the
 * probes never speak ({@code file:///tmp/token}). The SDKs then fail later, during the token
 * exchange, and the failure reads as a network error instead of the configuration problem it is.
 *
 * <p>An override is also a destination the platform calls on its own, with a credential attached,
 * so by default it must not point inside the platform's network: see {@link
 * #targetsInternalAddress(String)}.
 */
final class EndpointUrls {

  private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

  private EndpointUrls() {}

  /**
   * Parses an endpoint override.
   *
   * @param value the stored override
   * @return the parsed URI, absolute, HTTP(S) and with a host
   * @throws IllegalArgumentException when the value is not an absolute HTTP(S) URL with a host
   */
  static URI requireHttpUri(String value) {
    URI uri = URI.create(value);
    String scheme = uri.getScheme();
    if (scheme == null
        || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
        || uri.getHost() == null
        || uri.getHost().isBlank()
        // Credentials, a query or a fragment have no place in a base endpoint: what the probes
        // append to it would land in them, and the token would travel to another place than the
        // path suggests.
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null) {
      throw new IllegalArgumentException(
          "Endpoint must be an absolute HTTP(S) URL with a host, and no credentials, query or"
              + " fragment");
    }
    return uri;
  }

  /**
   * @param value a stored override, null or blank when there is none
   * @return {@code true} when a value is set and is not an absolute HTTP(S) URL with a host; an
   *     absent override is never malformed, it means the official endpoint
   */
  static boolean isMalformedOverride(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      requireHttpUri(value);
      return false;
    } catch (IllegalArgumentException e) {
      return true;
    }
  }

  /**
   * Whether an override points at a private, loopback, link-local or otherwise internal address.
   *
   * <p>The probes call these endpoints from the platform with the credential's token attached, and
   * report the outcome in the credential status. Left unchecked, an override naming an internal
   * host would turn that status into a way to map the platform's own network. The host is resolved
   * the way the HTTP stack resolves it, and one internal answer is enough to refuse it. A host that
   * does not resolve is not refused here: the call fails by itself and the status says so.
   *
   * <p>Meant to run after {@link #isMalformedOverride(String)}; a value that cannot be parsed is
   * reported as not internal, since the malformed check already rejects it.
   *
   * <p>Limits: the address is checked when the probe starts, and the HTTP stack resolves the host
   * again when it connects, so a host that changes its answer between the two is not caught; a
   * redirect from a public host, or an egress proxy that resolves the host itself, is not seen
   * either.
   *
   * @param value a stored override, null or blank when there is none
   * @return {@code true} when the host resolves to at least one internal address
   */
  static boolean targetsInternalAddress(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String host;
    try {
      host = URI.create(value).getHost();
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (host == null || host.isBlank()) {
      return false;
    }
    try {
      return Arrays.stream(InetAddress.getAllByName(host))
          .anyMatch(PublicAddressPolicy::isInternal);
    } catch (UnknownHostException e) {
      return false;
    }
  }

  /**
   * @param endpoint a stored override, null or blank when there is none
   * @param allowInternal the on-premise opt-out
   * @return {@code true} when the override must not be called: it targets an internal address and
   *     the opt-out is off
   */
  static boolean isRefused(String endpoint, boolean allowInternal) {
    return !allowInternal && targetsInternalAddress(endpoint);
  }
}
