package io.openaev.rest.xtm_auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Single JWK entry in a JWKS response.
 *
 * <p>Uses a flat map representation because JWK fields vary by key type (OKP, RSA, EC …). Jackson
 * serializes the map entries as top-level JSON properties via {@code @JsonAnyGetter} semantics
 * provided by the record's single-parameter nature — here we simply store the raw JWK map produced
 * by JJWT.
 */
public record JwkOutput(
    @JsonProperty("kty") String kty,
    @JsonProperty("crv") String crv,
    @JsonProperty("x") String x,
    @JsonProperty("kid") String kid,
    @JsonProperty("key_ops") List<String> keyOps) {

  /** Converts a JJWT {@code Jwk} (which implements {@code Map<String, Object>}) to this DTO. */
  @SuppressWarnings("unchecked")
  public static JwkOutput from(Map<String, Object> jwkMap) {
    return new JwkOutput(
        (String) jwkMap.get("kty"),
        (String) jwkMap.get("crv"),
        (String) jwkMap.get("x"),
        (String) jwkMap.get("kid"),
        (List<String>) jwkMap.get("key_ops"));
  }
}
