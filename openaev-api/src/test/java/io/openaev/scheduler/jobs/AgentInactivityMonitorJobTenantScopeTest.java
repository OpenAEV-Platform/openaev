package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Each tenant's pass of {@link AgentInactivityMonitorJob} must only read that tenant's agents.
 *
 * <p>Regression (#8169): the stale-agent query ran unscoped. No {@code @Transactional} method is
 * entered on the job's path, so the v1 {@code tenantFilter} was never enabled and the query
 * returned every tenant's agents. With {@code assets} tenant-active, as in production, loading
 * another tenant's agent resolved an asset the pass cannot see: every tenant failed, and no agent
 * ever became inactive. Without that activation the same query silently updated other tenants'
 * agents from the wrong scope, which is why {@code assets} is activated here to match production.
 *
 * <p>Deliberately not {@code @Transactional}: {@code forEachTenant} opens one top-level transaction
 * per tenant and refuses to run inside an active one. Rows are committed, then removed in {@code
 * finally}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@DisplayName("AgentInactivityMonitorJob reads each tenant's agents within that tenant only")
class AgentInactivityMonitorJobTenantScopeTest extends IntegrationTest {

  @Autowired private AgentInactivityMonitorJob agentInactivityMonitorJob;
  @Autowired private AgentRepository agentRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("given stale agents in two tenants should mark each inactive within its own tenant")
  void given_staleAgentsInTwoTenants_should_markEachInactiveWithinItsOwnTenant() {
    String otherTenantId = null;
    String defaultTenantAgentId = null;
    try {
      // Arrange
      otherTenantId = seedTenant("agent-inactivity-" + UUID.randomUUID());
      defaultTenantAgentId =
          persistStaleAgent(Tenant.DEFAULT_TENANT_UUID, "default-tenant-stale-endpoint");
      String otherTenantAgentId = persistStaleAgent(otherTenantId, "other-tenant-stale-endpoint");

      // Act
      agentInactivityMonitorJob.execute(null);

      // Assert
      assertThat(statusOf(Tenant.DEFAULT_TENANT_UUID, defaultTenantAgentId))
          .isEqualTo(AgentStatus.INACTIVE);
      assertThat(statusOf(otherTenantId, otherTenantAgentId)).isEqualTo(AgentStatus.INACTIVE);
    } finally {
      if (defaultTenantAgentId != null) {
        deleteAgentAndEndpoint(Tenant.DEFAULT_TENANT_UUID, defaultTenantAgentId);
      }
      // The other tenant's endpoint and agent cascade with it.
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  /** Commits a stale ACTIVE agent on a new endpoint of {@code tenantId}; returns the agent id. */
  private String persistStaleAgent(String tenantId, String endpointName) {
    return tenantScopedJobRunner.supplyInTenant(
        tenantId,
        () -> {
          Endpoint endpoint = EndpointFixture.createEndpoint(endpointName);
          endpoint.setTenant(new Tenant(tenantId));
          endpointRepository.save(endpoint);

          Agent agent = AgentFixture.createDefaultAgentService();
          agent.setAsset(endpoint);
          agent.setTenant(endpoint.getTenant());
          agent.setStatus(AgentStatus.ACTIVE);
          agent.setLastSeen(Instant.now().minus(2, ChronoUnit.HOURS));
          return agentRepository.save(agent).getId();
        });
  }

  /** Read inside the owning tenant: with {@code assets} active an unscoped agent read is empty. */
  private AgentStatus statusOf(String tenantId, String agentId) {
    return tenantScopedJobRunner.supplyInTenant(
        tenantId, () -> agentRepository.findById(agentId).orElseThrow().getStatus());
  }

  private void deleteAgentAndEndpoint(String tenantId, String agentId) {
    tenantScopedJobRunner.runInTenant(
        tenantId,
        () ->
            agentRepository
                .findById(agentId)
                .ifPresent(
                    agent -> {
                      String endpointId = agent.getAsset().getId();
                      agentRepository.delete(agent);
                      endpointRepository.deleteById(endpointId);
                    }));
  }

  /** Seeds a bare tenant row: tenant onboarding is not under test and needs an acting user. */
  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    new JdbcTemplate(dataSource)
        .update(
            "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
                + " VALUES (?, ?, now(), now())",
            id,
            name);
    return id;
  }
}
