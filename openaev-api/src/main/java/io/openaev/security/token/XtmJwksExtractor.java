package io.openaev.security.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Jwks;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

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
    String trustedIssuer = normalizeUrl(xtmOneConfig.getUrl());

    if (!issuer.equals(trustedIssuer)) {
      throw new AuthenticationError("Untrusted JWKS issuer: " + issuer);
    }

    Claims claims =
        Jwts.parser()
            .keyLocator(header -> resolveKey(issuer, (String) header.get("kid")))
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
        .filter(k -> kid.equals(k.getId()))
        .findFirst()
        .map(jwk -> (Key) jwk.toKey())
        .orElse(null);
  }

  private void refreshJwks(String issuer) {
    try {
      String jwksUrl = issuer + "/xtm/auth/jwks";
      String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
      if (jwksJson != null) {
        jwksCache.put(issuer, new CachedJwks(Instant.now(), jwksJson));
        log.debug("Refreshed JWKS cache for issuer {}", issuer);
      }
    } catch (Exception e) {
      log.warn("Failed to fetch JWKS from {}: {}", issuer, e.getMessage());
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
      throw new AuthenticationError("Failed to extract issuer from JWT: " + e.getMessage());
    }
  }

  private static String normalizeUrl(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
