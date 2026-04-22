package io.openaev.rest.xtm_auth;

import io.jsonwebtoken.security.Jwks;
import io.openaev.aop.LogExecutionTime;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.xtm_auth.XtmAuthKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the platform's public JWKS so that other Filigran products can verify JWTs emitted by
 * OpenAEV.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "XTM Auth", description = "Cross-platform authentication endpoints")
public class XtmAuthApi extends RestBehavior {

  public static final String XTM_AUTH_JWKS_URI = "/xtm/auth/jwks";

  private final XtmAuthKeyService keyService;

  @GetMapping(value = XTM_AUTH_JWKS_URI, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get JWKS",
      description =
          "Returns the public key(s) used to verify JWTs emitted by this OpenAEV instance. "
              + "Standard JWKS format with OKP/Ed25519 keys.")
  @ApiResponse(responseCode = "200", description = "JWKS payload")
  @LogExecutionTime
  public JwksOutput jwks() {
    var jwk =
        Jwks.builder()
            .key(keyService.getKeyPair().getPublic())
            .id(keyService.getKid())
            .operations()
            .add(Jwks.OP.VERIFY)
            .and()
            .build();
    return new JwksOutput(List.of(JwkOutput.from(jwk)));
  }
}
