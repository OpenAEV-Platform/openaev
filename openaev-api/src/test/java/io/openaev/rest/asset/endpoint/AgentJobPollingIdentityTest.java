package io.openaev.rest.asset.endpoint;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AgentFixture.createDefaultAgentService;
import static io.openaev.utils.fixtures.EndpointFixture.createWindowsEndpointRegisterInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.service.UserService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measures what a deployed agent actually gets from the two job-read endpoints, using the identity
 * production gives it: the per-tenant service account {@code ServiceAccountPrivilegeService}
 * provisions, whose token is embedded in the installer and in every executor command.
 *
 * <p>The isolation tests in {@code EndpointApiTest} all read with a caller the test attached to the
 * job's tenant by hand, so they prove the route contract but say nothing about the identity the
 * agent presents. This class uses the real service account and covers both routes, in the default
 * tenant (the single-tenant deployment) and in a tenant of its own.
 *
 * <p>The class-level mock user only exists to create the tenants: every request below is sent as
 * the service account, which {@link #asServiceAccountOf} puts in the security context.
 */
@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets,asset_agent_jobs")
@WithMockUser(isAdmin = true)
@DisplayName("Agent job polling with the per-tenant service account")
class AgentJobPollingIdentityTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private DataSource dataSource;

  private final List<String> committedTenantIds = new ArrayList<>();
  private final List<String> seededExternalReferences = new ArrayList<>();

  @AfterEach
  void cleanupCommittedRows() {
    // The arrange phase commits, so nothing rolls back: remove the seeded rows first (the agent and
    // its endpoint live in the default tenant in the single-tenant tests, which no tenant delete
    // would ever reach), then the tenants created for the multi-tenant ones.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    for (String reference : seededExternalReferences) {
      jdbc.update(
          "DELETE FROM asset_agent_jobs WHERE asset_agent_agent IN (SELECT agent_id FROM agents"
              + " WHERE agent_external_reference = ?)",
          reference);
      jdbc.update(
          "DELETE FROM assets WHERE asset_id IN (SELECT agent_asset FROM agents WHERE"
              + " agent_external_reference = ?)",
          reference);
      jdbc.update("DELETE FROM agents WHERE agent_external_reference = ?", reference);
    }
    seededExternalReferences.clear();
    if (!committedTenantIds.isEmpty()) {
      tenantHelper.deleteCommittedTenants(committedTenantIds.toArray(new String[0]));
      committedTenantIds.clear();
    }
  }

  /** A tenant created through the real onboarding flow, committed. */
  private String newTenant(String name) throws Exception {
    Tenant tenant = tenantHelper.createTenantWithCurrentUser(name);
    committedTenantIds.add(tenant.getId());
    return tenant.getId();
  }

  /** Seeds an endpoint, an agent carrying {@code externalReference} and one job in that tenant. */
  private String seedJob(String tenantId, String externalReference) {
    seededExternalReferences.add(externalReference);
    tenantHelper.switchToTenant(tenantId, entityManager);
    Endpoint endpoint = EndpointFixture.createEndpoint("Agent Poll Endpoint " + tenantId);
    Agent agent = createDefaultAgentService();
    agent.setExternalReference(externalReference);
    AgentComposer.Composer agentWrapper = agentComposer.forAgent(agent);
    endpointComposer.forEndpoint(endpoint).withAgent(agentWrapper).persist();

    AssetAgentJob job = new AssetAgentJob();
    job.setCommand("whoami");
    job.setAgent(agentWrapper.get());
    job.setTenant(new Tenant(tenantId));
    job.setCreatedAt(Instant.now());
    String jobId = assetAgentJobRepository.save(job).getId();

    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
    return jobId;
  }

  /**
   * Authenticates the rest of the test as the tenant's own service account.
   *
   * <p>The account is provisioned here through the same call production uses ({@code
   * V20260518_Service_Account}): the processor that drives data packs is {@code @Profile("!test")},
   * so tenant onboarding does not create it in this environment.
   */
  private User asServiceAccountOf(String tenantId) {
    String previousTenantId =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      serviceAccountPrivilegeService.ensurePrivilegedUserExists(tenantId);
    } finally {
      if (previousTenantId == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenantId);
      }
    }
    entityManager.flush();
    entityManager.clear();
    User serviceAccount =
        serviceAccountPrivilegeService
            .getUserServiceAccountByTenant(tenantId)
            .orElseThrow(() -> new AssertionError("no service account for tenant " + tenantId));
    SecurityContextHolder.getContext()
        .setAuthentication(UserService.buildAuthenticationToken(serviceAccount));
    return serviceAccount;
  }

  /** The job search, exactly as an agent sends it. Returns status and body. */
  private MvcResult pollJobs(String uri, String externalReference) throws Exception {
    EndpointRegisterInput input = createWindowsEndpointRegisterInput(List.of(), externalReference);
    MockHttpServletRequestBuilder request =
        post(uri)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf());
    return mvc.perform(request).andReturn();
  }

  private MvcResult pollJobsByReference(String uri) throws Exception {
    return mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON).with(csrf())).andReturn();
  }

  private int jobCount(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.length()");
  }

  @Test
  @DisplayName("the tenant's own service account reads its jobs on the tenant-prefixed route")
  void given_ownServiceAccount_should_readItsJobs_prefixedRoute() throws Exception {
    // -------- Arrange --------
    String tenantId = newTenant("Agent Poll A");
    String reference = "poll-prefixed-own-" + UUID.randomUUID();
    seedJob(tenantId, reference);
    asServiceAccountOf(tenantId);

    // -------- Act --------
    MvcResult result = pollJobs("/api/tenants/" + tenantId + "/endpoints/jobs", reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus())
        .as("an agent polling its own tenant's jobs must not be refused")
        .isEqualTo(200);
    assertThat(jobCount(result)).as("the agent must see the job waiting for it").isEqualTo(1);
  }

  /**
   * Pins what the non-prefixed route answers a service account of a tenant other than the default
   * one: 403, before any read happens. Capabilities are resolved from the groups of the ambient
   * tenant ({@code User#getCapabilities} filters on {@code TenantContext.getCurrentTenant()}, which
   * falls back to the default tenant), and this account's only group lives in its own tenant, so it
   * holds no capability there and the access-control aspect refuses the call. No active table is
   * involved, so this answer is the same with the tenant isolation of {@code asset_agent_jobs}
   * switched off.
   *
   * <p>No deployed agent reaches this: the agent polls {@code
   * /api/tenants/{tenant}/endpoints/jobs}, with the tenant id its installer wrote into its
   * configuration. The route is kept covered because the platform documents it as supported for API
   * clients.
   */
  @Test
  @DisplayName(
      "a service account of a non-default tenant is refused on the non-prefixed route, before any"
          + " read")
  void given_nonDefaultTenantServiceAccount_should_beRefused_nonPrefixedRoute() throws Exception {
    // -------- Arrange --------
    String tenantId = newTenant("Agent Poll B");
    String reference = "poll-plain-own-" + UUID.randomUUID();
    seedJob(tenantId, reference);
    asServiceAccountOf(tenantId);
    // Production: nothing binds the ambient tenant on this route, so the request starts with none.
    TenantContext.clearCurrentTenant();

    // -------- Act --------
    MvcResult result = pollJobs(ENDPOINT_URI + "/jobs", reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus())
        .as(
            "the capability check runs against the default tenant's groups, which this account has"
                + " none of")
        .isEqualTo(403);
  }

  @Test
  @DisplayName("the deprecated by-reference read works for the tenant's own service account")
  void given_ownServiceAccount_should_readItsJobs_deprecatedRoute() throws Exception {
    // -------- Arrange --------
    String tenantId = newTenant("Agent Poll C");
    String reference = "poll-byref-own-" + UUID.randomUUID();
    seedJob(tenantId, reference);
    asServiceAccountOf(tenantId);

    // -------- Act --------
    MvcResult result =
        pollJobsByReference("/api/tenants/" + tenantId + "/endpoints/jobs/" + reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(jobCount(result)).isEqualTo(1);
  }

  @Test
  @DisplayName("an agent of another tenant reads nothing for this tenant's agent reference")
  void given_anotherTenantsAgentReference_should_readNothing_prefixedRoute() throws Exception {
    // -------- Arrange --------
    String tenantId = newTenant("Agent Poll D");
    String otherTenantId = newTenant("Agent Poll E");
    String reference = "poll-prefixed-cross-" + UUID.randomUUID();
    seedJob(tenantId, reference);
    asServiceAccountOf(otherTenantId);

    // -------- Act --------
    MvcResult result = pollJobs("/api/tenants/" + otherTenantId + "/endpoints/jobs", reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus())
        .as("the caller is a member of the tenant it addresses, so the request itself is allowed")
        .isEqualTo(200);
    assertThat(jobCount(result))
        .as(
            "the read specification carries no tenant predicate and its join to agents is not"
                + " isolated, so the statement inspector on asset_agent_jobs is the only thing"
                + " keeping another tenant's job out of this response")
        .isZero();
  }

  @Test
  @DisplayName("the agent deletes the job it has run, on the prefixed route")
  void given_ownServiceAccount_should_deleteTheJobItHasRun_prefixedRoute() throws Exception {
    // -------- Arrange --------
    String tenantId = newTenant("Agent Poll F");
    String reference = "poll-delete-own-" + UUID.randomUUID();
    String jobId = seedJob(tenantId, reference);
    asServiceAccountOf(tenantId);

    // -------- Act --------
    MvcResult result =
        mvc.perform(
                delete("/api/tenants/" + tenantId + "/endpoints/jobs/" + jobId)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn();

    // -------- Assert --------
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    // Ground truth outside Hibernate: a scoped read of the table cannot tell a deleted row from one
    // the inspector hides.
    Integer remaining =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM asset_agent_jobs WHERE asset_agent_id = ?",
                Integer.class,
                jobId);
    assertThat(remaining)
        .as("a job the agent reports as run must actually be removed, or it runs again forever")
        .isZero();
  }

  @Test
  @DisplayName(
      "the default tenant's service account reads its jobs on the non-prefixed route, the"
          + " single-tenant shape")
  void given_defaultTenantServiceAccount_should_readItsJobs_nonPrefixedRoute() throws Exception {
    // -------- Arrange --------
    String reference = "poll-plain-default-" + UUID.randomUUID();
    seedJob(Tenant.DEFAULT_TENANT_UUID, reference);
    asServiceAccountOf(Tenant.DEFAULT_TENANT_UUID);
    // Production: nothing binds the ambient tenant on this route, so the request starts with none.
    TenantContext.clearCurrentTenant();

    // -------- Act --------
    MvcResult result = pollJobs(ENDPOINT_URI + "/jobs", reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus())
        .as("a single-tenant deployment's agent must not be refused")
        .isEqualTo(200);
    assertThat(jobCount(result)).as("the agent must see the job waiting for it").isEqualTo(1);
  }

  @Test
  @DisplayName("the default tenant's service account reads its jobs on the prefixed route")
  void given_defaultTenantServiceAccount_should_readItsJobs_prefixedRoute() throws Exception {
    // -------- Arrange --------
    String reference = "poll-prefixed-default-" + UUID.randomUUID();
    seedJob(Tenant.DEFAULT_TENANT_UUID, reference);
    asServiceAccountOf(Tenant.DEFAULT_TENANT_UUID);

    // -------- Act --------
    MvcResult result =
        pollJobs("/api/tenants/" + Tenant.DEFAULT_TENANT_UUID + "/endpoints/jobs", reference);

    // -------- Assert --------
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(jobCount(result)).isEqualTo(1);
  }
}
