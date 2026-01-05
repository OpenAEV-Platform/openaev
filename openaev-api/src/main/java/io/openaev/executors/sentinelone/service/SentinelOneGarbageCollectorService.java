package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAction;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SentinelOneGarbageCollectorService implements Runnable {

  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorContextService sentinelOneExecutorContextService;
  private final AgentService agentService;

  public SentinelOneGarbageCollectorService(
      SentinelOneExecutorConfig config,
      SentinelOneExecutorContextService sentinelOneExecutorContextService,
      AgentService agentService) {
    this.config = config;
    this.sentinelOneExecutorContextService = sentinelOneExecutorContextService;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    List<Agent> agents = this.agentService.getAgentsByExecutorType(SENTINELONE_EXECUTOR_TYPE);
    if (!agents.isEmpty()) {
      List<SentinelOneAction> actions = new ArrayList<>();
      log.info("Running SentinelOne executor garbage collector on " + agents.size() + " agents");
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      if (!windowsAgents.isEmpty()) {
        SentinelOneAction action = new SentinelOneAction();
        action.setAgents(windowsAgents);
        action.setScriptId(this.config.getWindowsScriptId());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(
                    WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_16LE)));
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      if (!unixAgents.isEmpty()) {
        SentinelOneAction action = new SentinelOneAction();
        action.setAgents(unixAgents);
        action.setScriptId(this.config.getUnixScriptId());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        actions.add(action);
      }
      sentinelOneExecutorContextService.executeActions(actions);
    }
  }
}
