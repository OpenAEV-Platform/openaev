package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.*;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommand;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommandList;
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
@Service(PaloAltoCortexExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class PaloAltoCortexExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = PALOALTOCORTEX_EXECUTOR_NAME;

  private final PaloAltoCortexExecutorConfig config;
  private final PaloAltoCortexExecutorClient client;
  private final Ee enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;

  ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

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

    enterpriseEditionService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    List<Agent> paloAltoCortexAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    paloAltoCortexAgents.forEach(
        agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    paloAltoCortexAgents =
        executorService.manageWithoutPlatformAgents(paloAltoCortexAgents, injectStatus);

    List<PaloAltoCortexAction> actions = new ArrayList<>();
    // Set implant script for each agent
    actions.addAll(
        getWindowsActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.Windows),
            injector,
            inject.getId()));
    actions.addAll(
        getUnixActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.Linux),
            injector,
            inject.getId(),
            Endpoint.PLATFORM_TYPE.Linux));
    actions.addAll(
        getUnixActions(
            getAgentsFromOS(paloAltoCortexAgents, Endpoint.PLATFORM_TYPE.MacOS),
            injector,
            inject.getId(),
            Endpoint.PLATFORM_TYPE.MacOS));
    // Launch payloads with Palo Alto Cortex API
    executeActions(actions);
    return paloAltoCortexAgents;
  }

  public void executeActions(List<PaloAltoCortexAction> actions) {
    int paginationLimit = this.config.getApiBatchExecutionActionPagination();
    int paginationCount = (int) Math.ceil(actions.size() / (double) paginationLimit);

    for (int batchIndex = 0; batchIndex < paginationCount; batchIndex++) {
      int fromIndex = (batchIndex * paginationLimit);
      int toIndex = Math.min(fromIndex + paginationLimit, actions.size());
      List<PaloAltoCortexAction> batchActions = actions.subList(fromIndex, toIndex);
      // Pagination of XXX calls (paginationLimit) per batch with 5s waiting
      // because each action will call the Palo Alto Cortex API to execute the implant
      // and each implant will call OpenAEV API to set traces
      scheduledExecutorService.schedule(
          () ->
              batchActions.forEach(
                  action ->
                      this.client.executeScript(
                          action.getAgentExternalReference(),
                          action.getScriptId(),
                          action.getCommandWindows() != null
                              ? action.getCommandWindows()
                              : action.getCommandUnix())),
          batchIndex * 5L,
          TimeUnit.SECONDS);
    }
  }

  private List<PaloAltoCortexAction> getWindowsActions(
      List<Agent> agents, Injector injector, String injectId) {
    List<PaloAltoCortexAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      PaloAltoCortexAction actionWindows = new PaloAltoCortexAction();
      actionWindows.setScriptId(this.config.getWindowsScriptUid());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      // x86_64 by default in the register because CS API doesn't provide the platform architecture
      // (we update this when the download implant script is launched on the endpoint)
      String executorCommandKey =
          PALOALTOCORTEX_EXECUTOR_NAME
              + "."
              + Endpoint.PLATFORM_TYPE.Windows.name()
              + "."
              + Endpoint.PLATFORM_ARCH.x86_64.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      // The default command to download the openaev implant and execute the attack is modified for
      // Cortex
      // - WINDOWS_ARCH: Cortex doesn't know the endpoint architecture so we include it to get the
      // architecture before downloading the implant and we replace the default x86_64 put before
      command =
          WINDOWS_ARCH
              + command.replace(
                  Endpoint.PLATFORM_ARCH.x86_64.name(),
                  ARCH_VARIABLE
                      + "`"); // Specific for Windows to escape the ? right after in the URL
      command = replaceArgs(Endpoint.PLATFORM_TYPE.Windows, command, injectId, agent.getId());
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
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
      List<Agent> agents, Injector injector, String injectId, Endpoint.PLATFORM_TYPE platform) {
    List<PaloAltoCortexAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      PaloAltoCortexAction actionUnix = new PaloAltoCortexAction();
      actionUnix.setScriptId(this.config.getUnixScriptUid());
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
      // The default command to download the openaev implant and execute the attack is modified for
      // Cortex
      // - UNIX_ARCH: Cortex doesn't know the endpoint architecture so we include it to get the
      // architecture before downloading the implant and we replace the default x86_64 put before
      command = UNIX_ARCH + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE);
      command = replaceArgs(platform, command, injectId, agent.getId());
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
      PaloAltoCortexCommand commandUnix = new PaloAltoCortexCommand();
      commandUnix.setCommand(Base64.getEncoder().encodeToString(command.getBytes()));
      actionUnix.setCommandUnix(commandUnix);
      actionUnix.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionUnix);
    }
    return actions;
  }
}
