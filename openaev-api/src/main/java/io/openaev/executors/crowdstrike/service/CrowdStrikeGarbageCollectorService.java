package io.openaev.executors.crowdstrike.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_TYPE;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CrowdStrikeGarbageCollectorService implements Runnable {

  private final CrowdStrikeExecutorConfig config;
  private final CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;
  private final AgentService agentService;

  public CrowdStrikeGarbageCollectorService(
      CrowdStrikeExecutorConfig config,
      CrowdStrikeExecutorContextService crowdStrikeExecutorContextService,
      AgentService agentService) {
    this.config = config;
    this.crowdStrikeExecutorContextService = crowdStrikeExecutorContextService;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    List<Agent> agents = this.agentService.getAgentsByExecutorType(CROWDSTRIKE_EXECUTOR_TYPE);
    if (!agents.isEmpty()) {
      List<CrowdStrikeAction> actions = new ArrayList<>();
      log.info("Running CrowdStrike executor garbage collector on " + agents.size() + " agents");
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      if (!windowsAgents.isEmpty()) {
        CrowdStrikeAction action = new CrowdStrikeAction();
        action.setAgents(windowsAgents);
        action.setScriptName(this.config.getWindowsScriptName());
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
        CrowdStrikeAction action = new CrowdStrikeAction();
        action.setAgents(unixAgents);
        action.setScriptName(this.config.getUnixScriptName());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        actions.add(action);
      }
      crowdStrikeExecutorContextService.executeActions(actions);
    }
  }
}
