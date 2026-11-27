package io.openaev.security.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.security.Jwks;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.TenantUriUtils;
import io.openaev.database.model.User;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.utils.RequestUtils;
import io.openaev.utils.StringUtils;
import io.openaev.xtmone.XtmOneIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * Authenticates the user another XTM product forwards on the XTM reverse lookup, so OpenAEV applies
 * THAT user's rights: no right in OpenAEV means no object and no fact, the right means the facts.
 *
 * <p>Today the forwarding product is the OpenCTI the tenant is connected to ({@code
 * openaev.xtm.opencti.{tenant}.*}): it signs a short-lived per-user JWT with its own key ({@code
 * issueXtmJwt}: EdDSA, {@code iss} its base URL, {@code aud} OpenAEV's base URL, {@code email} the
 * user). A token is accepted only when all of these hold:
 *
 * <ol>
 *   <li>the request targets the reverse lookup of a tenant: {@code
 *       /api/tenants/{uuid}/xtm/objects/...}, nothing else
 *   <li>that tenant is connected to an OpenCTI, and the issuer is that OpenCTI: its configured
 *       {@code issuer}, else its configured {@code url} (compared canonical)
 *   <li>the signature verifies against the keys that OpenCTI publishes at {@code
 *       {url}/xtm/auth/jwks} (the URL OpenAEV reaches it on, never one the token names)
 *   <li>the token has an expiry, is not expired, and one of its audiences is OpenAEV's base URL
 *   <li>its {@code email} claim names an OpenAEV user
 * </ol>
 *
 * <p>The keys are read before the token is authenticated, so the read is bounded: one read at a
 * time per JWKS URL, at most one every {@link #JWKS_MIN_REFRESH_INTERVAL} (a burst of tokens with
 * an unknown {@code kid} costs one request), connect, TLS handshake and reads bounded by {@link
 * #JWKS_TIMEOUT}, no retry, no redirect. A key set older than {@link #JWKS_CACHE_TTL} is never
 * used, even when its refresh fails.
 *
 * <p>Unlike {@link XtmJwksExtractor}, it never stamps {@link
 * XtmJwksExtractor#CROSS_PLATFORM_ATTRIBUTE}: the forwarded user is a person with their own rights,
 * never the orchestrator, so the tenant scope stays caller-authorized. The trust root is a
 * per-tenant issuer and JWKS pair, so moving it to XTM One (one user directory for the XTM
 * Platform) is a new source of that pair, with no user mapping to undo.
 */
@Component
@Slf4j(topic = "XTM user JWT Authentication")
@RequiredArgsConstructor
public class XtmUserJwtExtractor implements ExtractorBase {

  private static final String UUID_REGEX =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

  /** The only routes a forwarded user token opens: the reverse lookup of one tenant. */
  static final Pattern SCOPE =
      Pattern.compile("^" + TenantUriUtils.TENANT_BASE_PATH + UUID_REGEX + "/xtm/objects/[^/]+$");

  static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);
  static final Duration JWKS_MIN_REFRESH_INTERVAL = Duration.ofSeconds(30);
  static final Timeout JWKS_TIMEOUT = Timeout.ofSeconds(10);

  private final XtmConfig xtmConfig;
  private final TenantUriUtils tenantUriUtils;
  private final RequestUtils requestUtils;
  private final UserService userService;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper objectMapper;
  private final OpenAEVConfig openAEVConfig;

  private final ConcurrentHashMap<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Instant> lastRefreshAttempt = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> refreshLocks = new ConcurrentHashMap<>();

  /** The clock of the key cache, replaced in tests only. */
  Clock clock = Clock.systemUTC();

  private record CachedJwks(Instant fetchedAt, String jwksJson) {}

  @Override
  public Optional<User> authUser(String value, HttpServletRequest request)
      throws JwtException, AuthenticationError {
    if (value == null || request == null) {
      throw new AuthenticationError("No bearer token found");
    }
    String uri = requestUtils.getContextStrippedUri(request);
    if (StringUtils.isBlank(uri) || !SCOPE.matcher(uri).matches()) {
      throw new AuthenticationError("Forwarded user tokens only open the XTM reverse lookup");
    }
    String tenantId =
        tenantUriUtils
            .getTenantIdFromRequestUrl(request)
            .orElseThrow(() -> new AuthenticationError("No tenant in the request URL"));
    OpenCTIConfig opencti =
        Optional.ofNullable(xtmConfig.getOpencti().get(tenantId))
            .filter(config -> Boolean.TRUE.equals(config.getEnable()))
            .filter(config -> !StringUtils.isBlank(config.getUrl()))
            .orElseThrow(() -> new AuthenticationError("Tenant not connected to an OpenCTI"));

    String issuer = extractUnverifiedIssuer(value);
    if (!isTrustedIssuer(opencti, issuer)) {
      throw new AuthenticationError("Untrusted forwarded user token issuer: " + issuer);
    }

    String jwksUrl = trimTrailingSlash(opencti.getUrl()) + "/xtm/auth/jwks";
    Jws<Claims> jws =
        Jwts.parser()
            .keyLocator(header -> resolveKey(jwksUrl, (String) header.get("kid")))
            .build()
            .parseSignedClaims(value);
    Claims claims = jws.getPayload();
    if (claims.getExpiration() == null) {
      throw new MissingClaimException(
          jws.getHeader(), claims, Claims.EXPIRATION, null, "A forwarded token must expire");
    }
    requireOwnAudience(jws);

    String email = claims.get("email", String.class);
    if (StringUtils.isBlank(email)) {
      throw new AuthenticationError("The JWT does not contain the required 'email' claim.");
    }
    return userService.findByEmailIgnoreCase(email);
  }

  // -- PRIVATE --

  /** The issuer of the tenant's OpenCTI: its configured issuer, else its configured URL. */
  static boolean isTrustedIssuer(OpenCTIConfig opencti, String issuer) {
    Optional<String> candidate = XtmOneIdentity.canonical(issuer);
    if (candidate.isEmpty()) {
      return false;
    }
    Optional<String> expected =
        XtmOneIdentity.canonical(
            StringUtils.isBlank(opencti.getIssuer()) ? opencti.getUrl() : opencti.getIssuer());
    return expected.isPresent() && expected.equals(candidate);
  }

  private static String trimTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /** Refuses a token none of whose audiences is OpenAEV's base URL (compared canonical). */
  private void requireOwnAudience(Jws<Claims> jws) {
    String expected = openAEVConfig.getBaseUrl();
    Optional<String> canonicalExpected = XtmOneIdentity.canonical(expected);
    Set<String> audiences = jws.getPayload().getAudience();
    boolean matches =
        canonicalExpected.isPresent()
            && audiences != null
            && audiences.stream()
                .map(XtmOneIdentity::canonical)
                .anyMatch(canonicalExpected::equals);
    if (!matches) {
      throw new IncorrectClaimException(
          jws.getHeader(),
          jws.getPayload(),
          Claims.AUDIENCE,
          expected,
          "Expected aud claim to contain " + expected + " but was " + audiences);
    }
  }

  private Key resolveKey(String jwksUrl, String kid) {
    if (StringUtils.isBlank(kid)) {
      throw new JwtException("A forwarded token must name its key (kid)");
    }
    CachedJwks cached = freshEntry(jwksUrl);
    if (cached == null) {
      refreshJwks(jwksUrl, null);
      cached = freshEntry(jwksUrl);
    }
    Key key = findKey(cached, kid);
    if (key == null && cached != null) {
      // A key OpenCTI rotated in since the last read: read its JWKS again, throttled.
      refreshJwks(jwksUrl, cached);
      key = findKey(freshEntry(jwksUrl), kid);
    }
    if (key == null) {
      throw new JwtException("No matching key found for kid: " + kid + " at " + jwksUrl);
    }
    return key;
  }

  /** The cached key set when it is within its lifetime; an expired one is dropped, never used. */
  private CachedJwks freshEntry(String jwksUrl) {
    CachedJwks cached = jwksCache.get(jwksUrl);
    if (cached == null) {
      return null;
    }
    if (!isFresh(cached)) {
      jwksCache.remove(jwksUrl, cached);
      return null;
    }
    return cached;
  }

  private boolean isFresh(CachedJwks cached) {
    return cached.fetchedAt().plus(JWKS_CACHE_TTL).isAfter(clock.instant());
  }

  private static Key findKey(CachedJwks cached, String kid) {
    if (cached == null || StringUtils.isBlank(kid)) {
      return null;
    }
    return Jwks.setParser().build().parse(cached.jwksJson()).getKeys().stream()
        .filter(k -> kid.equals(k.getId()))
        .findFirst()
        .map(jwk -> (Key) jwk.toKey())
        .orElse(null);
  }

  /**
   * Reads the JWKS again unless another thread already replaced {@code seen} with a fresh set, or
   * the last attempt on this URL is younger than {@link #JWKS_MIN_REFRESH_INTERVAL}. Concurrent
   * callers on one URL wait for the read in progress instead of starting their own.
   */
  private void refreshJwks(String jwksUrl, CachedJwks seen) {
    synchronized (refreshLocks.computeIfAbsent(jwksUrl, url -> new Object())) {
      CachedJwks current = jwksCache.get(jwksUrl);
      if (current != null && current != seen && isFresh(current)) {
        return;
      }
      Instant now = clock.instant();
      Instant last = lastRefreshAttempt.get(jwksUrl);
      if (last != null && last.plus(JWKS_MIN_REFRESH_INTERVAL).isAfter(now)) {
        return;
      }
      lastRefreshAttempt.put(jwksUrl, now);
      String jwksJson = fetchJwks(jwksUrl);
      if (jwksJson != null) {
        jwksCache.put(jwksUrl, new CachedJwks(clock.instant(), jwksJson));
      }
    }
  }

  private String fetchJwks(String jwksUrl) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry(JWKS_TIMEOUT)) {
      HttpGet httpGet = new HttpGet(jwksUrl);
      // Only the configured URL may answer: a redirect is a failed read, never another origin
      // supplying OpenCTI's keys.
      httpGet.setConfig(
          RequestConfig.custom()
              .setRedirectsEnabled(false)
              .setConnectionRequestTimeout(JWKS_TIMEOUT)
              .setResponseTimeout(JWKS_TIMEOUT)
              .build());
      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() != 200) {
              log.warn("JWKS fetch from {} returned HTTP {}", jwksUrl, response.getCode());
              return null;
            }
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
          });
    } catch (Exception e) {
      log.warn("Failed to fetch JWKS from {}", jwksUrl, e);
      return null;
    }
  }

  private String extractUnverifiedIssuer(String token) throws AuthenticationError {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        throw new AuthenticationError("Malformed JWT: expected at least 2 parts");
      }
      JsonNode payload =
          objectMapper.readTree(
              new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
      JsonNode issNode = payload.get("iss");
      if (issNode == null || !issNode.isTextual()) {
        throw new AuthenticationError("JWT has no 'iss' claim");
      }
      return issNode.asText();
    } catch (AuthenticationError e) {
      throw e;
    } catch (Exception e) {
      throw new AuthenticationError("Malformed JWT payload");
    }
  }
}
