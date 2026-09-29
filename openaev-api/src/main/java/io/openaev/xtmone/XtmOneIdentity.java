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
  static final Timeout METADATA_TIMEOUT = Timeout.ofSeconds(10);

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

  /**
   * Where a browser opens XTM One: the identity it last published, else its configured URL. Never
   * reads XTM One, so no page waits on it; {@link XtmOneConnectivityService} keeps it fresh.
   */
  public String browserUrl() {
    Cached current = cached;
    return current != null && current.issuer() != null ? current.issuer() : config.getUrl();
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
   * no identity, so its tokens carry the configured URL), or not definitive (it could not be read,
   * or its document names no usable issuer).
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
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry(METADATA_TIMEOUT)) {
      HttpGet httpGet = new HttpGet(config.getUrl() + "/xtm/auth/metadata");
      // Only the configured URL may answer: a redirect is a failed read, never another origin
      // supplying XTM One's identity.
      httpGet.setConfig(
          RequestConfig.custom()
              .setResponseTimeout(METADATA_TIMEOUT)
              .setRedirectsEnabled(false)
              .build());
      String body = httpClient.execute(httpGet, XtmOneIdentity::readMetadata);
      if (body == null) {
        return new Answer(false, null);
      }
      JsonNode issuer = objectMapper.readTree(body).get("issuer");
      Optional<String> published =
          issuer != null && issuer.isTextual() ? canonical(issuer.asText()) : Optional.empty();
      if (published.isEmpty()) {
        // Only a 404 says XTM One publishes no identity: a document without one is a failed read.
        log.debug("XTM One metadata at {} carries no usable issuer", config.getUrl());
        return new Answer(false, null);
      }
      return new Answer(true, published.get());
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
        if (!host.matches("[A-Za-z0-9_.-]+")) {
          // A bracketed IPv6 literal is a server name; anything else here is malformed.
          return Optional.empty();
        }
        String digits = colon >= 0 ? authority.substring(colon + 1) : null;
        if (digits != null && !digits.matches("\\d{1,5}")) {
          // Integer.parseInt would read a sign: -2 or +80 is no port.
          return Optional.empty();
        }
        port = digits != null ? Integer.parseInt(digits) : -1;
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
