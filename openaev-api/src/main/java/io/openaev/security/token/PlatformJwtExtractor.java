package io.openaev.security.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.openaev.database.model.User;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.UserService;
import io.openaev.xtmone.XtmOneConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlatformJwtExtractor implements ExtractorBase {
  private static final Set<String> TRUSTED_ISSUERS = Set.of("filigran-copilot");

  private final XtmOneConfig xtmOneConfig;
  private final UserService userService;

  @Override
  public String extractToken(String value) throws ConnectorError, JwtException {

    // TODO integrate this with exception logic
    if (value == null) {
      log.debug("[XTM One Auth] No raw bearer token found");
      return null;
    }
    if (xtmOneConfig == null || !xtmOneConfig.isConfigured()) {
      log.debug("[XTM One Auth] XTM One not configured, skipping platform JWT check");
      return null;
    }
    String secret = xtmOneConfig.getToken();
    if (secret == null || secret.isBlank()) {
      log.debug("[XTM One Auth] XTM One token is blank");
      return null;
    }
    // TODO end

    byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    SecretKey sigVerificationKey = new SecretKeySpec(secretBytes, "HmacSHA256");

    try {
      Jws<Claims> jws = Jwts.parser()
              .verifyWith(sigVerificationKey)
              .build()
              .parseSignedClaims(value);

      Claims claims = jws.getPayload();

      if(!TRUSTED_ISSUERS.contains(claims.getIssuer())) {
        throw new JwtException("Issuer not trusted.");
      }

      String emailClaim = claims.get("email", String.class);

      User user = userService.findByEmailIgnoreCase(emailClaim).orElseThrow();

      return user.getTokens().get(0).getValue();
    } catch (JwtException e) {
      log.debug("Problem authenticating with platform JWT");
      throw e;
    } catch (Exception e) {
      log.debug("Unexpected error");
      throw e;
    }
  }
}
