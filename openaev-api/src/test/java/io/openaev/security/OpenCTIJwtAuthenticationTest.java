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
import io.openaev.opencti.connectors.impl.IocValidationConnector;
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
import javax.sql.DataSource;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
@WithMockOpenCTIConfig(url = "public_url", token = "auth token")
public class OpenCTIJwtAuthenticationTest extends IntegrationTest {
  @MockitoSpyBean private OpenCTIConnectorService openCTIConnectorService;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
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

  private Stream<Arguments> iocValidationAuthorization() throws Exception {
    JwtFixture.Bundle validJwtJwk = JwtFixture.generateConnectorJwtBundle(false);
    JwtFixture.Bundle otherKeyJwtJwk = JwtFixture.generateConnectorJwtBundle(false);
    JwtFixture.Bundle expiredJwtJwk = JwtFixture.generateConnectorJwtBundle(true);
    String configuredTenantId = TenantContext.getCurrentTenant();
    String otherTenantId = UUID.randomUUID().toString();

    return Stream.of(
        Arguments.of(
            null,
            validJwtJwk.jwks(),
            false,
            "Given no token, the IOC validation intake should get 401 Unauthorized status",
            configuredTenantId),
        Arguments.of(
            "Bearer " + validJwtJwk.jwtToken(),
            validJwtJwk.jwks(),
            true,
            "Given the valid JWT of the IOC validation connector alone, the intake should authorize",
            configuredTenantId),
        Arguments.of(
            "Bearer " + otherKeyJwtJwk.jwtToken(),
            validJwtJwk.jwks(),
            false,
            "Given a JWT signed with another key, the IOC validation intake should not authorize",
            configuredTenantId),
        Arguments.of(
            "Bearer " + expiredJwtJwk.jwtToken(),
            expiredJwtJwk.jwks(),
            false,
            "Given an expired JWT, the IOC validation intake should not authorize",
            configuredTenantId),
        Arguments.of(
            "Bearer " + validJwtJwk.jwtToken(),
            validJwtJwk.jwks(),
            false,
            "Given the valid JWT of the IOC validation connector, the intake of another tenant"
                + " should not authorize",
            otherTenantId));
  }

  private IocValidationConnector iocValidationConnector(String tenantId, String jwks) {
    IocValidationConnector connector = new IocValidationConnector();
    connector.setJwks(jwks);
    connector.setOpenCTIConfig(openCTIConfig.getOpencti().get(TenantContext.getCurrentTenant()));
    connector.setTenantId(tenantId);
    return connector;
  }

  // The intake suspends any caller transaction: its user must be committed to be found there, so
  // these cases run outside the test transaction and remove what they created.
  @ParameterizedTest(name = "{3}")
  @MethodSource("iocValidationAuthorization")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void processIocValidation_authorizationOpenCti(
      String authHeader,
      String jwks,
      Boolean isAuthorized,
      String displayName,
      String requestedTenantId)
      throws Exception {
    String configuredTenantId = TenantContext.getCurrentTenant();
    // Only the IOC validation connector of the tenant holds a key set to verify the JWT with
    Mockito.doReturn(Optional.empty())
        .when(openCTIConnectorService)
        .getConnectorBase(configuredTenantId);
    Mockito.doReturn(Optional.of(iocValidationConnector(configuredTenantId, jwks)))
        .when(openCTIConnectorService)
        .getIocValidationConnector(configuredTenantId);
    if (!configuredTenantId.equals(requestedTenantId)) {
      // The other tenant has an IOC validation connector of its own, with another key set
      Mockito.doReturn(
              Optional.of(
                  iocValidationConnector(
                      requestedTenantId, JwtFixture.generateConnectorJwtBundle(false).jwks())))
          .when(openCTIConnectorService)
          .getIocValidationConnector(requestedTenantId);
    }

    User user =
        userComposer
            .forUser(UserFixture.getUserWithDefaultEmail())
            .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue("auth token")))
            .persist()
            .get();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    try {
      jdbc.update(
          "INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
          user.getId(),
          Tenant.DEFAULT_TENANT_UUID);

      String urlPrefix =
          TENANT_STIX_URI.replaceAll("\\{" + TENANT_ID_PATH_VARIABLE + "}", requestedTenantId);
      var request =
          post(urlPrefix + "/process-ioc-validation")
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
    } finally {
      jdbc.update("DELETE FROM tokens WHERE token_user = ?", user.getId());
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", user.getId());
      jdbc.update("DELETE FROM users_groups WHERE user_id = ?", user.getId());
      jdbc.update("DELETE FROM users WHERE user_id = ?", user.getId());
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
