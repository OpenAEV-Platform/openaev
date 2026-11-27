package io.openaev.api.jwks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Single JWK entry in a JWKS response.
 *
 * <p>Uses a flat map representation because JWK fields vary by key type (OKP, RSA, EC …). Jackson
 * serializes the map entries as top-level JSON properties via {@code @JsonAnyGetter} semantics
 * provided by the record's single-parameter nature — here we simply store the raw JWK map produced
 * by JJWT. A member the key does not carry ({@code key_ops}, {@code alg}, {@code use}) is left out
 * of the JSON, not sent as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JwkOutput(
    @JsonProperty("kty") String kty,
    @JsonProperty("crv") String crv,
    @JsonProperty("x") String x,
    @JsonProperty("kid") String kid,
    @JsonProperty("key_ops") List<String> keyOps,
    @JsonProperty("alg") String alg,
    @JsonProperty("use") String use) {

  /** Converts a JJWT {@code Jwk} (which implements {@code Map<String, Object>}) to this DTO. */
  @SuppressWarnings("unchecked")
  public static JwkOutput from(Map<String, Object> jwkMap) {
    Object ops = jwkMap.get("key_ops");
    List<String> keyOpsList =
        ops instanceof Collection<?> ? new ArrayList<>((Collection<String>) ops) : null;
    return new JwkOutput(
        (String) jwkMap.get("kty"),
        (String) jwkMap.get("crv"),
        (String) jwkMap.get("x"),
        (String) jwkMap.get("kid"),
        keyOpsList,
        (String) jwkMap.get("alg"),
        (String) jwkMap.get("use"));
  }
}
