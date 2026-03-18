package io.openaev.security;

import static io.openaev.api.stix_process.StixApi.STIX_URI;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.*;
import io.openaev.IntegrationTest;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockConfig.WithMockOpenCTIConfig;
import java.security.KeyPair;
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
@WithMockOpenCTIConfig(url = "public_url", token = "auth token")
public class OpenCTIJwtAuthenticationTest extends IntegrationTest {
  @SpyBean private OpenCTIConnectorService openCTIConnectorService;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @Autowired private MockMvc mvc;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;
  @Autowired private UserComposer userComposer;
  @Autowired private TokenComposer tokenComposer;
  @Autowired private OpenCTIConfig openCTIConfig;

  @BeforeEach
  void setUp() throws Exception {
    userComposer.reset();
    tokenComposer.reset();
    new Manager(List.of(manualInjectorIntegrationFactory)).monitorIntegrations();
  }

  record JwtFixture(String jwtToken, String jwks) {}

  private JwtFixture generateJwtJwk(boolean expired) throws Exception {
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

    JWK jwk = JWK.parse(Jwks.builder().id("test-123").key(pair.getPublic()).build().toString());
    String jwksJson = new JWKSet(jwk).toString();
    return new JwtFixture(jwt, jwksJson);
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
            "Given expired valid JWT should not authorize"));
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("authorizationOpenCTI")
  void processBundle_authorizationOpenCti(
      String authHeader, String jwks, Boolean isAuthorized, String displayName) throws Exception {
    if (jwks != null) {
      SecurityCoverageConnector c = new SecurityCoverageConnector();
      c.setJwks(jwks);
      c.setOpenctiConfig(openCTIConfig);
      Mockito.doReturn(Optional.of(c)).when(openCTIConnectorService).getConnectorBase();
    }

    userComposer
        .forUser(UserFixture.getUserWithDefaultEmail())
        .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue("auth token")))
        .persist();
    entityManager.flush();

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
