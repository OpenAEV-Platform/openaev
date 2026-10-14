package io.openaev.rest.asset.endpoint;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.autonomous.AutonomousRunApi;
import io.openaev.context.TenantContext;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointRegisterInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Isolation of {@code agents} on both routes, the tenant path and the {@code X-Tenant-Ids} header,
 * for reads and for writes.
 *
 * <p>The read probe is the autonomous capability report, whose {@code active_agent_count} comes
 * from {@code AgentRepository#countByStatus}: a plain count rooted on {@code agents} alone, with no
 * join to {@code assets}. That matters, because the obvious read (an endpoint's {@code
 * asset_agents} array) reaches the rows through an asset that is already v2-active, so it would
 * stay correct with {@code agents} disarmed and prove nothing. Only {@code executors} is armed
 * alongside {@code agents} here, because the registration path resolves the built-in OpenAEV
 * executor by id and that table carries a per-tenant row for it; {@code assets} is deliberately
 * left disarmed so any filtering observed below can only come from the table under test.
 *
 * <p>The write probe is the real agent registration endpoint. Its agent row is stamped from the
 * executor the request resolves, so the assertion reads {@code tenant_id} straight off the column
 * rather than trusting the entity.
 *
 * <p>Not transactional: each MockMvc request then runs in its own transaction and resolves its own
 * scope, which is what production does, and what makes the header-route cases meaningful. Rows are
 * seeded and verified through JDBC, which the statement inspector never rewrites, and removed in
 * {@link #cleanup()}.
 */
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.tenant.active-tables=agents,executors")
@DisplayName("agents isolation on both routes, reads and writes")
class AgentHttpIsolationTest extends IntegrationTest {

  private static final String CAPABILITIES_PATH = "/capabilities/resolve";
  private static final String PLAIN_CAPABILITIES =
      AutonomousRunApi.AUTONOMOUS_URI + CAPABILITIES_PATH;
  private static final String SCOPED_CAPABILITIES =
      TENANT_PREFIX + "/autonomous-runs" + CAPABILITIES_PATH;
  private static final String PLAIN_REGISTER = EndpointApi.ENDPOINT_URI + "/register";
  private static final String SCOPED_REGISTER = TENANT_PREFIX + "/endpoints/register";
  private static final String SCOPED_SEARCH = TENANT_PREFIX + "/endpoints/search";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;

  // The capability report is EE-gated; the mock's license checks default to "active".
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String agentA;
  private String agentB;

  @BeforeEach
  void seedTwoTenantsWithOneActiveAgentEach() throws Exception {
    // Arrange: two tenants the mock user belongs to, each with the built-in OpenAEV executor (the
    // registration path resolves it by id) and exactly one active agent on one of its endpoints.
    tenantA = tenantHelper.createTenantWithCurrentUser("agent-iso-a").getId();
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantA);
    tenantB = tenantHelper.createTenantWithCurrentUser("agent-iso-b").getId();
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantB);

    seedExecutor(tenantA);
    seedExecutor(tenantB);
    agentA = seedActiveAgent(tenantA);
    agentB = seedActiveAgent(tenantB);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    // A plain array, not List.of: if setup fails after creating only the first tenant, List.of
    // throws on the null second element before the loop runs, which masks the original failure and
    // leaks the tenant that was committed.
    for (String tenantId : new String[] {tenantA, tenantB}) {
      if (tenantId == null) {
        continue;
      }
      jdbc.update("DELETE FROM asset_agent_jobs WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM agents WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM executors WHERE tenant_id = ?", tenantId);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Nested
  @DisplayName("reads, on both routes")
  class Reads {

    @Test
    @DisplayName(
        "given tenant A's path, when counting active agents, then only A's agent is counted")
    void given_tenant_a_path_when_counting_active_agents_then_only_tenant_a_is_counted()
        throws Exception {
      // Act
      int count = activeAgentCountUnderPath(tenantA);

      // Assert: the positive case is the same number as the negative one here. One armed tenant
      // holds exactly one agent, so 1 says both "A's own agent was seen" and "B's was not"; an
      // unfiltered read returns at least 2.
      assertThat(count)
          .as("tenant A's path must count A's single agent and no other tenant's")
          .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given the X-Tenant-Ids header naming tenant B, when counting active agents, then only B's"
            + " agent is counted")
    void given_the_header_route_when_counting_active_agents_then_only_tenant_b_is_counted()
        throws Exception {
      // Arrange: production reaches the non-prefixed route with no ambient tenant. The fixtures
      // leave one behind (tenant onboarding sets it and never clears it), so clear it and pin that
      // before asserting anything, or the v1 thread-local could be doing the filtering.
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("the header route must run with no ambient tenant, as it does in production")
          .isFalse();

      // Act
      int count = activeAgentCountUnderHeader(tenantB);

      // Assert
      assertThat(count)
          .as("the header tenant must count its own single agent and no other tenant's")
          .isEqualTo(1);
    }

    @Test
    @DisplayName(
        "given tenant A's path, when searching endpoints, then the real SQL runs and only A's"
            + " agents are serialized")
    void given_tenant_a_path_when_searching_endpoints_then_only_tenant_a_agents_are_serialized()
        throws Exception {
      // Act: the endpoint search is where the rewritten SQL is hardest. The activity-status
      // formula correlates a sub-query on agents inside the asset projection and each endpoint's
      // agents eager-load as a sub-select, so a rewrite PostgreSQL refuses here is a 500 rather
      // than a wrong row, and the 200 is as much of the point as the payload. assets stays
      // disarmed, so both tenants' endpoints come back and only the agents are filtered.
      String response =
          mvc.perform(
                  post(SCOPED_SEARCH, tenantA)
                      .content("{}")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(response)
          .as("tenant A's own agent must be serialized on its endpoint")
          .contains(agentA);
      assertThat(response)
          .as("tenant B's agent must not appear in a search under tenant A's path")
          .doesNotContain(agentB);
    }
  }

  @Nested
  @DisplayName("write attribution, on both routes")
  class Writes {

    @Test
    @DisplayName("given tenant A's path, when an agent registers, then its row belongs to A")
    void given_tenant_a_path_when_an_agent_registers_then_the_row_belongs_to_tenant_a()
        throws Exception {
      // Arrange
      String externalReference = UUID.randomUUID().toString();

      // Act
      registerUnderPath(tenantA, externalReference);

      // Assert: read the stamped column back, not the entity.
      assertThat(tenantIdsHoldingAgent(externalReference))
          .as("an agent registered under tenant A's path must be stamped with tenant A alone")
          .containsExactly(tenantA);
    }

    @Test
    @DisplayName(
        "given the X-Tenant-Ids header naming tenant B, when an agent registers, then its row"
            + " belongs to B and not to the ambient tenant")
    void given_the_header_route_when_an_agent_registers_then_the_row_belongs_to_header_tenant()
        throws Exception {
      // Arrange
      String externalReference = UUID.randomUUID().toString();
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("the header route must run with no ambient tenant, as it does in production")
          .isFalse();

      // Act
      registerUnderHeader(tenantB, externalReference);

      // Assert
      assertThat(tenantIdsHoldingAgent(externalReference))
          .as(
              "an agent registered on the header route must be stamped with the header tenant alone")
          .containsExactly(tenantB);
    }
  }

  // -- ACT HELPERS --

  private int activeAgentCountUnderPath(String tenantId) throws Exception {
    return activeAgentCount(
        mvc.perform(
                post(SCOPED_CAPABILITIES, tenantId)
                    .content("{}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private int activeAgentCountUnderHeader(String tenantId) throws Exception {
    return activeAgentCount(
        mvc.perform(
                post(PLAIN_CAPABILITIES)
                    .header("X-Tenant-Ids", tenantId)
                    .content("{}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private int activeAgentCount(String response) throws Exception {
    JsonNode arsenal = mapper.readTree(response).get("arsenal");
    assertThat(arsenal).as("the capability report must carry its arsenal inventory").isNotNull();
    return arsenal.get("active_agent_count").asInt();
  }

  private void registerUnderPath(String tenantId, String externalReference) throws Exception {
    mvc.perform(
            post(SCOPED_REGISTER, tenantId)
                .content(mapper.writeValueAsString(registerInput(externalReference)))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private void registerUnderHeader(String tenantId, String externalReference) throws Exception {
    mvc.perform(
            post(PLAIN_REGISTER)
                .header("X-Tenant-Ids", tenantId)
                .content(mapper.writeValueAsString(registerInput(externalReference)))
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private EndpointRegisterInput registerInput(String externalReference) {
    EndpointRegisterInput input = EndpointRegisterInputFixture.getDefaultEndpointRegisterInput();
    input.setExternalReference(externalReference);
    input.setHostname("agent-iso-" + externalReference);
    input.setMacAddresses(new String[] {macAddressFor(externalReference)});
    return input;
  }

  /** A MAC unique to this registration, so it never merges into another test's endpoint. */
  private String macAddressFor(String externalReference) {
    String hex = externalReference.replace("-", "").substring(0, 12);
    StringBuilder mac = new StringBuilder();
    for (int i = 0; i < 12; i += 2) {
      if (i > 0) {
        mac.append(':');
      }
      mac.append(hex, i, i + 2);
    }
    return mac.toString();
  }

  // -- ASSERT HELPERS (raw JDBC: the inspector never rewrites it) --

  /** Every tenant_id stamped on an agent registered with this external reference. */
  private List<String> tenantIdsHoldingAgent(String externalReference) {
    return jdbc.queryForList(
        "SELECT tenant_id FROM agents WHERE agent_external_reference = ? ORDER BY tenant_id",
        String.class,
        externalReference);
  }

  // -- ARRANGE HELPERS --

  private void seedExecutor(String tenantId) {
    jdbc.update(
        "INSERT INTO executors (executor_id, tenant_id, executor_name, executor_type,"
            + " executor_external, executor_created_at, executor_updated_at)"
            + " VALUES (?, ?, 'OpenAEV Executor', 'openaev_node', false, now(), now())",
        OPENAEV_EXECUTOR_ID,
        tenantId);
  }

  private String seedActiveAgent(String tenantId) {
    String endpointId = UUID.randomUUID().toString();
    String name = "agent-iso-endpoint-" + endpointId;
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
    return agentId;
  }
}
