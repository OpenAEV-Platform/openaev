package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOSAndArch;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static java.util.Optional.ofNullable;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.BatchExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service(SentinelOneExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class SentinelOneExecutorContextService
    extends BatchExecutorContextService<SentinelOneAction> {
  public static final String SERVICE_NAME = SENTINELONE_EXECUTOR_NAME;

  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorClient client;
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

    List<Agent> sentinelOneAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    sentinelOneAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    Optional<InjectorContract> injectorContract = inject.getInjectorContract();
    if (inject.getInjector() == null && injectorContract.isEmpty()) {
      throw new UnsupportedOperationException("Inject does not have a contract");
    }
    Injector injector =
        ofNullable(inject.getInjector()).orElse(injectorContract.get().getFirstInjector());

    sentinelOneAgents =
        executorService.manageWithoutPlatformAgents(sentinelOneAgents, injectStatus);

    List<SentinelOneAction> actions = new ArrayList<>();
    // Set implant script for each agent
    for (Endpoint.PLATFORM_TYPE platform : Endpoint.PLATFORM_TYPE.values()) {
      for (Endpoint.PLATFORM_ARCH arch : Endpoint.PLATFORM_ARCH.values()) {
        switch (platform) {
          case Windows ->
              actions.addAll(
                  getWindowsActions(
                      getAgentsFromOSAndArch(sentinelOneAgents, platform, arch),
                      injector,
                      inject,
                      arch.name(),
                      token));
          case Linux ->
              actions.addAll(
                  getLinuxActions(
                      getAgentsFromOSAndArch(sentinelOneAgents, platform, arch),
                      injector,
                      inject,
                      arch.name(),
                      token));
          case MacOS ->
              actions.addAll(
                  getMacOSActions(
                      getAgentsFromOSAndArch(sentinelOneAgents, platform, arch),
                      injector,
                      inject,
                      arch.name(),
                      token));
          default -> { // No need, only Mac, Windows and Linux for now
          }
        }
      }
    }
    // Launch payloads with SentinelOne API
    executeActions(actions);
    return sentinelOneAgents;
  }

  // Batches of apiBatchExecutionActionPagination calls, one batch every 5 s: each action calls the
  // SentinelOne API to execute the implant, and each implant calls the OpenAEV API to set traces.
  public void executeActions(List<SentinelOneAction> actions) {
    dispatch(actions);
  }

  @Override
  protected void send(SentinelOneAction action) {
    this.client.executeScript(
        action.getAgentExternalReference(), action.getScriptId(), action.getCommandEncoded());
  }

  @Override
  protected String agentReference(SentinelOneAction action) {
    return action.getAgentExternalReference();
  }

  @Override
  protected Integer batchSize() {
    return this.config.getApiBatchExecutionActionPagination();
  }

  private List<SentinelOneAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String arch, String token) {
    List<SentinelOneAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      SentinelOneAction actionWindows = new SentinelOneAction();
      actionWindows.setScriptId(this.config.getWindowsScriptId());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      Endpoint.PLATFORM_TYPE platform = Endpoint.PLATFORM_TYPE.Windows;
      String executorCommandKey = platform.name() + "." + arch;
      String command = injector.getExecutorCommands().get(executorCommandKey);
      command =
          replaceArgs(
              platform,
              command,
              inject.getId(),
              agent.getId(),
              inject.getTenant().getId(),
              token,
              openAEVConfig.getBaseUrlForAgent(),
              Integer.toString(openAEVConfig.getLogsMaxSize()),
              Boolean.toString(openAEVConfig.isUnsecuredCertificate()),
              Boolean.toString(openAEVConfig.isWithProxy()));
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      actionWindows.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE)));
      actionWindows.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<SentinelOneAction> getLinuxActions(
      List<Agent> agents, Injector injector, Inject inject, String arch, String token) {
    List<SentinelOneAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      SentinelOneAction actionLinux = new SentinelOneAction();
      actionLinux.setScriptId(this.config.getUnixScriptId());
      actionLinux.setCommandEncoded(
          getUnixCommand(Endpoint.PLATFORM_TYPE.Linux, injector, inject, agent, arch, token));
      actionLinux.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionLinux);
    }
    return actions;
  }

  private List<SentinelOneAction> getMacOSActions(
      List<Agent> agents, Injector injector, Inject inject, String arch, String token) {
    List<SentinelOneAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      SentinelOneAction actionMac = new SentinelOneAction();
      actionMac.setScriptId(this.config.getUnixScriptId());
      actionMac.setCommandEncoded(
          getUnixCommand(Endpoint.PLATFORM_TYPE.MacOS, injector, inject, agent, arch, token));
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
      String arch,
      String token) {
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    String executorCommandKey = platform.name() + "." + arch;
    String command = injector.getExecutorCommands().get(executorCommandKey);
    command =
        replaceArgs(
            platform,
            command,
            inject.getId(),
            agent.getId(),
            inject.getTenant().getId(),
            token,
            openAEVConfig.getBaseUrlForAgent(),
            Integer.toString(openAEVConfig.getLogsMaxSize()),
            Boolean.toString(openAEVConfig.isUnsecuredCertificate()),
            Boolean.toString(openAEVConfig.isWithProxy()));
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}
