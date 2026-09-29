package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
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

  /** Replaced by tests to expire the cached identity. */
  Clock clock = Clock.systemUTC();

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
    if (current != null && !current.expiresAt().isBefore(clock.instant())) {
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
    if (current != null && !current.expiresAt().isBefore(clock.instant())) {
      return current;
    }
    String previous = current != null ? current.issuer() : null;
    Answer answer = fetchIssuer();
    Cached next;
    if (answer.definitive()) {
      // No identity published: asked again soon, so an XTM One that starts publishing is seen.
      Duration ttl = answer.issuer() != null ? IDENTITY_TTL : RETRY_AFTER;
      next = new Cached(answer.issuer(), clock.instant().plus(ttl));
      if (!Objects.equals(answer.issuer(), previous)) {
        log.info(
            "XTM One reached on {} signs as {}",
            config.getUrl(),
            answer.issuer() != null ? answer.issuer() : config.getUrl());
      }
    } else {
      // Keep the last identity XTM One published while it cannot be reached.
      next = new Cached(previous, clock.instant().plus(RETRY_AFTER));
    }
    cached = next;
    return next;
  }

  /**
   * What XTM One answered: definitive with its identity, definitive with none (a 404: it publishes
   * no identity, so its tokens carry the configured URL), or not definitive (it could not be read).
   */
  record Answer(boolean definitive, String issuer) {}

  /** A 404 from the metadata document: XTM One publishes no identity. */
  static final class IdentityNotPublished extends IOException {
    IdentityNotPublished() {
      super("XTM One publishes no identity");
    }
  }

  private Answer fetchIssuer() {
    if (config.getUrl() == null || config.getUrl().isBlank()) {
      return new Answer(false, null);
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      HttpGet httpGet = new HttpGet(config.getUrl() + "/xtm/auth/metadata");
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());
      String body = httpClient.execute(httpGet, XtmOneIdentity::readMetadata);
      if (body == null) {
        return new Answer(false, null);
      }
      JsonNode issuer = objectMapper.readTree(body).get("issuer");
      return new Answer(
          true,
          issuer != null && issuer.isTextual() ? canonical(issuer.asText()).orElse(null) : null);
    } catch (IdentityNotPublished e) {
      return new Answer(true, null);
    } catch (Exception e) {
      log.debug("XTM One identity unavailable at {}", config.getUrl(), e);
      return new Answer(false, null);
    }
  }

  /** The metadata body of a 200, {@link IdentityNotPublished} on a 404, null otherwise. */
  static String readMetadata(ClassicHttpResponse response) throws IOException, ParseException {
    if (response.getCode() == 404) {
      throw new IdentityNotPublished();
    }
    return response.getCode() == 200
        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
        : null;
  }

  /**
   * {@code url} as one spelling: lower-case scheme and host, no default port, no trailing slash.
   * Empty for anything but an http(s) URL made of a host, an optional port and a path: user info, a
   * query or a fragment would make two different identities compare equal.
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
      if (uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || uri.getRawAuthority() == null
          || uri.getRawAuthority().contains("@")) {
        return Optional.empty();
      }
      String host;
      int port;
      if (uri.getHost() != null) {
        host = uri.getHost();
        port = uri.getPort();
      } else {
        // A host java.net.URI does not read as a server name: an underscore, as in xtm_one.
        String authority = uri.getRawAuthority();
        int colon = authority.lastIndexOf(':');
        host = colon >= 0 ? authority.substring(0, colon) : authority;
        port = colon >= 0 ? Integer.parseInt(authority.substring(colon + 1)) : -1;
      }
      if (host.isEmpty() || port == 0 || port > 65535) {
        return Optional.empty();
      }
      boolean defaultPort =
          port == -1
              || (scheme.equals("http") && port == 80)
              || (scheme.equals("https") && port == 443);
      String authority = host.toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
      String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
      return Optional.of(scheme + "://" + authority + path);
    } catch (URISyntaxException | NumberFormatException e) {
      return Optional.empty();
    }
  }
}
