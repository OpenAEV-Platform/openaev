package io.openaev.utils.fixtures;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Tenant;
import java.time.Instant;

public class AgentFixture {

  /**
   * agents is a v2-active table, so the entity carries no listener to stamp the owning tenant: a
   * fixture that does not set it writes NULL and the NOT NULL constraint refuses the insert. The
   * ambient tenant is what the removed listener used, so a test that does not care about the tenant
   * keeps the row it had before. A test that does care sets its own afterwards.
   */
  private static Agent newAgent() {
    Agent agent = new Agent();
    agent.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    return agent;
  }

  public static Agent createDefaultAgentService() {
    Agent agent = newAgent();
    agent.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    agent.setDeploymentMode(Agent.DEPLOYMENT_MODE.service);
    agent.setLastSeen(Instant.now());
    return agent;
  }

  public static Agent createDefaultAgentSession() {
    Agent agent = newAgent();
    agent.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    agent.setDeploymentMode(Agent.DEPLOYMENT_MODE.session);
    agent.setLastSeen(Instant.now());
    return agent;
  }

  public static Agent createDefaultAgentSession(Executor executor) {
    Agent agent = createDefaultAgentSession();
    agent.setExecutor(executor);
    return agent;
  }

  public static Agent createAgent(Asset asset, String externalReference) {
    Agent agent = createDefaultAgentService();
    agent.setAsset(asset);
    agent.setExternalReference(externalReference);
    return agent;
  }

  public static Agent createInactiveAgent() {
    Agent agent = createDefaultAgentService();
    agent.setLastSeen(Instant.now().minusSeconds(3600 * 24 * 30));
    agent.setStatus(AgentStatus.INACTIVE);
    return agent;
  }
}
