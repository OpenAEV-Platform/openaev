package io.openaev.rest.asset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.repository.AgentRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code Agent.asset} is the sharpest association this activation touches: a {@code @ManyToOne}
 * with no {@code fetch} attribute, so it is EAGER, carrying {@code @NotNull}, from a table ({@code
 * agents}) that is NOT tenant-active onto one that is.
 *
 * <p>The association is {@code optional = true} by default, so Hibernate joins it with a LEFT JOIN.
 * An unscoped load therefore does not fail: the join finds nothing through {@code
 * can_access_tenant} and the agent comes back with a null asset, silently, while {@code @NotNull}
 * says that state is impossible. This pins both halves so a future change to either the fetch type
 * or the scope handling shows up as a test failure rather than as agents that quietly lose their
 * endpoint.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("the eager Agent.asset association follows the tenant scope")
class AgentAssetAssociationTenantScopeTest extends IntegrationTest {

  @Autowired private AgentRepository agentRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String agentId;

  @BeforeEach
  void seedAnAgentOnAnEndpoint() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("agent-assoc-" + UUID.randomUUID()).getId();
    String assetId = seedEndpoint(tenantA, "agent-assoc-endpoint");
    agentId = seedAgent(assetId, tenantA);
  }

  @Test
  @DisplayName("inside the owning tenant's scope the agent resolves its asset")
  void scopedLoadResolvesTheAsset() {
    // setScopeOnCurrentTransaction, not execute(): the class is @Transactional, so a transaction
    // is already open and the primitive refuses to nest one, on purpose. Joining the existing
    // transaction is what a scoped read looks like from inside a test.
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
    entityManager.clear();
    Agent agent = agentRepository.findById(agentId).orElseThrow();
    assertNotNull(
        agent.getAsset(), "an agent loaded inside its tenant's scope must carry its asset");
  }

  @Test
  @DisplayName("with no scope the agent disappears entirely, not just its asset")
  void unscopedLoadReturnsNoAgentAtAll() {
    // The first version of this test asserted the agent came back with a null asset. It does not:
    // it does not come back at all. Agent.asset is a mandatory eager @ManyToOne, so Hibernate joins
    // assets on the way in; with no scope the inspector makes that side return nothing and the
    // agent row goes with it.
    //
    // The v1 @Filter on Agent is not what does this: TenantContext holds the same value in
    // scopedLoadResolvesTheAsset above, and that one finds the agent. The difference is the v2
    // scope alone.
    //
    // This matters more than a null association would. A caller reading agents outside a scope does
    // not get degraded data it might notice, it gets an empty result that looks like "no agents
    // exist", which is the same answer a healthy platform gives a tenant that installed none.
    entityManager.clear();
    assertTrue(
        agentRepository.findById(agentId).isEmpty(),
        "an unscoped agent read is expected to return nothing at all; if the agent now comes back,"
            + " the association's fetch or optionality changed and every unscoped agent read must"
            + " be revisited");
  }

  private String seedEndpoint(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at,"
                + " asset_updated_at, tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
                + " VALUES (:id, :name, 'Endpoint', now(), now(), :tenantId, :name, 'Linux',"
                + " 'x86_64')")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedAgent(String assetId, String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
                + " agent_executed_by_user, agent_created_at, agent_updated_at, tenant_id)"
                + " VALUES (:id, :assetId, 'admin', 'service', 'root', now(), now(), :tenantId)")
        .setParameter("id", id)
        .setParameter("assetId", assetId)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
    entityManager.flush();
    return id;
  }
}
