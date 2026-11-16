package io.openaev.xtmhub;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The public half of the instance's XTM Hub key, as a JWK (RFC 8037). It never carries the private
 * part ({@code d}): it is what leaves the instance, at registration, at enrollment and on the JWKS
 * endpoint.
 */
public record XtmHubPublicJwk(
    @JsonProperty("kty") String kty,
    @JsonProperty("crv") String crv,
    @JsonProperty("alg") String alg,
    @JsonProperty("use") String use,
    @JsonProperty("kid") String kid,
    @JsonProperty("x") String x) {}
