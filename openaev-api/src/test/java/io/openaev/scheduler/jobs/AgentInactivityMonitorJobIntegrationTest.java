package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The inactivity monitor is the only background writer on {@code agents}.
 *
 * <p>The default test profile activates no table, so this class used to exercise the job with the
 * statement inspector inert: {@code forEachTenant} opened its per-tenant scope, but no statement
 * was ever rewritten, so a read the rewriter refuses or a status write it narrows to zero rows
 * would have passed CI and only shown up once the table went live. {@code agents} is armed here,
 * and armed alone: anything filtered below can only come from the table under test.
 *
 * <p>Every read and write the test makes for itself runs inside {@code
 * tenantTx.execute(forTenant(...))}. The previous version used {@code agentRepository.findById}
 * inside a bare {@code TransactionTemplate}, which carries no scope at all: with the table armed,
 * {@code can_access_tenant} is fail-closed and each of those read-backs returns nothing. Status and
 * tenant are then asserted through JDBC, which the Hibernate statement inspector never sees, so the
 * assertion reads the stored column rather than trusting an entity. Timestamps are written through
 * JPA on purpose: seeding {@code agent_last_seen} with the database's own {@code now()} stores it
 * in the server's rendering while the application binds an {@code Instant}, and the two differ by
 * the server's offset, which is more than the one-hour staleness threshold this job compares
 * against.
 *
 * <p>Not transactional: {@code forEachTenant} refuses to run inside an active transaction, as it
 * does in production.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=agents")
@DisplayName("AgentInactivityMonitorJob with agents v2-active")
class AgentInactivityMonitorJobIntegrationTest extends IntegrationTest {

  private static final Duration STALE = Duration.ofHours(2);
  private static final Duration JUST_SEEN = Duration.ZERO;

  @Autowired private AgentInactivityMonitorJob agentInactivityMonitorJob;
  @Autowired private AgentRepository agentRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private JdbcTemplate jdbc;

  /** A second tenant, so "its own tenant" and "another tenant" are different rows. */
  private String tenantB;

  private final List<String> seededAgents = new ArrayList<>();
  private final List<String> seededEndpoints = new ArrayList<>();

  @BeforeEach
  void createSecondTenant() {
    tenantB = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES (?, ?)",
        tenantB,
        "agent-inactivity-b-" + tenantB);
  }

  @AfterEach
  void cleanup() {
    // The job sets the v1 thread-local per tenant and clears it; a failure mid-loop would leave it
    // behind for the next test in this class.
    TenantContext.clearCurrentTenant();
    seededAgents.forEach(id -> jdbc.update("DELETE FROM agents WHERE agent_id = ?", id));
    seededEndpoints.forEach(id -> jdbc.update("DELETE FROM assets WHERE asset_id = ?", id));
    seededAgents.clear();
    seededEndpoints.clear();
    if (tenantB != null) {
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
    }
  }

  @Nested
  @DisplayName("the job's own read and write, with the table armed")
  class TheJobItself {

    @Test
    @DisplayName("given a stale active agent should mark it inactive, active again, inactive again")
    void given_staleActiveAgent_should_transitionInactiveActiveInactive() {
      // Arrange
      String agentId = seedAgent(DEFAULT_TENANT_UUID, STALE);

      // Act 1: a stale active agent becomes inactive. The read and the saveAll both run under the
      // scope forEachTenant pins, so a refusal or a zero-row narrowing shows up as no transition.
      agentInactivityMonitorJob.execute(null);

      // Assert 1
      assertThat(statusOf(agentId))
          .as("the armed job must still mark a stale agent inactive")
          .isEqualTo(AgentStatus.INACTIVE.name());

      // Arrange 2: a heartbeat recovers the agent
      refresh(DEFAULT_TENANT_UUID, agentId, AgentStatus.ACTIVE, JUST_SEEN);

      // Act 2
      agentInactivityMonitorJob.execute(null);

      // Assert 2: a recent active agent is left alone
      assertThat(statusOf(agentId))
          .as("an agent seen just now must stay active")
          .isEqualTo(AgentStatus.ACTIVE.name());

      // Arrange 3: stale again
      refresh(DEFAULT_TENANT_UUID, agentId, AgentStatus.ACTIVE, STALE);

      // Act 3
      agentInactivityMonitorJob.execute(null);

      // Assert 3
      assertThat(statusOf(agentId))
          .as("the transition must be repeatable, not a one-off")
          .isEqualTo(AgentStatus.INACTIVE.name());
    }

    @Test
    @DisplayName("given stale agents in two tenants should mark each one inside its own tenant")
    void given_staleAgentsInTwoTenants_should_markEachInsideItsOwnTenant() {
      // Arrange: one stale active agent per tenant.
      String agentA = seedAgent(DEFAULT_TENANT_UUID, STALE);
      String agentB = seedAgent(tenantB, STALE);

      // Act: forEachTenant visits both tenants, one scope at a time.
      agentInactivityMonitorJob.execute(null);

      // Assert: both are marked, and neither row moved tenant. A status write stamped from a scope
      // left behind by the previous iteration of the loop would show up as the wrong tenant_id.
      assertThat(statusOf(agentA)).isEqualTo(AgentStatus.INACTIVE.name());
      assertThat(statusOf(agentB))
          .as("a non-default tenant's agent must be marked by its own pass of the loop")
          .isEqualTo(AgentStatus.INACTIVE.name());
      assertThat(tenantOf(agentA)).isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(tenantOf(agentB)).isEqualTo(tenantB);
    }
  }

  @Nested
  @DisplayName("cross-tenant isolation of the paths the job uses")
  class CrossTenantIsolation {

    @Test
    @DisplayName("given tenant A's scope should read A's agent and not the other tenant's")
    void given_tenantAScope_should_readOwnAgentAndNotTheOtherTenants() {
      // Arrange
      String agentA = seedAgent(DEFAULT_TENANT_UUID, STALE);
      String agentB = seedAgent(tenantB, STALE);

      // Act and Assert, positive first: findById carries no tenant predicate of its own, so with
      // the table armed the inspector is the only thing standing between the two tenants.
      assertThat(inScope(DEFAULT_TENANT_UUID, () -> agentRepository.findById(agentA)))
          .as("tenant A's own agent must be readable under tenant A's scope")
          .isPresent();
      assertThat(inScope(DEFAULT_TENANT_UUID, () -> agentRepository.findById(agentB)))
          .as("the other tenant's agent must not be readable under tenant A's scope")
          .isEmpty();
    }

    @Test
    @DisplayName("given tenant A's scope should not write the other tenant's agent")
    void given_tenantAScope_should_notWriteTheOtherTenantsAgent() {
      // Arrange: the other tenant's agent, loaded under its own scope so the entity is real.
      String agentB = seedAgent(tenantB, STALE);
      Agent agentOfB = inScope(tenantB, () -> agentRepository.findById(agentB)).orElseThrow();
      assertThat(agentOfB.getStatus())
          .as("the row under test must start active, otherwise the assertion below is vacuous")
          .isEqualTo(AgentStatus.ACTIVE);
      agentOfB.setStatus(AgentStatus.INACTIVE);

      // Act: the job's own write, but under a scope pinned to the other tenant. forEachTenant pins
      // exactly one tenant per iteration, so the job cannot reach this shape today; the assertion
      // is the net that catches a scope left behind by a previous iteration.
      try {
        inScopeDo(DEFAULT_TENANT_UUID, () -> agentRepository.save(agentOfB));
      } catch (RuntimeException refused) {
        // Armed, the row is invisible under this scope, so the write is refused rather than
        // applied. Which exception carries the refusal is Hibernate's business; the row below is
        // the assertion.
      }

      // Assert
      assertThat(statusOf(agentB))
          .as("a status write made under another tenant's scope must not reach this row")
          .isEqualTo(AgentStatus.ACTIVE.name());
    }
  }

  // -- ARRANGE HELPERS (JPA, under the tenant's own scope) --

  /** An active agent on its own endpoint, last seen the given time ago, in the given tenant. */
  private String seedAgent(String tenantId, Duration lastSeenAgo) {
    return inScope(
        tenantId,
        () -> {
          Endpoint endpoint =
              EndpointFixture.createEndpoint("agent-inactivity-" + UUID.randomUUID());
          endpoint.setTenant(new Tenant(tenantId));
          endpointRepository.save(endpoint);
          seededEndpoints.add(endpoint.getId());

          Agent agent = AgentFixture.createDefaultAgentService();
          agent.setAsset(endpoint);
          agent.setTenant(endpoint.getTenant());
          agent.setStatus(AgentStatus.ACTIVE);
          agent.setLastSeen(Instant.now().minus(lastSeenAgo));
          agentRepository.save(agent);
          seededAgents.add(agent.getId());
          return agent.getId();
        });
  }

  /** Re-reads the agent under its tenant's scope and writes a new status and heartbeat. */
  private void refresh(String tenantId, String agentId, AgentStatus status, Duration lastSeenAgo) {
    inScopeDo(
        tenantId,
        () -> {
          Agent agent = agentRepository.findById(agentId).orElseThrow();
          agent.setStatus(status);
          agent.setLastSeen(Instant.now().minus(lastSeenAgo));
          agentRepository.save(agent);
        });
  }

  // -- ASSERT HELPERS (raw JDBC: the Hibernate statement inspector never sees it) --

  private String statusOf(String agentId) {
    return jdbc.queryForObject(
        "SELECT agent_status FROM agents WHERE agent_id = ?", String.class, agentId);
  }

  private String tenantOf(String agentId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM agents WHERE agent_id = ?", String.class, agentId);
  }

  private <T> T inScope(String tenantId, Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  private void inScopeDo(String tenantId, Runnable work) {
    tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }
}
