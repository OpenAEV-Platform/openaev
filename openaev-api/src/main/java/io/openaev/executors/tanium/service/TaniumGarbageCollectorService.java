package io.openaev.executors.tanium.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.TaniumAction;
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
  private final Executor executor;
  private final TenantScopedTransaction tenantTx;

  public TaniumGarbageCollectorService(
      TaniumExecutorConfig config,
      TaniumExecutorContextService taniumExecutorContextService,
      AgentService agentService,
      Executor executor,
      TenantScopedTransaction tenantTx) {
    this.config = config;
    this.taniumExecutorContextService = taniumExecutorContextService;
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
    List<io.openaev.database.model.Agent> agents =
        this.agentService.getAgentsByExecutorId(executor.getId());
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
