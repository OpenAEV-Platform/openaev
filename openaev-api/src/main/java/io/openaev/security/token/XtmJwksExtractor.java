package io.openaev.security.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Jwks;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.User;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.utils.StringUtils;
import io.openaev.xtmone.XtmOneConfig;
import io.openaev.xtmone.XtmOneIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Component;

/**
 * Validates incoming cross-platform JWTs using JWKS discovery.
 *
 * <p>When a JWT is received, this extractor:
 *
 * <ol>
 *   <li>Peeks at the unverified payload to extract the {@code iss} claim
 *   <li>Checks that the issuer names XTM One: its configured URL or the identity it publishes
 *       ({@link XtmOneIdentity})
 *   <li>Fetches (and caches) XTM One's JWKS from {@code {configured url}/xtm/auth/jwks}, the URL
 *       OpenAEV reaches it on whatever its issuer says
 *   <li>Resolves the signing key by {@code kid} from the cached JWKS
 *   <li>Validates the JWT signature, expiration and audience (OpenAEV's base URL, compared
 *       normalized)
 *   <li>Resolves the user by the {@code email} claim
 * </ol>
 *
 * <p>The JWKS is cached for 1 hour. An unknown {@code kid} triggers a forced cache refresh.
 */
@Component
@Slf4j(topic = "XTM JWKS Authentication")
@RequiredArgsConstructor
public class XtmJwksExtractor implements ExtractorBase {

  /**
   * Request attribute stamped (only) on a request whose bearer was validated as an XTM One
   * cross-platform JWT: trusted issuer, JWKS signature, expected audience, resolved user. Request
   * attributes are server-side only, so a client can never forge it. {@link
   * io.openaev.config.TxCtxArgumentResolver} keys on it to grant run-authoritative tenant scope to
   * the orchestrator callbacks - any caller without it keeps caller-authorized resolution.
   */
  public static final String CROSS_PLATFORM_ATTRIBUTE =
      "io.openaev.security.xtmCrossPlatformAuthenticated";

  private static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);

  private final XtmOneConfig xtmOneConfig;
  private final UserService userService;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper objectMapper;
  private final OpenAEVConfig openAEVConfig;
  private final XtmOneIdentity xtmOneIdentity;

  private final ConcurrentHashMap<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

  private record CachedJwks(Instant fetchedAt, String jwksJson) {}

  @Override
  public Optional<User> authUser(String value, HttpServletRequest request)
      throws JwtException, AuthenticationError {
    if (value == null) {
      throw new AuthenticationError("No bearer token found");
    }
    if (!xtmOneConfig.isConfigured()) {
      throw new AuthenticationError("XTM One not configured, skipping JWKS JWT check");
    }

    String issuer = extractUnverifiedIssuer(value);
    if (!xtmOneIdentity.isXtmOneIssuer(issuer)) {
      throw new AuthenticationError("Untrusted JWKS issuer: " + issuer);
    }

    String jwksUrl = xtmOneConfig.getUrl() + "/xtm/auth/jwks";
    Jws<Claims> jws =
        Jwts.parser()
            .keyLocator(header -> resolveKey(jwksUrl, (String) header.get("kid")))
            .build()
            .parseSignedClaims(value);
    requireOwnAudience(jws);
    Claims claims = jws.getPayload();

    String email = claims.get("email", String.class);
    if (StringUtils.isBlank(email)) {
      throw new AuthenticationError("The JWT does not contain the required 'email' claim.");
    }

    Optional<User> user = userService.findByEmailIgnoreCase(email);
    // Stamp the service-identity marker ONLY when this fully validated token resolved a user: a
    // failed or unresolved authentication must never leave the request marked as the orchestrator.
    if (user.isPresent() && request != null) {
      request.setAttribute(CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
    }
    return user;
  }

  // -- PRIVATE --

  /** Refuses a token none of whose audiences is OpenAEV's base URL (compared normalized). */
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
    // First attempt: look in cache
    Key key = findKeyInCache(jwksUrl, kid);
    if (key != null) {
      return key;
    }

    // Force-refresh on unknown kid
    refreshJwks(jwksUrl);
    key = findKeyInCache(jwksUrl, kid);
    if (key != null) {
      return key;
    }

    throw new JwtException("No matching key found for kid: " + kid + " at " + jwksUrl);
  }

  private Key findKeyInCache(String jwksUrl, String kid) {
    CachedJwks cached = jwksCache.get(jwksUrl);
    if (cached == null) {
      refreshJwks(jwksUrl);
      cached = jwksCache.get(jwksUrl);
    }
    if (cached == null) {
      return null;
    }

    // Refresh if TTL expired
    if (cached.fetchedAt().plus(JWKS_CACHE_TTL).isBefore(Instant.now())) {
      refreshJwks(jwksUrl);
      cached = jwksCache.get(jwksUrl);
    }
    if (cached == null) {
      return null;
    }

    return Jwks.setParser().build().parse(cached.jwksJson()).getKeys().stream()
        .filter(k -> !StringUtils.isBlank(kid) && kid.equals(k.getId()))
        .findFirst()
        .map(jwk -> (Key) jwk.toKey())
        .orElse(null);
  }

  private void refreshJwks(String jwksUrl) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(jwksUrl);
      String jwksJson =
          httpClient.execute(
              httpGet,
              response -> {
                if (response.getCode() != 200) {
                  log.warn("JWKS fetch from {} returned HTTP {}", jwksUrl, response.getCode());
                  return null;
                }
                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
              });
      if (jwksJson != null) {
        jwksCache.put(jwksUrl, new CachedJwks(Instant.now(), jwksJson));
        log.debug("Refreshed JWKS cache from {}", jwksUrl);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch JWKS from {}", jwksUrl, e);
    }
  }

  private String extractUnverifiedIssuer(String token) throws AuthenticationError {
    try {
      String[] parts = token.split("\\.");
      if (parts.length < 2) {
        throw new AuthenticationError("Malformed JWT: expected at least 2 parts");
      }
      String payloadJson =
          new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      JsonNode payload = objectMapper.readTree(payloadJson);
      JsonNode issNode = payload.get("iss");
      if (issNode == null || issNode.isNull()) {
        throw new AuthenticationError("JWT has no 'iss' claim");
      }
      return issNode.asText();
    } catch (AuthenticationError e) {
      throw e;
    } catch (Exception e) {
      throw new AuthenticationError("Failed to extract issuer from JWT", e);
    }
  }
}
