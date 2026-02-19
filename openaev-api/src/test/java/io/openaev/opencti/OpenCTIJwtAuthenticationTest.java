package io.openaev.opencti;

import static io.openaev.api.stix_process.StixApi.STIX_URI;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.openaev.IntegrationTest;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
public class OpenCTIJwtAuthenticationTest extends IntegrationTest {
  @SpyBean private OpenCTIConnectorService openCTIConnectorService;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @Autowired private MockMvc mvc;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  @BeforeEach
  void setUp() throws Exception {
    new Manager(List.of(manualInjectorIntegrationFactory)).monitorIntegrations();
  }

  record JwtFixture(String jwtToken, String jwks) {}

  private JwtFixture generateJwtJwk(boolean expired) throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-123").generate();

    long offset = expired ? -60 * 1000L : 60 * 1000L;
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject("connector")
            .issuer("opencti")
            .expirationTime(new Date(new Date().getTime() + offset))
            .build();

    SignedJWT signedJWT =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(jwk.getKeyID()).build(), claimsSet);
    signedJWT.sign(new Ed25519Signer(jwk));

    return new JwtFixture(signedJWT.serialize(), new JWKSet(jwk.toPublicJWK()).toString(false));
  }

  private Stream<Arguments> authorizationOpenCTI() throws Exception {
    JwtFixture validJwtJwk = generateJwtJwk(false);
    JwtFixture expiredJwtJwk = generateJwtJwk(true);

    return Stream.of(
        Arguments.of(null, null, false, "Given no token should get 401 Unauthorized status"),
        Arguments.of(adminToken, null, true, "Given Admin token should be authorized"),
        Arguments.of(
            "Bearer " + validJwtJwk.jwtToken,
            validJwtJwk.jwks,
            true,
            "Given valid JWT should authorized"),
        Arguments.of(
            "Bearer " + expiredJwtJwk.jwtToken,
            expiredJwtJwk.jwks,
            false,
            "Given expired valid JWT should authorized"));
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("authorizationOpenCTI")
  void processBundle_authorizationOpenCti(
      String authHeader, String jwks, Boolean isAuthorized, String displayName) throws Exception {
    if (jwks != null) {
      SecurityCoverageConnector c = new SecurityCoverageConnector();
      c.setJwks(jwks);
      Mockito.doReturn(Optional.of(c)).when(openCTIConnectorService).getConnectorBase();
    }

    var request =
        post(STIX_URI + "/process-bundle").contentType(MediaType.APPLICATION_JSON).content("");

    if (authHeader != null) {
      request = request.header("Authorization", authHeader);
    }

    if (isAuthorized) {
      mvc.perform(request)
          .andExpect(
              result ->
                  assertNotEquals(
                      HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus()));
    } else {
      mvc.perform(request).andExpect(status().isUnauthorized());
    }
  }
}
