package io.openaev.executors.crowdstrike.service;

import static io.openaev.executors.ExecutorHelper.*;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
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
import org.springframework.stereotype.Service;

@Slf4j
@Service(CrowdStrikeExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class CrowdStrikeExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = CROWDSTRIKE_EXECUTOR_NAME;

  private final CrowdStrikeExecutorConfig crowdStrikeExecutorConfig;
  private final CrowdStrikeExecutorClient crowdStrikeExecutorClient;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;
  private final OpenAEVConfig openAEVConfig;

  ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

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

    Injector injector = inject.getInjector();
    if (injector == null) {
      // Fallback for legacy injects without inject_injector populated
      injector =
          inject
              .getInjectorContract()
              .map(InjectorContract::getFirstInjector)
              .orElseThrow(
                  () -> new UnsupportedOperationException("Inject does not have a contract"));
    }

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

  public void executeActions(List<CrowdStrikeAction> actions) {
    int paginationLimit = this.crowdStrikeExecutorConfig.getApiBatchExecutionActionPagination();
    int paginationCount = (int) Math.ceil(actions.size() / (double) paginationLimit);

    for (int batchIndex = 0; batchIndex < paginationCount; batchIndex++) {
      int fromIndex = (batchIndex * paginationLimit);
      int toIndex = Math.min(fromIndex + paginationLimit, actions.size());
      List<CrowdStrikeAction> batchActions = actions.subList(fromIndex, toIndex);
      // Pagination of XXX calls (paginationLimit) per batch with 5s waiting
      // because each action will call the CS API to execute the implant
      // and each implant will call OpenAEV API to set traces
      scheduledExecutorService.schedule(
          () ->
              batchActions.forEach(
                  action ->
                      this.crowdStrikeExecutorClient.executeAction(
                          action.getAgentExternalReference(),
                          action.getScriptName(),
                          action.getCommandEncoded())),
          batchIndex * 5L,
          TimeUnit.SECONDS);
    }
  }

  private List<CrowdStrikeAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      CrowdStrikeAction actionWindows = new CrowdStrikeAction();
      actionWindows.setScriptName(this.crowdStrikeExecutorConfig.getWindowsScriptName());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      Endpoint.PLATFORM_TYPE platform = Endpoint.PLATFORM_TYPE.Windows;
      // x86_64 by default in the register because CS API doesn't provide the platform architecture
      // (we update this when the download implant script is launched on the endpoint)
      String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      // The default command to download the openaev implant and execute the attack is modified for
      // CS
      // - WINDOWS_ARCH: CS doesn't know the endpoint architecture so we include it to get the
      // architecture before downloading the implant and we replace the default x86_64 put before
      command =
          WINDOWS_ARCH
              + command.replace(
                  Endpoint.PLATFORM_ARCH.x86_64.name(),
                  ARCH_VARIABLE
                      + "`"); // Specific for Windows to escape the ? right after in the URL
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
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    // x86_64 by default in the register because CS API doesn't provide the platform architecture
    // (we update this when the download implant script is launched on the endpoint)
    String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    // The default command to download the openaev implant and execute the attack is modified for CS
    // - UNIX_ARCH: CS doesn't know the endpoint architecture so we include it to get the
    // architecture before downloading the implant and we replace the default x86_64 put before
    command = UNIX_ARCH + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE);
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
