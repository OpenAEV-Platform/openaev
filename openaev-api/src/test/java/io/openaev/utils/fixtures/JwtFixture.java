package io.openaev.utils.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.SignatureAlgorithm;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Date;

public class JwtFixture {
  public record Bundle(String jwtToken, String jwks, KeyPair keyPair) {}

  public static Bundle generateConnectorJwtBundle(boolean expired) throws Exception {
    return generateBundle("opencti", "connector", Jwts.SIG.EdDSA, expired);
  }

  public static Bundle generatePlatformJwtBundle(String subject, boolean expired) throws Exception {
    return generatePlatformJwtBundleWithSigAlgo(subject, Jwts.SIG.EdDSA, expired);
  }

  /**
   * Generates a JWT bundle with a URL-based issuer, suitable for testing the JWKS-based validation
   * flow where the issuer is a trusted platform URL (e.g. {@code https://xtmone.filigran.io}).
   */
  public static Bundle generateXtmJwksJwtBundle(
      String issuerUrl, String email, String audience, boolean expired) throws Exception {
    return generateBundle(issuerUrl, email, audience, Jwts.SIG.EdDSA, expired);
  }

  public static Bundle generatePlatformJwtBundleWithSigAlgo(
      String subject, SignatureAlgorithm signatureAlgorithm, boolean expired) throws Exception {
    return generateBundle("filigran-copilot", subject, signatureAlgorithm, expired);
  }

  public static Bundle generateForeignJwtBundle(String subject, boolean expired) throws Exception {
    return generateBundle("rejected issuer", subject, Jwts.SIG.EdDSA, expired);
  }

  private static Bundle generateBundle(
      String issuer, String subject, SignatureAlgorithm signatureAlgorithm, boolean expired)
      throws Exception {
    return generateBundle(issuer, subject, null, signatureAlgorithm, expired);
  }

  private static Bundle generateBundle(
      String issuer,
      String subject,
      String audience,
      SignatureAlgorithm signatureAlgorithm,
      boolean expired)
      throws Exception {
    KeyPair pair = getKeyPairForSigAlgo(signatureAlgorithm);

    long offset = expired ? -60 * 1000L : 60 * 1000L;

    var builder =
        Jwts.builder()
            .issuer(issuer)
            .subject(subject)
            .claim("email", subject)
            .header()
            .keyId("test-123")
            .and()
            .expiration(new Date(new Date().getTime() + offset));

    if (audience != null) {
      builder.audience().add(audience);
    }

    String jwt = builder.signWith(pair.getPrivate(), signatureAlgorithm).compact();

    JWK jwk =
        JWK.parse(
            new ObjectMapper()
                .writeValueAsString(Jwks.builder().id("test-123").key(pair.getPublic()).build()));
    String jwksJson = new JWKSet(jwk).toString();
    return new Bundle(jwt, jwksJson, pair);
  }

  public static String b64(byte[] data) {
    return Base64.getEncoder().encodeToString(data);
  }

  private static KeyPair getKeyPairForSigAlgo(SignatureAlgorithm signatureAlgorithm) {
    return switch (signatureAlgorithm.getId()) {
      case "EdDSA" -> Jwks.CRV.Ed25519.keyPair().build();
      case "ES256" -> Jwks.CRV.P256.keyPair().build();
      default -> null;
    };
  }
}
