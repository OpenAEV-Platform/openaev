package io.openaev.executors.tanium.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOSAndArch;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.TaniumAction;
import jakarta.validation.constraints.NotNull;
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
@Service(TaniumExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class TaniumExecutorContextService extends ExecutorContextService {

  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final TaniumExecutorConfig taniumExecutorConfig;
  private final TaniumExecutorClient taniumExecutorClient;
  private final ExecutorService executorService;
  public static final String SERVICE_NAME = TANIUM_EXECUTOR_NAME;

  ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {}

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) {

    eeService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    if (!this.taniumExecutorConfig.isEnable()) {
      throw new ExecutorException(
          "Fatal error: Tanium executor is not enabled", TANIUM_EXECUTOR_NAME);
    }

    List<Agent> taniumAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    taniumAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    taniumAgents = executorService.manageWithoutPlatformAgents(taniumAgents, injectStatus);

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
                      inject.getId(),
                      arch));
          case Linux, MacOS ->
              actions.addAll(
                  getUnixActions(
                      getAgentsFromOSAndArch(taniumAgents, platform, arch),
                      injector,
                      inject.getId(),
                      platform,
                      arch));
          default -> { // No need, only Mac, Windows and Linux for now
          }
        }
      }
    }
    // Launch payloads with Tanium API
    executeActions(actions);
    return taniumAgents;
  }

  public void executeActions(List<TaniumAction> actions) {
    int paginationLimit = this.taniumExecutorConfig.getApiBatchExecutionActionPagination();
    int paginationCount = (int) Math.ceil(actions.size() / (double) paginationLimit);

    for (int batchIndex = 0; batchIndex < paginationCount; batchIndex++) {
      int fromIndex = (batchIndex * paginationLimit);
      int toIndex = Math.min(fromIndex + paginationLimit, actions.size());
      List<TaniumAction> batchActions = actions.subList(fromIndex, toIndex);
      // Pagination of XXX calls (paginationLimit) per batch with 5s waiting
      // because each action will call the Tanium API to execute the implant
      // and each implant will call OpenAEV API to set traces
      scheduledExecutorService.schedule(
          () ->
              batchActions.forEach(
                  action ->
                      this.taniumExecutorClient.executeAction(
                          action.getAgentExternalReference(),
                          action.getScriptId(),
                          action.getCommandEncoded())),
          batchIndex * 5L,
          TimeUnit.SECONDS);
    }
  }

  private List<TaniumAction> getWindowsActions(
      List<Agent> agents, Injector injector, String injectId, Endpoint.PLATFORM_ARCH arch) {
    List<TaniumAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      TaniumAction actionUnix = new TaniumAction();
      actionUnix.setScriptId(this.taniumExecutorConfig.getWindowsPackageId());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      String executorCommandKey = Endpoint.PLATFORM_TYPE.Windows.name() + "." + arch.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      command = replaceArgs(Endpoint.PLATFORM_TYPE.Windows, command, injectId, agent.getId());
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      actionUnix.setCommandEncoded(Base64.getEncoder().encodeToString(command.getBytes()));
      actionUnix.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionUnix);
    }
    return actions;
  }

  private List<TaniumAction> getUnixActions(
      List<Agent> agents,
      Injector injector,
      String injectId,
      Endpoint.PLATFORM_TYPE platform,
      Endpoint.PLATFORM_ARCH arch) {
    List<TaniumAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      TaniumAction actionUnix = new TaniumAction();
      actionUnix.setScriptId(this.taniumExecutorConfig.getUnixPackageId());
      String implantLocation =
          "location="
              + ExecutorHelper.IMPLANT_LOCATION_UNIX
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + ";mkdir -p $location;filename=";
      String executorCommandKey = platform.name() + "." + arch.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      command = replaceArgs(platform, command, injectId, agent.getId());
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
      actionUnix.setCommandEncoded(Base64.getEncoder().encodeToString(command.getBytes()));
      actionUnix.setAgentExternalReference(agent.getExternalReference());
      actions.add(actionUnix);
    }
    return actions;
  }
}
