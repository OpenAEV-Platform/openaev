package io.openaev.security.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.openaev.database.model.User;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.xtmone.XtmOneConfig;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j(topic = "XTM One Authentication")
@RequiredArgsConstructor
public class PlatformJwtExtractor implements ExtractorBase {
  private static final Set<String> TRUSTED_ISSUERS = Set.of("filigran-copilot");

  private final XtmOneConfig xtmOneConfig;
  private final UserService userService;

  @Override
  public Optional<User> authUser(String value) throws JwtException, AuthenticationError {
    if (value == null) {
      String message = "No raw bearer token found";
      log.debug(message);
      throw new AuthenticationError(message);
    }
    if (xtmOneConfig == null || !xtmOneConfig.isConfigured()) {
      String message = "XTM One not configured, skipping platform JWT check";
      log.debug(message);
      throw new AuthenticationError(message);
    }

    SecretKey sigVerificationKey =
        new SecretKeySpec(xtmOneConfig.getToken().getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    Jws<Claims> jws = Jwts.parser().verifyWith(sigVerificationKey).build().parseSignedClaims(value);

    Claims claims = jws.getPayload();

    if (!TRUSTED_ISSUERS.contains(claims.getIssuer())) {
      throw new JwtException("Issuer not trusted.");
    }

    String emailClaim = claims.get("email", String.class);

    return userService.findByEmailIgnoreCase(emailClaim);
  }
}
