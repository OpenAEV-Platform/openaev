package io.openaev.security.jwt;

import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.security.SignatureAlgorithm;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyFromAlgHeader extends LocatorAdapter<Key> {
  private final Set<SignatureAlgorithm> supportedAlgorithms = Set.of(Jwts.SIG.EdDSA);
  private final String rawKeyMaterial;

  public KeyFromAlgHeader(String rawKeyMaterial) {
    this.rawKeyMaterial = rawKeyMaterial;
  }

  @Override
  protected Key locate(JwsHeader header) {
    if (supportedAlgorithms.stream()
        .noneMatch(
            signatureAlgorithm ->
                signatureAlgorithm.getId().equalsIgnoreCase(header.getAlgorithm()))) {
      log.debug("Header 'alg' {} is not supported.", header.getAlgorithm());
      return null;
    }

    try {
      X509EncodedKeySpec keySpec =
          new X509EncodedKeySpec(Base64.getDecoder().decode(this.rawKeyMaterial));
      KeyFactory keyFactory = KeyFactory.getInstance(header.getAlgorithm());
      return keyFactory.generatePublic(keySpec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      log.debug(
          "Could not generate a public key with the specified algorithm {} and key material",
          header.getAlgorithm(),
          e);
      return null;
    }
  }
}
