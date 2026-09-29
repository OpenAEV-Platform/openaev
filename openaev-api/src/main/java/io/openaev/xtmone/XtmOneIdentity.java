package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * XTM One's identity, as opposed to the URL OpenAEV reaches it on.
 *
 * <p>In Docker or Kubernetes OpenAEV reaches XTM One on an internal URL ({@code
 * http://xtm-one:4000}) while XTM One signs its tokens with, and expects on the tokens sent to it,
 * its public {@code BASE_URL}: the issuer it publishes at {@code /xtm/auth/metadata}. An XTM One
 * that predates that document is known by the configured URL alone.
 */
@Component
@RequiredArgsConstructor
@Slf4j(topic = "XTM One identity")
public class XtmOneIdentity {

  static final Duration IDENTITY_TTL = Duration.ofHours(1);
  static final Duration RETRY_AFTER = Duration.ofMinutes(1);

  private final XtmOneConfig config;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper objectMapper;

  private record Cached(String issuer, Instant expiresAt) {}

  private final ReentrantLock refreshLock = new ReentrantLock();
  private volatile Cached cached;

  /** Whether {@code issuer} names XTM One: its configured URL or the issuer it publishes. */
  public boolean isXtmOneIssuer(String issuer) {
    Optional<String> candidate = canonical(issuer);
    Optional<String> configured = canonical(config.getUrl());
    if (candidate.isEmpty() || configured.isEmpty()) {
      return false;
    }
    return candidate.equals(configured) || candidate.equals(publishedIssuer());
  }

  /** The audience of a token sent to XTM One: its published identity, else its configured URL. */
  public String audience() {
    return publishedIssuer().orElse(config.getUrl());
  }

  /** The issuer XTM One publishes, empty when it publishes none or has never answered. */
  public Optional<String> publishedIssuer() {
    Cached current = cached;
    if (current != null && !current.expiresAt().isBefore(Instant.now())) {
      return Optional.ofNullable(current.issuer());
    }
    // One thread reads XTM One; the others keep the last answer instead of waiting on it.
    if (!refreshLock.tryLock()) {
      return Optional.ofNullable(current != null ? current.issuer() : null);
    }
    try {
      return Optional.ofNullable(refresh().issuer());
    } finally {
      refreshLock.unlock();
    }
  }

  private Cached refresh() {
    Cached current = cached;
    if (current != null && !current.expiresAt().isBefore(Instant.now())) {
      return current;
    }
    String previous = current != null ? current.issuer() : null;
    Optional<String> fetched = fetchIssuer();
    Cached next =
        fetched
            .map(issuer -> new Cached(issuer, Instant.now().plus(IDENTITY_TTL)))
            // Keep the last identity XTM One published while it cannot be reached.
            .orElseGet(() -> new Cached(previous, Instant.now().plus(RETRY_AFTER)));
    if (fetched.isPresent() && !Objects.equals(fetched.get(), previous)) {
      log.info("XTM One reached on {} signs as {}", config.getUrl(), fetched.get());
    }
    cached = next;
    return next;
  }

  private Optional<String> fetchIssuer() {
    if (config.getUrl() == null || config.getUrl().isBlank()) {
      return Optional.empty();
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      HttpGet httpGet = new HttpGet(config.getUrl() + "/xtm/auth/metadata");
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());
      String body =
          httpClient.execute(
              httpGet,
              response ->
                  response.getCode() == 200
                      ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                      : null);
      if (body == null) {
        return Optional.empty();
      }
      JsonNode issuer = objectMapper.readTree(body).get("issuer");
      return issuer != null && issuer.isTextual() ? canonical(issuer.asText()) : Optional.empty();
    } catch (Exception e) {
      log.debug("XTM One identity unavailable at {}", config.getUrl(), e);
      return Optional.empty();
    }
  }

  /**
   * {@code url} as one spelling: lower-case scheme and host, no default port, no trailing slash.
   * Empty for anything but an http(s) URL.
   */
  public static Optional<String> canonical(String url) {
    if (url == null || url.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = new URI(url.trim());
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!scheme.equals("http") && !scheme.equals("https")) {
        return Optional.empty();
      }
      String authority;
      if (uri.getHost() != null) {
        int port = uri.getPort();
        boolean defaultPort =
            port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);
        authority = uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
      } else if (uri.getRawAuthority() != null) {
        // A host java.net.URI does not read as a server name: an underscore, as in xtm_one.
        authority = uri.getRawAuthority().toLowerCase(Locale.ROOT);
      } else {
        return Optional.empty();
      }
      String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
      return Optional.of(scheme + "://" + authority + path);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }
}
