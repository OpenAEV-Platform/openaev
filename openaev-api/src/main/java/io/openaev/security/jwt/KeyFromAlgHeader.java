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
  /*
  Note: while this could theoretically support more algorithms, do not expand this
  collection without assigning a specific algorithm indicator on the stored key.
  See https://www.rfc-editor.org/rfc/rfc8725#section-3.1:
     """
     each key MUST be used with exactly one algorithm, and this MUST be checked
     when the cryptographic operation is performed
     """
  While we only have a single algorithm defined here it is de facto the case.
  */
  private final Set<SignatureAlgorithm> supportedAlgorithms = Set.of(Jwts.SIG.EdDSA);
  private final String rawKeyMaterial;

  public KeyFromAlgHeader(String rawKeyMaterial) {
    this.rawKeyMaterial = rawKeyMaterial;
  }

  @Override
  protected Key locate(JwsHeader header) {
    String alg = header.getAlgorithm();

    // Enforce 'alg' validation as per https://www.rfc-editor.org/rfc/rfc8725#section-3.1
    if (supportedAlgorithms.stream()
        .noneMatch(signatureAlgorithm -> signatureAlgorithm.getId().equalsIgnoreCase(alg))) {
      log.debug("Header 'alg' {} is not supported.", alg);
      return null;
    }

    try {
      X509EncodedKeySpec keySpec =
          new X509EncodedKeySpec(Base64.getDecoder().decode(this.rawKeyMaterial));
      KeyFactory keyFactory = KeyFactory.getInstance(alg);
      return keyFactory.generatePublic(keySpec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      log.debug(
          "Could not generate a public key with the specified algorithm {} and key material",
          alg,
          e);
      return null;
    }
  }
}
