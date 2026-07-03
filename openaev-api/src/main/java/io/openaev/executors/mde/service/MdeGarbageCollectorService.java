package io.openaev.executors.mde.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeAction;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MdeGarbageCollectorService implements Runnable {

  private final MdeExecutorConfig config;
  private final MdeExecutorContextService mdeExecutorContextService;
  private final AgentService agentService;
  private final String executorId;
  private final String tenantId;

  public MdeGarbageCollectorService(
      MdeExecutorConfig config,
      MdeExecutorContextService mdeExecutorContextService,
      AgentService agentService,
      String executorId,
      String tenantId) {
    this.config = config;
    this.mdeExecutorContextService = mdeExecutorContextService;
    this.agentService = agentService;
    this.executorId = executorId;
    this.tenantId = tenantId;
  }

  @Override
  public void run() {
    List<Agent> agents = agentService.getAgentsByExecutorIdAndTenantId(executorId, tenantId);
    if (!agents.isEmpty()) {
      List<MdeAction> actions = new ArrayList<>();
      log.info("Running MDE executor garbage collector on {} agents", agents.size());
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      if (!windowsAgents.isEmpty()) {
        MdeAction action = new MdeAction();
        action.setAgents(windowsAgents);
        action.setScriptName(config.getWindowsScriptName());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      if (!unixAgents.isEmpty()) {
        MdeAction action = new MdeAction();
        action.setAgents(unixAgents);
        action.setScriptName(config.getUnixScriptName());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        actions.add(action);
      }
      mdeExecutorContextService.executeActions(actions);
    }
  }
}
