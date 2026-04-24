package io.openaev.rest.xtm_auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Standard JWKS (JSON Web Key Set) response. */
public record JwksOutput(@JsonProperty("keys") List<JwkOutput> keys) {}
