package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.POWERSHELL_CMD;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static io.openaev.utils.InjectUtils.injectorOf;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ArchSource;
import io.openaev.executors.BatchExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.ImplantCommand;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommand;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommandList;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service(PaloAltoCortexExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class PaloAltoCortexExecutorContextService
    extends BatchExecutorContextService<PaloAltoCortexAction> {
  public static final String SERVICE_NAME = PALOALTOCORTEX_EXECUTOR_NAME;
  private static final ImplantCommand IMPLANT_COMMAND =
      new ImplantCommand(ArchSource.ENDPOINT, PALOALTOCORTEX_EXECUTOR_NAME);

  private final PaloAltoCortexExecutorConfig config;
  private final PaloAltoCortexExecutorClient client;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;
  private final OpenAEVConfig openAEVConfig;

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent,
      @NotNull final String token) {
    // launchBatchExecutorSubprocess is used here for better performances
  }

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus, String token) {

    enterpriseEditionService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    List<Agent> paloAltoCortexAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    paloAltoCortexAgents.forEach(
        agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    paloAltoCortexAgents =
        executorService.manageWithoutPlatformAgents(paloAltoCortexAgents, injectStatus);

    Injector injector = injectorOf(inject);

    List<PaloAltoCortexAction> actions = new ArrayList<>();
    // Set implant script for each agent
    actions.addAll(
        getWindowsActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.Windows),
            injector,
            inject,
            token));
    actions.addAll(
        getUnixActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.Linux),
            injector,
            inject,
            Endpoint.PLATFORM_TYPE.Linux,
            token));
    actions.addAll(
        getUnixActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.MacOS),
            injector,
            inject,
            Endpoint.PLATFORM_TYPE.MacOS,
            token));
    // Launch payloads with Palo Alto Cortex API
    executeActions(actions);
    return paloAltoCortexAgents;
  }

  // Batches of apiBatchExecutionActionPagination calls, one batch every 5 s: each action calls the
  // Palo Alto Cortex API to execute the implant, and each implant calls the OpenAEV API to set
  // traces.
  public void executeActions(List<PaloAltoCortexAction> actions) {
    dispatch(actions);
  }

  @Override
  protected void send(PaloAltoCortexAction action) {
    this.client.executeScript(
        action.getAgentExternalReference(),
        action.getScriptId(),
        action.getCommandWindows() != null ? action.getCommandWindows() : action.getCommandUnix());
  }

  @Override
  protected String agentReference(PaloAltoCortexAction action) {
    return action.getAgentExternalReference();
  }

  @Override
  protected Integer batchSize() {
    return this.config.getApiBatchExecutionActionPagination();
  }

  private List<PaloAltoCortexAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<PaloAltoCortexAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      PaloAltoCortexAction actionWindows = new PaloAltoCortexAction();
      actionWindows.setScriptId(this.config.getWindowsScriptUid());
      String command =
          IMPLANT_COMMAND.build(
              openAEVConfig, injector, inject, agent, Endpoint.PLATFORM_TYPE.Windows, token);
      PaloAltoCortexCommandList commandWindows = new PaloAltoCortexCommandList();
      commandWindows.setCommands_list(
          List.of(
              POWERSHELL_CMD
                  + Base64.getEncoder()
                      .encodeToString(command.getBytes(StandardCharsets.UTF_16LE))));
      actionWindows.setCommandWindows(commandWindows);
      actionWindows.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<PaloAltoCortexAction> getUnixActions(
      List<Agent> agents,
      Injector injector,
      Inject inject,
      Endpoint.PLATFORM_TYPE platform,
      String token) {
    List<PaloAltoCortexAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      PaloAltoCortexAction actionUnix = new PaloAltoCortexAction();
      actionUnix.setScriptId(this.config.getUnixScriptUid());
      String command =
          IMPLANT_COMMAND.build(openAEVConfig, injector, inject, agent, platform, token);
      PaloAltoCortexCommand commandUnix = new PaloAltoCortexCommand();
      commandUnix.setCommand(Base64.getEncoder().encodeToString(command.getBytes()));
      actionUnix.setCommandUnix(commandUnix);
      actionUnix.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionUnix);
    }
    return actions;
  }
}
