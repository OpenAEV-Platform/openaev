package io.openaev.executors.crowdstrike.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
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
  private final Executor executor;
  private final TenantScopedTransaction tenantTx;

  public CrowdStrikeGarbageCollectorService(
      CrowdStrikeExecutorConfig config,
      CrowdStrikeExecutorContextService crowdStrikeExecutorContextService,
      AgentService agentService,
      Executor executor,
      TenantScopedTransaction tenantTx) {
    this.config = config;
    this.crowdStrikeExecutorContextService = crowdStrikeExecutorContextService;
    this.agentService = agentService;
    this.executor = executor;
    this.tenantTx = tenantTx;
  }

  @Override
  public void run() {
    tenantTx.execute(
        TxCtx.forTenant(executor.getTenantId()),
        () -> {
          doRun();
          return null;
        });
  }

  private void doRun() {
    List<Agent> agents = this.agentService.getAgentsByExecutorId(executor.getId());
    if (!agents.isEmpty()) {
      List<CrowdStrikeAction> actions = new ArrayList<>();
      log.info("Running CrowdStrike executor garbage collector on " + agents.size() + " agents");
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      for (Agent agent : windowsAgents) {
        CrowdStrikeAction action = new CrowdStrikeAction();
        action.setAgentExternalReference(agent.getExternalReference());
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
      for (Agent agent : unixAgents) {
        CrowdStrikeAction action = new CrowdStrikeAction();
        action.setAgentExternalReference(agent.getExternalReference());
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
