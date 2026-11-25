package io.openaev.secrets.provider.impl.validators;

import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.security.ssrf.HostResolver;
import io.openaev.security.ssrf.PublicAddressPolicy;
import io.openaev.security.ssrf.TimeoutHostResolver;
import io.openaev.security.ssrf.TimeoutHostResolver.LookupTimeoutException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates the endpoint overrides of a credential before an SDK is built from them.
 *
 * <p>{@link URI#create(String)} accepts a relative reference ({@code /token}) or a scheme the
 * probes never speak ({@code file:///tmp/token}). The SDKs then fail later, during the token
 * exchange, and the failure reads as a network error instead of the configuration problem it is.
 *
 * <p>An override is also a destination the platform calls on its own, with a credential attached,
 * so by default it must not point inside the platform's network: see {@link #refusal}.
 */
final class EndpointUrls {

  private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

  /** Lookups allowed to wait on DNS at once, abandoned ones included. */
  private static final int MAX_CONCURRENT_LOOKUPS = 8;

  /** One bounded resolver per probe budget: each owns a small pool of daemon threads. */
  private static final Map<Integer, HostResolver> BOUNDED_RESOLVERS = new ConcurrentHashMap<>();

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
   * Decides whether the probe may call the given endpoint overrides.
   *
   * <p>The probes call these endpoints from the platform with the credential's token attached, and
   * report the outcome in the credential status. Left unchecked, an override naming an internal
   * host would turn that status into a way to map the platform's own network. Each host is resolved
   * the way the HTTP stack resolves it, and one internal answer is enough to refuse it.
   *
   * <p>The lookup is bounded by the probe budget. One that is not answered in time is inconclusive:
   * the probe is not started, since a host that stalls the check and answers the SDK right after
   * would otherwise get through, and the credential is not flagged, since a slow resolver says
   * nothing about it. A host that does not resolve is not refused: the call fails by itself and the
   * status says so.
   *
   * <p>Meant to run after {@link #isMalformedOverride(String)}; a value that cannot be parsed is
   * left to it.
   *
   * <p>Limits: the address is checked when the probe starts, and the HTTP stack resolves the host
   * again when it connects, so a host that changes its answer between the two is not caught; a
   * redirect from a public host, or an egress proxy that resolves the host itself, is not seen
   * either.
   *
   * @param allowInternal the on-premise opt-out
   * @param timeoutSeconds the probe budget, one lookup at most
   * @param endpoints stored overrides, null or blank when there is none
   * @return the outcome to report when the probe must not go on, empty otherwise
   */
  static Optional<SecretConnectionResult> refusal(
      boolean allowInternal, int timeoutSeconds, String... endpoints) {
    int seconds = Math.max(1, timeoutSeconds);
    return refusal(
        allowInternal,
        BOUNDED_RESOLVERS.computeIfAbsent(
            seconds,
            budget ->
                new TimeoutHostResolver(
                    InetAddress::getAllByName, Duration.ofSeconds(budget), MAX_CONCURRENT_LOOKUPS)),
        endpoints);
  }

  static Optional<SecretConnectionResult> refusal(
      boolean allowInternal, HostResolver resolver, String... endpoints) {
    if (allowInternal) {
      return Optional.empty();
    }
    for (String endpoint : endpoints) {
      String host = hostOf(endpoint);
      if (host == null) {
        continue;
      }
      try {
        if (Arrays.stream(resolver.resolve(host)).anyMatch(PublicAddressPolicy::isInternal)) {
          return Optional.of(SecretConnectionResult.formatError());
        }
      } catch (LookupTimeoutException e) {
        return Optional.of(SecretConnectionResult.timeout());
      } catch (UnknownHostException e) {
        // Not refused: the call fails by itself.
      }
    }
    return Optional.empty();
  }

  /**
   * Whether a host can be read from the endpoint, the way the HTTP stack reads it. Used for a URL
   * the platform did not accept itself (a key file's token URI): one that names no host cannot be
   * checked, so the caller refuses it.
   */
  static boolean hasHost(String endpoint) {
    return hostOf(endpoint) != null;
  }

  private static String hostOf(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return null;
    }
    try {
      String host = URI.create(endpoint).getHost();
      if (host != null && !host.isBlank()) {
        return host;
      }
    } catch (IllegalArgumentException e) {
      // Falls back to the lenient reading below.
    }
    // java.net.URI gives no host to "http://127.1/" or "http://my_host/", which the HTTP stacks
    // still connect to: read it as java.net.URL does.
    try {
      String host = new URL(endpoint).getHost();
      return host == null || host.isBlank() ? null : host;
    } catch (MalformedURLException e) {
      return null;
    }
  }
}
