package io.openaev.api.jwks;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.security.Jwks;
import io.openaev.utils.Ed25519KeyUtils;
import java.security.PublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWK of a JWKS response")
class JwkOutputTest {

  private static final byte[] SEED = new byte[32];

  private final ObjectMapper mapper = new ObjectMapper();

  private static PublicKey publicKey() throws Exception {
    return Ed25519KeyUtils.keyPair(SEED).getPublic();
  }

  @Test
  @DisplayName("a verify-only key carries key_ops, and no alg or use")
  void given_verifyOnlyKey_should_serveKeyOps() throws Exception {
    // Arrange
    var jwk =
        Jwks.builder().key(publicKey()).id("kid").operations().add(Jwks.OP.VERIFY).and().build();

    // Act
    String json = mapper.writeValueAsString(JwkOutput.from(jwk));

    // Assert
    assertThatJson(json).isObject().containsOnlyKeys("kty", "crv", "x", "kid", "key_ops");
    assertThatJson(json).node("key_ops").isEqualTo("[\"verify\"]");
  }

  @Test
  @DisplayName("a key with its algorithm and use carries them, and no key_ops")
  void given_algorithmAndUse_should_serveThem() throws Exception {
    // Arrange
    var jwk =
        Jwks.builder().key(publicKey()).id("kid").algorithm("EdDSA").publicKeyUse("sig").build();

    // Act
    String json = mapper.writeValueAsString(JwkOutput.from(jwk));

    // Assert
    assertThatJson(json).isObject().containsOnlyKeys("kty", "crv", "x", "kid", "alg", "use");
    assertThatJson(json).node("alg").isEqualTo("EdDSA");
    assertThatJson(json).node("use").isEqualTo("sig");
  }
}
