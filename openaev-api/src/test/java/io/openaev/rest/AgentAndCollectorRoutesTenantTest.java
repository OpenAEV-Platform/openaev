package io.openaev.rest;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ENDPOINT_URI;
import static io.openaev.rest.executor.ExecutorApi.AGENT_URI;
import static io.openaev.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openaev.rest.expectation.ExpectationApi.TENANT_INJECTS_EXPECTATIONS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.EndpointService;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The routes the agents, the implants and the collectors call take their tenant from the request's
 * scope: the tenant-prefixed route, the {@code X-Tenant-Ids} header, and with neither the single
 * tenant of the caller. The caller is a member of tenant B only and an admin, so the access check
 * passes on the non-prefixed route too (a non-admin of B is refused there, see {@code
 * ServiceAccountRoutesTenantTest}). Tenant A holds the same rows with the same source id, and the
 * caller is not a member of it unless a test says so. The callers of the default tenant are not
 * admins, as a tenant-unaware agent or collector. The ambient tenant of the test thread is cleared
 * before each request and falls back to the default tenant, so a route that reads it answers for
 * the default tenant.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Agent and collector routes take their tenant from the request scope")
class AgentAndCollectorRoutesTenantTest extends IntegrationTest {

  private static final String TENANT_AGENT_URI = TENANT_PREFIX + "/agent";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final Set<Capability> SERVICE_ACCOUNT_CAPABILITIES =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_ASSETS,
          Capability.MANAGE_ASSETS,
          Capability.INSTALL_AGENT);
  private static final String INSTALLER_PATH =
      "/installer/openaev/" + Endpoint.PLATFORM_TYPE.Linux.name() + "/" + EndpointService.SERVICE;
  private static final String INSTALLER_TOKEN_PATH = "/installer/openaev/token";
  private static final String LOGGING_POLL = "/logging/{sourceId}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;

  private String tenantA;
  private String tenantB;
  private String sourceId;
  private SeededExpectations expectationsOfA;
  private SeededExpectations expectationsOfB;
  private String originalDevFeatures;

  /** How a request names tenant B. */
  enum Route {
    NO_HEADER,
    HEADER,
    PREFIXED
  }

  @BeforeEach
  void seedCallerOfTenantBOnly() throws Exception {
    tenantB =
        tenantHelper
            .createTenantWithCapabilities(
                "agent-routes-b-" + UUID.randomUUID(), SERVICE_ACCOUNT_CAPABILITIES)
            .getId();
    tenantA = insertTenant("agent-routes-a-" + UUID.randomUUID());
    sourceId = UUID.randomUUID().toString();
    expectationsOfA = seedExpectations(tenantA);
    expectationsOfB = seedExpectations(tenantB);
    originalDevFeatures = openAEVConfig.getEnabledDevFeatures();
    setLoggingFlag(true);
  }

  @AfterEach
  void restore() {
    TenantContext.clearCurrentTenant();
    openAEVConfig.setEnabledDevFeatures(originalDevFeatures);
    Optional.ofNullable(cacheManager.getCache("global")).ifPresent(Cache::clear);
  }

  @Nested
  @DisplayName("Expectation polls of the agents and the collectors")
  class ExpectationPolls {

    static Stream<Arguments> polls() {
      return Stream.of(
          Arguments.of("", ExpectedRow.MANUAL),
          Arguments.of("/{sourceId}", ExpectedRow.MANUAL),
          Arguments.of("/assets/{sourceId}", ExpectedRow.PREVENTION),
          Arguments.of("/prevention", ExpectedRow.PREVENTION),
          Arguments.of("/prevention/{sourceId}", ExpectedRow.PREVENTION),
          Arguments.of("/detection", ExpectedRow.DETECTION),
          Arguments.of("/detection/{sourceId}", ExpectedRow.DETECTION),
          Arguments.of("/ai/{sourceId}", ExpectedRow.AGENTLESS_DETECTION),
          Arguments.of(LOGGING_POLL, ExpectedRow.LOGGING));
    }

    static Stream<Arguments> pollsOnEveryRoute() {
      return Stream.of(Route.values())
          .flatMap(route -> polls().map(poll -> Arguments.of(route, poll.get()[0], poll.get()[1])));
    }

    static Stream<String> pollPaths() {
      return polls().map(poll -> (String) poll.get()[0]);
    }

    @ParameterizedTest(name = "{0}, poll {1}")
    @MethodSource("pollsOnEveryRoute")
    @DisplayName("given expectations of A and B should return those of B only to B")
    void given_expectationsOfAAndB_should_returnThoseOfBOnly(
        Route route, String poll, ExpectedRow expected) throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      List<String> ids = pollExpectationIds(route, poll);

      // Assert
      assertThat(ids).contains(expected.of(expectationsOfB));
      assertThat(ids).doesNotContain(expected.of(expectationsOfA));
    }

    @ParameterizedTest(name = "poll {0}")
    @MethodSource("pollPaths")
    @DisplayName("given a header naming two tenants of the caller should answer 400")
    void given_headerNamingTwoTenants_should_answer400(String poll) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(INJECTS_EXPECTATIONS_URI + withSource(poll))
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest(name = "poll {0}")
    @MethodSource("pollPaths")
    @DisplayName("given a header naming a tenant the caller is not a member of should answer 403")
    void given_headerNamingANonMemberTenant_should_answer403(String poll) throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(INJECTS_EXPECTATIONS_URI + withSource(poll)).header(TENANT_IDS_HEADER, tenantA))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName(
        "given the logging flag off should answer an empty list to a caller of several tenants"
            + " outside the default tenant")
    void given_loggingFlagOff_should_answerAnEmptyListToACallerOfSeveralTenants() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA);
      setLoggingFlag(false);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(INJECTS_EXPECTATIONS_URI + withSource(LOGGING_POLL)))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName(
        "given the logging flag on should refuse with 400 a caller of several tenants outside"
            + " the default tenant")
    void given_loggingFlagOn_should_refuseACallerOfSeveralTenantsOutsideTheDefaultOne()
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(INJECTS_EXPECTATIONS_URI + withSource(LOGGING_POLL)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("given a caller of the default tenant only should return its expectations")
    void given_callerOfTheDefaultTenantOnly_should_returnItsExpectations() throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, SERVICE_ACCOUNT_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      SeededExpectations expectationsOfDefault = seedExpectations(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      List<String> ids = pollExpectationIds(Route.NO_HEADER, "");

      // Assert
      assertThat(ids)
          .contains(expectationsOfDefault.manual())
          .doesNotContain(expectationsOfB.manual());
    }

    @Test
    @WithMockUser
    @DisplayName(
        "given a caller of the default tenant and of B with no header should fall back to the"
            + " default tenant")
    void given_callerOfTheDefaultTenantAndOfB_should_fallBackToTheDefaultTenant() throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, SERVICE_ACCOUNT_CAPABILITIES);
      SeededExpectations expectationsOfDefault = seedExpectations(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      List<String> ids = pollExpectationIds(Route.NO_HEADER, "");

      // Assert
      assertThat(ids)
          .contains(expectationsOfDefault.manual())
          .doesNotContain(expectationsOfB.manual());
    }
  }

  @Nested
  @DisplayName("Endpoint of an agent")
  class EndpointOfAnAgent {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given an endpoint of tenant B should read it in tenant B")
    void given_endpointOfTenantB_should_readItInTenantB(Route route) throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(named(get(endpointUri(route, expectationsOfB.endpoint())), route))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("asset_id").isEqualTo(expectationsOfB.endpoint());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given an endpoint of tenant B should update it in tenant B")
    void given_endpointOfTenantB_should_updateItInTenantB(Route route) throws Exception {
      // Arrange
      String newName = "agent-routes-updated-" + UUID.randomUUID();
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(
                  named(put(endpointUri(route, expectationsOfB.endpoint())), route)
                      .content(asJsonString(endpointUpdate(newName)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).node("asset_id").isEqualTo(expectationsOfB.endpoint());
      assertThatJson(response).node("asset_name").isEqualTo(newName);
    }

    @Test
    @DisplayName("given an endpoint of the default tenant should not find it for a caller of B")
    void given_endpointOfTheDefaultTenant_should_notFindItForACallerOfB() throws Exception {
      // Arrange
      String endpointOfDefault = seedEndpoint(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(endpointUri(Route.NO_HEADER, endpointOfDefault)))
          .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "PUT"})
    @WithMockUser
    @DisplayName(
        "given a caller with rights in the default tenant only and member of another tenant"
            + " should read and update an endpoint of the default tenant with no header")
    void given_callerWithRightsInTheDefaultTenantOnly_should_readAndUpdateItsEndpoint(String method)
        throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, SERVICE_ACCOUNT_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      tenantHelper.attachCurrentUserToTenant(tenantA);
      String uri = endpointUri(Route.NO_HEADER, seedEndpoint(Tenant.DEFAULT_TENANT_UUID));
      MockHttpServletRequestBuilder request =
          "GET".equals(method)
              ? get(uri)
              : put(uri)
                  .content(
                      asJsonString(endpointUpdate("agent-routes-updated-" + UUID.randomUUID())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf());
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(request).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Agent installer")
  class AgentInstaller {

    static Stream<String> installerPaths() {
      return Stream.of(INSTALLER_TOKEN_PATH, INSTALLER_PATH);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given a caller of tenant B should return the installer token of tenant B")
    void given_callerOfTenantB_should_returnTheInstallerTokenOfTenantB(Route route)
        throws Exception {
      // Arrange
      String tokenOfB = serviceAccountToken(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = installer(route, INSTALLER_TOKEN_PATH);

      // Assert
      assertThat(response).isEqualTo(tokenOfB);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given a caller of tenant B should build the install command of tenant B")
    void given_callerOfTenantB_should_buildTheInstallCommandOfTenantB(Route route)
        throws Exception {
      // Arrange
      String tokenOfB = serviceAccountToken(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String command = installer(route, INSTALLER_PATH);

      // Assert
      assertThat(command).contains(tokenOfB);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("installerPaths")
    @DisplayName("given a caller of another tenant should never return the token of B")
    void given_callerOfAnotherTenant_should_neverReturnTheTokenOfB(String path) throws Exception {
      // Arrange
      String tokenOfB = serviceAccountToken(tenantB);
      String tenantC =
          tenantHelper
              .createTenantWithCapabilities(
                  "agent-routes-c-" + UUID.randomUUID(), SERVICE_ACCOUNT_CAPABILITIES)
              .getId();
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      String tokenOfC = serviceAccountToken(tenantC);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = installer(Route.NO_HEADER, path);

      // Assert
      assertThat(response).contains(tokenOfC).doesNotContain(tokenOfB);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("installerPaths")
    @DisplayName(
        "given a caller of several tenants outside the default tenant asking for plain text"
            + " should answer 400")
    void given_callerOfSeveralTenantsOutsideTheDefaultOne_should_answer400(String path)
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(AGENT_URI + path).accept(MediaType.TEXT_PLAIN))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("installerPaths")
    @DisplayName("given a header naming two tenants asking for plain text should answer 400")
    void given_headerNamingTwoTenants_should_answer400AsPlainText(String path) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(AGENT_URI + path)
                  .accept(MediaType.TEXT_PLAIN)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("installerPaths")
    @DisplayName(
        "given a header naming a tenant the caller is not a member of asking for plain text"
            + " should answer 403")
    void given_headerNamingANonMemberTenant_should_answer403AsPlainText(String path)
        throws Exception {
      // Arrange
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(AGENT_URI + path).accept(MediaType.TEXT_PLAIN).header(TENANT_IDS_HEADER, tenantA))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("installerPaths")
    @WithMockUser
    @DisplayName(
        "given a header naming a tenant where the caller lacks the right, asking for plain text,"
            + " should answer 403 with the code as plain text")
    void given_headerNamingATenantWithoutTheRight_should_answer403AsPlainText(String path)
        throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, SERVICE_ACCOUNT_CAPABILITIES);
      tenantHelper.attachCurrentUserToTenant(tenantA);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(AGENT_URI + path).accept(MediaType.TEXT_PLAIN).header(TENANT_IDS_HEADER, tenantA))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
          .andExpect(content().string(containsString("TENANT_SCOPE_FORBIDDEN")));
    }

    @Test
    @WithMockUser
    @DisplayName(
        "given a caller of the default tenant only should return the installer token of the"
            + " default tenant")
    void given_callerOfTheDefaultTenantOnly_should_returnTheInstallerTokenOfTheDefaultTenant()
        throws Exception {
      // Arrange
      tenantHelper.grantCapabilitiesInTenant(
          Tenant.DEFAULT_TENANT_UUID, SERVICE_ACCOUNT_CAPABILITIES);
      tenantHelper.detachCurrentUserFromTenant(tenantB);
      String tokenOfDefault = serviceAccountToken(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = installer(Route.NO_HEADER, INSTALLER_TOKEN_PATH);

      // Assert
      assertThat(response).isEqualTo(tokenOfDefault);
    }
  }

  // -- ROUTES --

  private MockHttpServletRequestBuilder named(MockHttpServletRequestBuilder request, Route route) {
    return route == Route.HEADER ? request.header(TENANT_IDS_HEADER, tenantB) : request;
  }

  private String withSource(String poll) {
    return poll.replace("{sourceId}", sourceId);
  }

  private String expectationsUri(Route route, String poll) {
    return (route == Route.PREFIXED
            ? TENANT_INJECTS_EXPECTATIONS_URI.replace("{tenantId}", tenantB)
            : INJECTS_EXPECTATIONS_URI)
        + withSource(poll);
  }

  private String endpointUri(Route route, String endpointId) {
    return (route == Route.PREFIXED
            ? TENANT_ENDPOINT_URI.replace("{tenantId}", tenantB)
            : ENDPOINT_URI)
        + "/"
        + endpointId;
  }

  private String installer(Route route, String path) throws Exception {
    String uri =
        (route == Route.PREFIXED ? TENANT_AGENT_URI.replace("{tenantId}", tenantB) : AGENT_URI)
            + path;
    return mvc.perform(named(get(uri), route).accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<String> pollExpectationIds(Route route, String poll) throws Exception {
    String response =
        mvc.perform(named(get(expectationsUri(route, poll)), route))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$[*].inject_expectation_id");
  }

  private void setLoggingFlag(boolean enabled) {
    openAEVConfig.setEnabledDevFeatures(
        enabled ? PreviewFeature.LOGGING_EXPECTATION.getValue() : "");
    Optional.ofNullable(cacheManager.getCache("global")).ifPresent(Cache::clear);
  }

  // -- ARRANGE (raw JDBC: the inspector never rewrites it) --

  /** The ids of the expectations of one tenant, one per shape a poll reads. */
  record SeededExpectations(
      String endpoint,
      String manual,
      String prevention,
      String detection,
      String agentlessDetection,
      String logging) {}

  enum ExpectedRow {
    MANUAL,
    PREVENTION,
    DETECTION,
    AGENTLESS_DETECTION,
    LOGGING;

    String of(SeededExpectations seeded) {
      return switch (this) {
        case MANUAL -> seeded.manual();
        case PREVENTION -> seeded.prevention();
        case DETECTION -> seeded.detection();
        case AGENTLESS_DETECTION -> seeded.agentlessDetection();
        case LOGGING -> seeded.logging();
      };
    }
  }

  private String insertTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  /**
   * The rows every poll reads in one tenant, under the source id the test shares between tenants:
   * an inject, an endpoint with an agent and an agentless one, and a collector of that id that
   * declares the logging expectations.
   */
  private SeededExpectations seedExpectations(String tenantId) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams,"
            + " inject_enabled, inject_depends_duration) VALUES (?, ?, ?, false, true, 0)",
        injectId,
        tenantId,
        "agent-routes-inject-" + injectId);
    String agentEndpoint = seedEndpoint(tenantId);
    String agentId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
            + " agent_executed_by_user, agent_status, agent_last_seen, agent_created_at,"
            + " agent_updated_at, tenant_id)"
            + " VALUES (?, ?, 'admin', 'service', 'root', 'ACTIVE', now(), now(), now(), ?)",
        agentId,
        agentEndpoint,
        tenantId);
    jdbc.update(
        "INSERT INTO collectors (collector_id, collector_name, collector_type, collector_period,"
            + " tenant_id, collector_supported_expectation_types)"
            + " VALUES (?, ?, 'agent-routes', 60, ?, ARRAY['LOGGING'])",
        sourceId,
        "agent-routes-collector-" + sourceId,
        tenantId);
    String agentlessEndpoint = seedEndpoint(tenantId);
    return new SeededExpectations(
        agentEndpoint,
        seedExpectation(injectId, "MANUAL", null, null),
        seedExpectation(injectId, "PREVENTION", agentId, null),
        seedExpectation(injectId, "DETECTION", agentId, null),
        seedExpectation(injectId, "DETECTION", null, agentlessEndpoint),
        seedExpectation(injectId, "LOGGING", null, agentlessEndpoint));
  }

  private String seedExpectation(String injectId, String type, String agentId, String assetId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id,"
            + " inject_expectation_type, inject_expiration_time, agent_id, asset_id,"
            + " inject_expectation_created_at, inject_expectation_updated_at)"
            + " VALUES (?, ?, ?, 3600, ?, ?, now(), now())",
        id,
        injectId,
        type,
        agentId,
        assetId);
    return id;
  }

  private String seedEndpoint(String tenantId) {
    String endpointId = UUID.randomUUID().toString();
    String name = "agent-routes-endpoint-" + endpointId;
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        endpointId,
        name,
        tenantId,
        name);
    return endpointId;
  }

  private static EndpointInput endpointUpdate(String name) {
    EndpointInput input = new EndpointInput();
    input.setName(name);
    input.setHostname(name);
    input.setIps(new String[] {"192.168.1.1"});
    input.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    return input;
  }

  /** Ensures the agent service account of the tenant exists and returns its token. */
  private String serviceAccountToken(String tenantId) {
    agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(
        TxCtx.forTenant(tenantId), tenantId);
    entityManager.flush();
    entityManager.clear();
    return jdbc.queryForObject(
        "SELECT t.token_value FROM tokens t JOIN users u ON u.user_id = t.token_user"
            + " WHERE lower(u.user_email) = lower(?) ORDER BY t.token_created_at LIMIT 1",
        String.class,
        ReservedServiceAccount.AGENT.emailPattern().formatted(tenantId));
  }

  private static void assertAmbientTenantIsTheDefaultOne() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
