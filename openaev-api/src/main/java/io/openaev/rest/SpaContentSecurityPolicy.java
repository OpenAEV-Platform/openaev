package io.openaev.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Content Security Policy of the single page application shell (the {@code index.html} served by
 * {@link HomeApi}).
 *
 * <p>The policy is attached to the shell response only: the API, Swagger and the hosted pages are
 * not covered. The phishing template previews are: they are {@code srcdoc} iframes, which inherit
 * the policy of the page embedding them.
 *
 * <p>It is sent as {@value #HEADER}: browsers report violations in their console and block nothing.
 * Nothing collects those reports yet, so before switching to {@code Content-Security-Policy} the
 * violations have to be gathered (click through staging with the console open, or add a collector)
 * and the phishing previews decided on.
 */
final class SpaContentSecurityPolicy {

  static final String HEADER = "Content-Security-Policy-Report-Only";

  /** Placeholder of {@code index.html} replaced by the per-response nonce. */
  static final String NONCE_PLACEHOLDER = "%CSP_NONCE%";

  private static final SecureRandom RANDOM = new SecureRandom();

  private SpaContentSecurityPolicy() {}

  /** A fresh unguessable nonce (128 bits, hex), to generate for every response. */
  static String newNonce() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /**
   * @param xtmHubUrl the configured XTM Hub URL: the browser calls it directly (health check,
   *     document download), so its origin has to be allowed to connect. Ignored when blank or not a
   *     plain http(s) origin.
   */
  static String policy(String nonce, String xtmHubUrl) {
    return String.join("; ", directives(nonce, originOf(xtmHubUrl)));
  }

  /** {@code scheme://host[:port]} of an http(s) URL, or null when there is no safe origin. */
  static String originOf(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(url.trim());
      String authority = uri.getRawAuthority();
      boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
      if (!http || authority == null || !authority.matches("[A-Za-z0-9.:\\-\\[\\]]+")) {
        return null;
      }
      return uri.getScheme() + "://" + authority;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private static List<String> directives(String nonce, String xtmHubOrigin) {
    return List.of(
        "default-src 'self'",
        // The only inline script of the shell carries the nonce; every other script is a
        // same-origin file.
        "script-src 'self' 'nonce-" + nonce + "'",
        // MUI / emotion inject inline styles at runtime.
        "style-src 'self' 'unsafe-inline'",
        // https only: images served over plain http (admin-configured logos, markdown) are
        // reported, on purpose.
        "img-src 'self' data: blob: https:",
        "font-src 'self' data:",
        "connect-src 'self'" + (xtmHubOrigin == null ? "" : " " + xtmHubOrigin),
        "frame-src 'self' https://www.youtube.com",
        "worker-src 'self' blob:",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        // Same rule as the X-Frame-Options SAMEORIGIN set in AppSecurityConfig.
        "frame-ancestors 'self'");
  }
}
