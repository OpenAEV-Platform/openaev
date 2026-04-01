package io.openaev.utils.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Curve;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.util.Date;

public class JwtFixture {
  public record TokenKeyPair(String jwtToken, String jwks) {}

  //
  //  public static TokenKeyPair generateTokenKeyPairWithKey(String key, boolean expired) {
  //
  //  }

  public static TokenKeyPair generateTokenKeyPair(boolean expired) throws Exception {
    Curve curve = Jwks.CRV.Ed25519;
    KeyPair pair = curve.keyPair().build();

    long offset = expired ? -60 * 1000L : 60 * 1000L;

    String jwt =
        Jwts.builder()
            .issuer("opencti")
            .subject("connector")
            .header()
            .keyId("test-123")
            .and()
            .expiration(new Date(new Date().getTime() + offset))
            .signWith(pair.getPrivate(), Jwts.SIG.EdDSA)
            .compact();

    JWK jwk =
        JWK.parse(
            new ObjectMapper()
                .writeValueAsString(Jwks.builder().id("test-123").key(pair.getPublic()).build()));
    String jwksJson = new JWKSet(jwk).toString();
    return new TokenKeyPair(jwt, jwksJson);
  }
}
