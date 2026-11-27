package io.openaev.executors.crowdstrike.service;

import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openaev.utils.InjectUtils.injectorOf;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.BatchExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.ImplantCommand;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service(CrowdStrikeExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class CrowdStrikeExecutorContextService
    extends BatchExecutorContextService<CrowdStrikeAction> {
  public static final String SERVICE_NAME = CROWDSTRIKE_EXECUTOR_NAME;
  private static final ImplantCommand IMPLANT_COMMAND =
      new ImplantCommand(ImplantCommand.ArchSource.ENDPOINT, null);

  private final CrowdStrikeExecutorConfig crowdStrikeExecutorConfig;
  private final CrowdStrikeExecutorClient crowdStrikeExecutorClient;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;
  private final OpenAEVConfig openAEVConfig;

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent,
      @NotNull final String token) {}

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus, String token) {

    enterpriseEditionService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    List<Agent> csAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    csAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    csAgents = executorService.manageWithoutPlatformAgents(csAgents, injectStatus);

    Injector injector = injectorOf(inject);

    List<CrowdStrikeAction> actions = new ArrayList<>();
    // Set implant script for Windows CS agents
    actions.addAll(
        getWindowsActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.Windows), injector, inject, token));
    // Set implant script for Linux CS agents
    actions.addAll(
        getLinuxActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.Linux), injector, inject, token));
    // Set implant script for MacOS CS agents
    actions.addAll(
        getMacOSActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.MacOS), injector, inject, token));
    // Launch payloads with CS API
    executeActions(actions);
    return csAgents;
  }

  // Batches of apiBatchExecutionActionPagination calls, one batch every 5 s: each action calls the
  // CrowdStrike API to execute the implant, and each implant calls the OpenAEV API to set traces.
  public void executeActions(List<CrowdStrikeAction> actions) {
    dispatch(actions);
  }

  @Override
  protected void send(CrowdStrikeAction action) {
    this.crowdStrikeExecutorClient.executeAction(
        action.getAgentExternalReference(), action.getScriptName(), action.getCommandEncoded());
  }

  @Override
  protected String agentReference(CrowdStrikeAction action) {
    return action.getAgentExternalReference();
  }

  @Override
  protected Integer batchSize() {
    return this.crowdStrikeExecutorConfig.getApiBatchExecutionActionPagination();
  }

  private List<CrowdStrikeAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      CrowdStrikeAction actionWindows = new CrowdStrikeAction();
      actionWindows.setScriptName(this.crowdStrikeExecutorConfig.getWindowsScriptName());
      String command =
          IMPLANT_COMMAND.build(
              openAEVConfig, injector, inject, agent, Endpoint.PLATFORM_TYPE.Windows, token);
      actionWindows.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE)));
      actionWindows.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<CrowdStrikeAction> getLinuxActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      CrowdStrikeAction actionLinux = new CrowdStrikeAction();
      actionLinux.setScriptName(this.crowdStrikeExecutorConfig.getUnixScriptName());
      actionLinux.setCommandEncoded(
          getUnixCommand(Endpoint.PLATFORM_TYPE.Linux, injector, inject, agent, token));
      actionLinux.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionLinux);
    }
    return actions;
  }

  private List<CrowdStrikeAction> getMacOSActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      CrowdStrikeAction actionMac = new CrowdStrikeAction();
      actionMac.setScriptName(this.crowdStrikeExecutorConfig.getUnixScriptName());
      actionMac.setCommandEncoded(
          getUnixCommand(Endpoint.PLATFORM_TYPE.MacOS, injector, inject, agent, token));
      actionMac.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionMac);
    }
    return actions;
  }

  private String getUnixCommand(
      Endpoint.PLATFORM_TYPE platform,
      Injector injector,
      Inject inject,
      Agent agent,
      String token) {
    String command = IMPLANT_COMMAND.build(openAEVConfig, injector, inject, agent, platform, token);
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}
