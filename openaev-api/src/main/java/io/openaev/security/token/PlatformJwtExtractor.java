package io.openaev.security.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.openaev.database.model.User;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.xtmone.XtmOneConfig;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
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
  public Optional<User> authUser(String value)
      throws JwtException, AuthenticationError, NoSuchAlgorithmException, InvalidKeySpecException {
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

    /*
     JWT is assumed signed with the EdDSA algorithm as per Filigran standard.
     The configured key material retrieved with `XtmOneConfig.getToken()` must
     be an ASN.1/DER encoded public key, itself Base64-encoded.
    */
    X509EncodedKeySpec keySpec =
        new X509EncodedKeySpec(Base64.getDecoder().decode(xtmOneConfig.getToken()));
    KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
    PublicKey pubkey = keyFactory.generatePublic(keySpec);

    Jws<Claims> jws = Jwts.parser().verifyWith(pubkey).build().parseSignedClaims(value);

    Claims claims = jws.getPayload();

    if (!TRUSTED_ISSUERS.contains(claims.getIssuer())) {
      throw new JwtException("Issuer not trusted.");
    }

    String emailClaim = claims.get("email", String.class);

    return userService.findByEmailIgnoreCase(emailClaim);
  }
}
