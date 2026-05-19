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
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
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
 *   <li>Checks that the issuer matches the configured XTM One URL (trusted issuer)
 *   <li>Fetches (and caches) the issuer's JWKS from {@code {iss}/xtm/auth/jwks}
 *   <li>Resolves the signing key by {@code kid} from the cached JWKS
 *   <li>Validates the JWT signature and expiration
 *   <li>Resolves the user by the {@code email} claim
 * </ol>
 *
 * <p>The JWKS is cached for 1 hour. An unknown {@code kid} triggers a forced cache refresh.
 */
@Component
@Slf4j(topic = "XTM JWKS Authentication")
@RequiredArgsConstructor
public class XtmJwksExtractor implements ExtractorBase {

  private static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);

  private final XtmOneConfig xtmOneConfig;
  private final UserService userService;
  private final HttpClientFactory httpClientFactory;
  private final ObjectMapper objectMapper;
  private final OpenAEVConfig openAEVConfig;

  private final ConcurrentHashMap<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

  private record CachedJwks(Instant fetchedAt, String jwksJson) {}

  @Override
  public Optional<User> authUser(String value) throws JwtException, AuthenticationError {
    if (value == null) {
      throw new AuthenticationError("No bearer token found");
    }
    if (!xtmOneConfig.isConfigured()) {
      throw new AuthenticationError("XTM One not configured, skipping JWKS JWT check");
    }

    String issuer = extractUnverifiedIssuer(value);
    List<String> trustedIssuers = buildTrustedIssuers();

    if (!trustedIssuers.contains(issuer)) {
      throw new AuthenticationError("Untrusted JWKS issuer: " + issuer);
    }

    Claims claims =
        Jwts.parser()
            .keyLocator(header -> resolveKey(issuer, (String) header.get("kid")))
            .requireAudience(openAEVConfig.getBaseUrl())
            .build()
            .parseSignedClaims(value)
            .getPayload();

    String email = claims.get("email", String.class);
    if (StringUtils.isBlank(email)) {
      throw new AuthenticationError("The JWT does not contain the required 'email' claim.");
    }

    return userService.findByEmailIgnoreCase(email);
  }

  // -- PRIVATE --

  /**
   * Builds the list of trusted JWKS issuers from configuration. Currently, includes the XTM One
   * URL; additional trusted URLs can be appended here as the platform evolves (e.g. peer
   * instances).
   */
  private List<String> buildTrustedIssuers() {
    return Stream.of(xtmOneConfig.getUrl())
        .filter(Objects::nonNull)
        .filter(url -> !url.isEmpty())
        .toList();
  }

  private Key resolveKey(String issuer, String kid) {
    // First attempt: look in cache
    Key key = findKeyInCache(issuer, kid);
    if (key != null) {
      return key;
    }

    // Force-refresh on unknown kid
    refreshJwks(issuer);
    key = findKeyInCache(issuer, kid);
    if (key != null) {
      return key;
    }

    throw new JwtException("No matching key found for kid: " + kid + " from issuer: " + issuer);
  }

  private Key findKeyInCache(String issuer, String kid) {
    CachedJwks cached = jwksCache.get(issuer);
    if (cached == null) {
      refreshJwks(issuer);
      cached = jwksCache.get(issuer);
    }
    if (cached == null) {
      return null;
    }

    // Refresh if TTL expired
    if (cached.fetchedAt().plus(JWKS_CACHE_TTL).isBefore(Instant.now())) {
      refreshJwks(issuer);
      cached = jwksCache.get(issuer);
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

  private void refreshJwks(String issuer) {
    String jwksUrl = issuer + "/xtm/auth/jwks";
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
        jwksCache.put(issuer, new CachedJwks(Instant.now(), jwksJson));
        log.debug("Refreshed JWKS cache for issuer {}", issuer);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch JWKS from {}", issuer, e);
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
