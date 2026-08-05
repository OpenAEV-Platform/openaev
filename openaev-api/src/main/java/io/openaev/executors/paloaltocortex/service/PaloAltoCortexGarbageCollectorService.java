package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.*;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommand;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommandList;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaloAltoCortexGarbageCollectorService implements Runnable {

  private final PaloAltoCortexExecutorConfig config;
  private final PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;
  private final AgentService agentService;
  private final Executor executor;
  private final TenantScopedTransaction tenantTx;

  public PaloAltoCortexGarbageCollectorService(
      PaloAltoCortexExecutorConfig config,
      PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService,
      AgentService agentService,
      Executor executor,
      TenantScopedTransaction tenantTx) {
    this.config = config;
    this.paloAltoCortexExecutorContextService = paloAltoCortexExecutorContextService;
    this.agentService = agentService;
    this.executor = executor;
    this.tenantTx = tenantTx;
  }

  @Override
  public void run() {
    try {
      tenantTx.execute(
          TxCtx.forTenant(executor.getTenantId()),
          () -> {
            // Bridge for v1 tables (Tag, Asset, Agent, AssetGroup) still relying on
            // TenantContext via HibernateFilterTransactionAspect: this Runnable executes on the
            // shared scheduler thread pool outside any HTTP request, so TenantContext is never
            // set here otherwise and falls back to the default tenant, silently scoping the v1
            // Hibernate filter to the wrong tenant.
            TenantContext.setCurrentTenant(executor.getTenantId());
            doRun();
            return null;
          });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void doRun() {
    List<Agent> agents = this.agentService.getAgentsByExecutorId(executor.getId());
    if (!agents.isEmpty()) {
      log.info(
          "Running Palo Alto Cortex executor garbage collector on " + agents.size() + " agents");
      List<PaloAltoCortexAction> actions = new ArrayList<>();
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      for (Agent agent : windowsAgents) {
        PaloAltoCortexAction action = new PaloAltoCortexAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getWindowsScriptUid());
        PaloAltoCortexCommandList commandWindows = new PaloAltoCortexCommandList();
        commandWindows.setCommands_list(
            List.of(
                POWERSHELL_CMD
                    + Base64.getEncoder()
                        .encodeToString(
                            WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_16LE))));
        action.setCommandWindows(commandWindows);
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      for (Agent agent : unixAgents) {
        PaloAltoCortexAction action = new PaloAltoCortexAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getUnixScriptUid());
        PaloAltoCortexCommand commandUnix = new PaloAltoCortexCommand();
        commandUnix.setCommand(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        action.setCommandUnix(commandUnix);
        actions.add(action);
      }
      paloAltoCortexExecutorContextService.executeActions(actions);
    }
  }
}
