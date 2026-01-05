package io.openaev.executors.tanium.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.TaniumAction;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration;
import io.openaev.service.AgentService;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaniumGarbageCollectorService implements Runnable {

  private final TaniumExecutorConfig config;
  private final TaniumExecutorContextService taniumExecutorContextService;
  private final AgentService agentService;

  public TaniumGarbageCollectorService(
      TaniumExecutorConfig config,
      TaniumExecutorContextService taniumExecutorContextService,
      AgentService agentService) {
    this.config = config;
    this.taniumExecutorContextService = taniumExecutorContextService;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    List<io.openaev.database.model.Agent> agents =
        this.agentService.getAgentsByExecutorType(TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE);
    if (!agents.isEmpty()) {
      log.info("Running Tanium executor garbage collector on " + agents.size() + " agents");
      List<TaniumAction> actions = new ArrayList<>();
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      for (Agent agent : windowsAgents) {
        TaniumAction action = new TaniumAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getWindowsPackageId());
        action.setCommandEncoded(
            Base64.getEncoder().encodeToString(WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes()));
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      for (Agent agent : unixAgents) {
        TaniumAction action = new TaniumAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getUnixPackageId());
        action.setCommandEncoded(
            Base64.getEncoder().encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes()));
        actions.add(action);
      }
      taniumExecutorContextService.executeActions(actions);
    }
  }
}
