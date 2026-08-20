package io.openaev.security;

import static io.openaev.api.stix_process.StixApi.TENANT_STIX_URI;
import static io.openaev.config.TenantUriUtils.TENANT_ID_PATH_VARIABLE;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.fixtures.JwtFixture;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockConfig.WithMockOpenCTIConfig;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
@WithMockOpenCTIConfig(url = "public_url", token = "auth token")
public class OpenCTIJwtAuthenticationTest extends IntegrationTest {
  @MockitoSpyBean private OpenCTIConnectorService openCTIConnectorService;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @Autowired private MockMvc mvc;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;
  @Autowired private UserComposer userComposer;
  @Autowired private TokenComposer tokenComposer;
  @Autowired private XtmConfig openCTIConfig;

  @BeforeEach
  void setUp() throws Exception {
    userComposer.reset();
    tokenComposer.reset();
    manualInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
  }

  private Stream<Arguments> authorizationOpenCTI() throws Exception {
    JwtFixture.Bundle validJwtJwk = JwtFixture.generateConnectorJwtBundle(false);
    JwtFixture.Bundle expiredJwtJwk = JwtFixture.generateConnectorJwtBundle(true);
    String configuredTenantId = TenantContext.getCurrentTenant();
    String otherTenantId = UUID.randomUUID().toString();

    return Stream.of(
        Arguments.of(
            null,
            null,
            false,
            "Given no token should get 401 Unauthorized status",
            configuredTenantId,
            configuredTenantId),
        Arguments.of(
            adminToken,
            null,
            true,
            "Given Admin token should be authorized",
            configuredTenantId,
            configuredTenantId),
        Arguments.of(
            "Bearer " + validJwtJwk.jwtToken(),
            validJwtJwk.jwks(),
            true,
            "Given valid JWT should authorize",
            configuredTenantId,
            configuredTenantId),
        Arguments.of(
            "Bearer " + validJwtJwk.jwtToken(),
            validJwtJwk.jwks(),
            false,
            "Given valid JWT for configured tenant, requesting other tenant should fail",
            configuredTenantId,
            otherTenantId),
        Arguments.of(
            "Bearer " + expiredJwtJwk.jwtToken(),
            expiredJwtJwk.jwks(),
            false,
            "Given expired valid JWT should not authorize",
            configuredTenantId,
            configuredTenantId));
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("authorizationOpenCTI")
  void processBundle_authorizationOpenCti(
      String authHeader,
      String jwks,
      Boolean isAuthorized,
      String displayName,
      String configuredTenantId,
      String requestedTenantId)
      throws Exception {
    if (jwks != null) {
      SecurityCoverageConnector c = new SecurityCoverageConnector();
      c.setJwks(jwks);
      c.setOpenCTIConfig(openCTIConfig.getOpencti().get(configuredTenantId));
      c.setTenantId(configuredTenantId);
      Mockito.doReturn(Optional.of(c))
          .when(openCTIConnectorService)
          .getConnectorBase(configuredTenantId);
    }

    String urlPrefix =
        TENANT_STIX_URI.replaceAll("\\{" + TENANT_ID_PATH_VARIABLE + "}", requestedTenantId);

    User user =
        userComposer
            .forUser(UserFixture.getUserWithDefaultEmail())
            .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue("auth token")))
            .persist()
            .get();
    tenantRepository.addUserToTenant(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();

    var request =
        post(urlPrefix + "/process-bundle")
            .contentType(MediaType.APPLICATION_JSON)
            .content("")
            .with(csrf());

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

  @Test
  void processBundle_withServletContextPath_authorizesConnectorJwtForUrlTenant() throws Exception {
    JwtFixture.Bundle validJwtJwk = JwtFixture.generateConnectorJwtBundle(false);
    String tenantId = TenantContext.getCurrentTenant();

    SecurityCoverageConnector c = new SecurityCoverageConnector();
    c.setJwks(validJwtJwk.jwks());
    c.setOpenCTIConfig(openCTIConfig.getOpencti().get(tenantId));
    c.setTenantId(tenantId);
    Mockito.doReturn(Optional.of(c)).when(openCTIConnectorService).getConnectorBase(tenantId);

    User user =
        userComposer
            .forUser(UserFixture.getUserWithDefaultEmail())
            .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue("auth token")))
            .persist()
            .get();
    tenantRepository.addUserToTenant(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();

    String contextPath = "/openaev";
    String urlPrefix = TENANT_STIX_URI.replaceAll("\\{" + TENANT_ID_PATH_VARIABLE + "}", tenantId);

    // the tenant resolution used for connector JWT auth must still work when the
    // application is deployed under a non-root servlet context path
    var request =
        post(contextPath + urlPrefix + "/process-bundle")
            .contextPath(contextPath)
            .contentType(MediaType.APPLICATION_JSON)
            .content("")
            .with(csrf())
            .header("Authorization", "Bearer " + validJwtJwk.jwtToken());

    mvc.perform(request)
        .andExpect(
            result ->
                assertNotEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus()));
  }
}
