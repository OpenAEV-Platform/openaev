package io.openaev.secrets.provider.impl.validators;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Validates the endpoint overrides of a credential before an SDK is built from them.
 *
 * <p>{@link URI#create(String)} accepts a relative reference ({@code /token}) or a scheme the
 * probes never speak ({@code file:///tmp/token}). The SDKs then fail later, during the token
 * exchange, and the failure reads as a network error instead of the configuration problem it is.
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
        || uri.getHost().isBlank()) {
      throw new IllegalArgumentException("Endpoint must be an absolute HTTP(S) URL with a host");
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
}
