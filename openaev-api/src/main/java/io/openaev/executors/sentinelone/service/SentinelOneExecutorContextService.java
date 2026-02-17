package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOSAndArch;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;

@Slf4j
@RequiredArgsConstructor
public class SentinelOneExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = SENTINELONE_EXECUTOR_NAME;

  private static final String AGENT_ID_VARIABLE = "$agentID";

  private static final String WINDOWS_EXTERNAL_REFERENCE =
      "$agentID=& 'C:\\Program Files\\SentinelOne\\Sentinel Agent *\\SentinelCtl.exe' agent_id;";
  private static final String LINUX_EXTERNAL_REFERENCE =
      "agentID=$(sudo /opt/sentinelone/bin/sentinelctl management status | grep UUID | sed 's/UUID //g; s/ //g');";
  private static final String MAC_EXTERNAL_REFERENCE =
      "agentID=$(sudo /Library/Sentinel/sentinel-agent.bundle/Contents/MacOS/sentinelctl status | grep ID: | sed 's/ID: //g; s/ //g');";

  ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorClient client;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {
    // launchBatchExecutorSubprocess is used here for better performances
  }

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) {

    eeService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    if (!this.config.isEnable()) {
      throw new ExecutorException(
          "Fatal error: SentinelOne executor is not enabled", SENTINELONE_EXECUTOR_NAME);
    }
    List<Agent> sentinelOneAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    sentinelOneAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

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
                      inject.getId(),
                      arch.name()));
          case Linux ->
              actions.addAll(
                  getLinuxActions(
                      getAgentsFromOSAndArch(sentinelOneAgents, platform, arch),
                      injector,
                      inject.getId(),
                      arch.name()));
          case MacOS ->
              actions.addAll(
                  getMacOSActions(
                      getAgentsFromOSAndArch(sentinelOneAgents, platform, arch),
                      injector,
                      inject.getId(),
                      arch.name()));
          default -> { // No need, only Mac, Windows and Linux for now
          }
        }
      }
    }
    // Launch payloads with SentinelOne API
    executeActions(actions);
    return sentinelOneAgents;
  }

  public void executeActions(List<SentinelOneAction> actions) {
    int paginationLimit = this.config.getApiBatchExecutionActionPagination();
    for (SentinelOneAction action : actions) {
      int paginationCount = (int) Math.ceil(action.getAgents().size() / (double) paginationLimit);
      for (int batchIndex = 0; batchIndex < paginationCount; batchIndex++) {
        int fromIndex = (batchIndex * paginationLimit);
        int toIndex = Math.min(fromIndex + paginationLimit, action.getAgents().size());
        List<String> batchAgentIds =
            action.getAgents().subList(fromIndex, toIndex).stream().map(Agent::getId).toList();
        // Pagination of XXX agents (paginationLimit) per batch with 5s waiting
        // because each XXX actions will call the SentinelOne API to execute the implants
        // and each implant will call OpenAEV API to set traces
        scheduledExecutorService.schedule(
            () ->
                this.client.executeScript(
                    batchAgentIds, action.getScriptId(), action.getCommandEncoded()),
            batchIndex * 5L,
            TimeUnit.SECONDS);
      }
    }
  }

  private List<SentinelOneAction> getWindowsActions(
      List<Agent> agents, Injector injector, String injectId, String arch) {
    List<SentinelOneAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
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
      // The default command to download the openaev implant and execute the attack is modified for
      // Sentinel ONE
      // - WINDOWS_EXTERNAL_REFERENCE: the agent id in the openAEV DB for SentinelOne is the
      // SentinelOne agent id to
      // make the batch attack work so we get it with a command line from the endpoint and give it
      // to the implant
      command = WINDOWS_EXTERNAL_REFERENCE + command;
      command = replaceArgs(platform, command, injectId, AGENT_ID_VARIABLE);
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      actionWindows.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE)));
      actionWindows.setAgents(agents);
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<SentinelOneAction> getLinuxActions(
      List<Agent> agents, Injector injector, String injectId, String arch) {
    List<SentinelOneAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      SentinelOneAction actionLinux = new SentinelOneAction();
      actionLinux.setScriptId(this.config.getUnixScriptId());
      actionLinux.setCommandEncoded(
          getUnixCommand(
              Endpoint.PLATFORM_TYPE.Linux, injector, injectId, LINUX_EXTERNAL_REFERENCE, arch));
      actionLinux.setAgents(agents);
      actions.add(actionLinux);
    }
    return actions;
  }

  private List<SentinelOneAction> getMacOSActions(
      List<Agent> agents, Injector injector, String injectId, String arch) {
    List<SentinelOneAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      SentinelOneAction actionMac = new SentinelOneAction();
      actionMac.setScriptId(this.config.getUnixScriptId());
      actionMac.setCommandEncoded(
          getUnixCommand(
              Endpoint.PLATFORM_TYPE.MacOS, injector, injectId, MAC_EXTERNAL_REFERENCE, arch));
      actionMac.setAgents(agents);
      actions.add(actionMac);
    }
    return actions;
  }

  private String getUnixCommand(
      Endpoint.PLATFORM_TYPE platform,
      Injector injector,
      String injectId,
      String externalReferenceVariable,
      String arch) {
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    String executorCommandKey = platform.name() + "." + arch;
    String command = injector.getExecutorCommands().get(executorCommandKey);
    // The default command to download the openaev implant and execute the attack is modified for
    // SentinelOne
    // - externalReferenceVariable: the agent id in the openAEV DB for SentinelOne is the
    // SentinelOne agent id to make
    // the batch attack works so we get it with a command line from the endpoint and give it to the
    // implant
    command = externalReferenceVariable + command;
    command = replaceArgs(platform, command, injectId, AGENT_ID_VARIABLE);
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}
