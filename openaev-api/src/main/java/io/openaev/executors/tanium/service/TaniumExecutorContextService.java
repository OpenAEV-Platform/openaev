package io.openaev.executors.tanium.service;

import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOSAndArch;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.BatchExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.ImplantCommand;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.TaniumAction;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service(TaniumExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class TaniumExecutorContextService extends BatchExecutorContextService<TaniumAction> {

  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final TaniumExecutorConfig taniumExecutorConfig;
  private final TaniumExecutorClient taniumExecutorClient;
  private final ExecutorService executorService;
  private final OpenAEVConfig openAEVConfig;
  public static final String SERVICE_NAME = TANIUM_EXECUTOR_NAME;
  private static final ImplantCommand IMPLANT_COMMAND =
      new ImplantCommand(ImplantCommand.ArchSource.AGENT, null);

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

    List<Agent> taniumAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    taniumAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    taniumAgents = executorService.manageWithoutPlatformAgents(taniumAgents, injectStatus);

    Injector injector = ImplantCommand.injectorOf(inject);

    List<TaniumAction> actions = new ArrayList<>();
    // Set implant script for each agent
    for (Endpoint.PLATFORM_TYPE platform : Endpoint.PLATFORM_TYPE.values()) {
      for (Endpoint.PLATFORM_ARCH arch : Endpoint.PLATFORM_ARCH.values()) {
        switch (platform) {
          case Windows ->
              actions.addAll(
                  getWindowsActions(
                      getAgentsFromOSAndArch(taniumAgents, platform, arch),
                      injector,
                      inject,
                      token));
          case Linux, MacOS ->
              actions.addAll(
                  getUnixActions(
                      getAgentsFromOSAndArch(taniumAgents, platform, arch),
                      injector,
                      inject,
                      platform,
                      token));
          default -> { // No need, only Mac, Windows and Linux for now
          }
        }
      }
    }
    // Launch payloads with Tanium API
    executeActions(actions);
    return taniumAgents;
  }

  // Batches of apiBatchExecutionActionPagination calls, one batch every 5 s: each action calls the
  // Tanium API to execute the implant, and each implant calls the OpenAEV API to set traces.
  public void executeActions(List<TaniumAction> actions) {
    dispatch(actions);
  }

  @Override
  protected void send(TaniumAction action) {
    this.taniumExecutorClient.executeAction(
        action.getAgentExternalReference(), action.getScriptId(), action.getCommandEncoded());
  }

  @Override
  protected String agentReference(TaniumAction action) {
    return action.getAgentExternalReference();
  }

  @Override
  protected Integer batchSize() {
    return this.taniumExecutorConfig.getApiBatchExecutionActionPagination();
  }

  private List<TaniumAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<TaniumAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      TaniumAction actionWindows = new TaniumAction();
      actionWindows.setScriptId(this.taniumExecutorConfig.getWindowsPackageId());
      String command =
          IMPLANT_COMMAND.build(
              openAEVConfig, injector, inject, agent, Endpoint.PLATFORM_TYPE.Windows, token);
      actionWindows.setCommandEncoded(Base64.getEncoder().encodeToString(command.getBytes()));
      actionWindows.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<TaniumAction> getUnixActions(
      List<Agent> agents,
      Injector injector,
      Inject inject,
      Endpoint.PLATFORM_TYPE platform,
      String token) {
    List<TaniumAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      TaniumAction actionUnix = new TaniumAction();
      actionUnix.setScriptId(this.taniumExecutorConfig.getUnixPackageId());
      String command =
          IMPLANT_COMMAND.build(openAEVConfig, injector, inject, agent, platform, token);
      actionUnix.setCommandEncoded(Base64.getEncoder().encodeToString(command.getBytes()));
      actionUnix.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionUnix);
    }
    return actions;
  }
}
