package io.openbas.utils.fixtures;

import io.openbas.database.model.Agent;
import io.openbas.database.model.Asset;
import java.time.Instant;

public class AgentFixture {

  public static Agent createDefaultAgentService() {
    Agent agent = new Agent();
    agent.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    agent.setDeploymentMode(Agent.DEPLOYMENT_MODE.service);
    agent.setLastSeen(Instant.now());
    return agent;
  }

  public static Agent createDefaultAgentSession() {
    Agent agent = new Agent();
    agent.setExecutedByUser(Agent.ADMIN_SYSTEM_WINDOWS);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    agent.setDeploymentMode(Agent.DEPLOYMENT_MODE.session);
    agent.setLastSeen(Instant.now());
    return agent;
  }

  public static Agent createAgent(Asset asset, String externalReference) {
    Agent agent = createDefaultAgentService();
    agent.setAsset(asset);
    agent.setExternalReference(externalReference);
    return agent;
  }
}
