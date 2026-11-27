package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ENDPOINT_URI;
import static io.openaev.rest.executor.ExecutorApi.AGENT_URI;
import static io.openaev.rest.expectation.ExpectationApi.INJECTS_EXPECTATIONS_URI;
import static io.openaev.rest.expectation.ExpectationApi.TENANT_INJECTS_EXPECTATIONS_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.service.EndpointService;
import io.openaev.service.account.AbstractPrivilegeService;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The agents, the install scripts and the collectors authenticate with the bearer token of a
 * service account the platform provisions per tenant: the agent account (installer) and the catalog
 * connector account (expectation polls, endpoints). They call the non-prefixed routes with no
 * {@code X-Tenant-Ids} header. No {@code @WithMockUser}: the token is the only authenticator. The
 * accounts and the rows are committed, as the platform holds them, and removed after each test. The
 * ambient tenant of the test thread is cleared before each request and falls back to the default
 * tenant.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Service accounts call the agent and collector routes with their bearer token")
class ServiceAccountRoutesTenantTest extends IntegrationTest {

  private static final String INSTALLER_PATH =
      "/installer/openaev/" + Endpoint.PLATFORM_TYPE.Linux.name() + "/" + EndpointService.SERVICE;
  private static final String INSTALLER_TOKEN_PATH = "/installer/openaev/token";
  private static final List<ReservedServiceAccount> PROVISIONED_ACCOUNTS =
      List.of(ReservedServiceAccount.AGENT, ReservedServiceAccount.CATALOG_CONNECTOR);
  private static final String MANUAL_POLL = "";
  private static final String PREVENTION_POLL = "/prevention";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;
  @Autowired private TenantScopedTransaction tenantTx;

  private String tenantB;
  private final List<String> seededInjects = new ArrayList<>();
  private final List<String> seededAssets = new ArrayList<>();
  private final List<ReservedServiceAccount> provisionedInDefault = new ArrayList<>();

  @BeforeEach
  void seedTenantB() {
    tenantB = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantB,
        "service-account-routes-b-" + tenantB);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    seededInjects.forEach(id -> jdbc.update("DELETE FROM injects WHERE inject_id = ?", id));
    seededAssets.forEach(id -> jdbc.update("DELETE FROM assets WHERE asset_id = ?", id));
    seededInjects.clear();
    seededAssets.clear();
    for (String tenantId : List.of(tenantB, Tenant.DEFAULT_TENANT_UUID)) {
      for (ReservedServiceAccount account : PROVISIONED_ACCOUNTS) {
        if (tenantId.equals(tenantB) || provisionedInDefault.contains(account)) {
          deleteAccount(account, tenantId);
        }
      }
    }
    provisionedInDefault.clear();
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
  }

  @Nested
  @DisplayName("Service accounts of the default tenant on the non-prefixed route")
  class OfTheDefaultTenant {

    @Test
    @DisplayName("given the agent account should return its own installer token")
    void given_agentAccount_should_returnItsOwnInstallerToken() throws Exception {
      // Arrange
      String token = agentToken(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response = installer(token, INSTALLER_TOKEN_PATH);

      // Assert
      assertThat(response).isEqualTo(token);
    }

    @Test
    @DisplayName("given the agent account should build the install command with its own token")
    void given_agentAccount_should_buildTheInstallCommandWithItsOwnToken() throws Exception {
      // Arrange
      String token = agentToken(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String command = installer(token, INSTALLER_PATH);

      // Assert
      assertThat(command).contains(token);
    }

    @ParameterizedTest(name = "poll {0}")
    @ValueSource(strings = {MANUAL_POLL, PREVENTION_POLL})
    @DisplayName(
        "given the collector account should return the expectations of the default tenant only")
    void given_collectorAccount_should_returnTheExpectationsOfTheDefaultTenantOnly(String poll)
        throws Exception {
      // Arrange
      String token = collectorToken(Tenant.DEFAULT_TENANT_UUID);
      Seeded ofDefault = seed(Tenant.DEFAULT_TENANT_UUID);
      Seeded ofB = seed(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(
                  get(INJECTS_EXPECTATIONS_URI + poll)
                      .header(HttpHeaders.AUTHORIZATION, bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].inject_expectation_id");
      String expected = MANUAL_POLL.equals(poll) ? ofDefault.manual() : ofDefault.prevention();
      String other = MANUAL_POLL.equals(poll) ? ofB.manual() : ofB.prevention();
      assertThat(ids).contains(expected).doesNotContain(other);
    }

    @Test
    @DisplayName("given the collector account should read an endpoint of the default tenant")
    void given_collectorAccount_should_readAnEndpointOfTheDefaultTenant() throws Exception {
      // Arrange
      String token = collectorToken(Tenant.DEFAULT_TENANT_UUID);
      Seeded ofDefault = seed(Tenant.DEFAULT_TENANT_UUID);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(ENDPOINT_URI + "/" + ofDefault.endpoint())
                  .header(HttpHeaders.AUTHORIZATION, bearer(token)))
          .andExpect(status().isOk());
    }
  }

  /**
   * A service account of a tenant other than the default one is refused with 403 on the
   * non-prefixed route, before its handler runs: the access check reads the caller's rights in the
   * ambient tenant, which is the default tenant off the prefixed route, and the account holds none
   * there. The prefixed route names its tenant and passes. These tests pin that refusal; the change
   * that makes the access check read the request scope turns them into 200.
   */
  @Nested
  @DisplayName("Service accounts of tenant B")
  class OfTenantB {

    @Test
    @DisplayName(
        "given the agent account of B on the non-prefixed route should be refused by the access"
            + " check of the ambient tenant")
    void given_agentAccountOfBOnTheNonPrefixedRoute_should_beRefusedByTheAmbientAccessCheck()
        throws Exception {
      // Arrange
      String token = agentToken(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(
              get(AGENT_URI + INSTALLER_TOKEN_PATH)
                  .accept(MediaType.TEXT_PLAIN)
                  .header(HttpHeaders.AUTHORIZATION, bearer(token)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
        "given the collector account of B on the non-prefixed route should be refused by the"
            + " access check of the ambient tenant")
    void given_collectorAccountOfBOnTheNonPrefixedRoute_should_beRefusedByTheAmbientAccessCheck()
        throws Exception {
      // Arrange
      String token = collectorToken(tenantB);
      Seeded ofB = seed(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act & Assert
      mvc.perform(get(INJECTS_EXPECTATIONS_URI).header(HttpHeaders.AUTHORIZATION, bearer(token)))
          .andExpect(status().isForbidden());
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(
              get(ENDPOINT_URI + "/" + ofB.endpoint())
                  .header(HttpHeaders.AUTHORIZATION, bearer(token)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given the collector account of B on the prefixed route should read the rows of B")
    void given_collectorAccountOfBOnThePrefixedRoute_should_readTheRowsOfB() throws Exception {
      // Arrange
      String token = collectorToken(tenantB);
      Seeded ofB = seed(tenantB);
      assertAmbientTenantIsTheDefaultOne();

      // Act
      String response =
          mvc.perform(
                  get(TENANT_INJECTS_EXPECTATIONS_URI.replace("{tenantId}", tenantB))
                      .header(HttpHeaders.AUTHORIZATION, bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertAmbientTenantIsTheDefaultOne();
      mvc.perform(
              get(TENANT_ENDPOINT_URI.replace("{tenantId}", tenantB) + "/" + ofB.endpoint())
                  .header(HttpHeaders.AUTHORIZATION, bearer(token)))
          .andExpect(status().isOk());

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].inject_expectation_id");
      assertThat(ids).contains(ofB.manual());
    }
  }

  // -- ROUTES --

  private String installer(String token, String path) throws Exception {
    return mvc.perform(
            get(AGENT_URI + path)
                .accept(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  // -- ARRANGE --

  private String agentToken(String tenantId) {
    provisioned(ReservedServiceAccount.AGENT, tenantId);
    TxCtx scope = TxCtx.forTenant(tenantId);
    tenantTx.execute(
        scope,
        () -> agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(scope, tenantId));
    return tenantTx.execute(
        scope,
        () -> agentServiceAccountPrivilegeService.getTokenUserServiceAccountByTenant(tenantId));
  }

  private String collectorToken(String tenantId) {
    provisioned(ReservedServiceAccount.CATALOG_CONNECTOR, tenantId);
    TxCtx scope = TxCtx.forTenant(tenantId);
    return tenantTx.execute(
        scope, () -> catalogConnectorPrivilegeService.getOrCreateToken(scope, tenantId));
  }

  /** Records an account of the default tenant to remove, unless it exists before the test. */
  private void provisioned(ReservedServiceAccount account, String tenantId) {
    if (Tenant.DEFAULT_TENANT_UUID.equals(tenantId)
        && jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE lower(user_email) = lower(?)",
                Integer.class,
                account.emailPattern().formatted(tenantId))
            == 0) {
      provisionedInDefault.add(account);
    }
  }

  private void deleteAccount(ReservedServiceAccount account, String tenantId) {
    jdbc.update(
        "DELETE FROM users WHERE lower(user_email) = lower(?)",
        account.emailPattern().formatted(tenantId));
    jdbc.update(
        "DELETE FROM groups WHERE group_id = ?",
        AbstractPrivilegeService.getUUIDFromName(account.access().groupId(), tenantId));
    jdbc.update(
        "DELETE FROM roles WHERE role_id = ?",
        AbstractPrivilegeService.getUUIDFromName(account.access().roleId(), tenantId));
  }

  /** The rows one tenant holds: an endpoint, a manual and a prevention expectation of an inject. */
  record Seeded(String endpoint, String manual, String prevention) {}

  private Seeded seed(String tenantId) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams,"
            + " inject_enabled, inject_depends_duration) VALUES (?, ?, ?, false, true, 0)",
        injectId,
        tenantId,
        "service-account-routes-inject-" + injectId);
    seededInjects.add(injectId);
    String endpointId = UUID.randomUUID().toString();
    seededAssets.add(endpointId);
    String name = "service-account-routes-endpoint-" + endpointId;
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        endpointId,
        name,
        tenantId,
        name);
    String agentId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
            + " agent_executed_by_user, agent_status, agent_last_seen, agent_created_at,"
            + " agent_updated_at, tenant_id)"
            + " VALUES (?, ?, 'admin', 'service', 'root', 'ACTIVE', now(), now(), now(), ?)",
        agentId,
        endpointId,
        tenantId);
    return new Seeded(
        endpointId,
        insertExpectation(injectId, "MANUAL", null),
        insertExpectation(injectId, "PREVENTION", agentId));
  }

  private String insertExpectation(String injectId, String type, String agentId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id,"
            + " inject_expectation_type, inject_expiration_time, agent_id,"
            + " inject_expectation_created_at, inject_expectation_updated_at)"
            + " VALUES (?, ?, ?, 3600, ?, now(), now())",
        id,
        injectId,
        type,
        agentId);
    return id;
  }

  private static void assertAmbientTenantIsTheDefaultOne() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
