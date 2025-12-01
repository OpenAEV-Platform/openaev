package io.openaev.executors.tanium.service;

import static io.openaev.database.model.Endpoint.PLATFORM_TYPE.Windows;
import static io.openaev.executors.ExecutorHelper.SLEEP_INTERVAL_BATCH_EXECUTIONS;
import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.tanium.service.TaniumExecutorService.TANIUM_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import jakarta.validation.constraints.NotNull;
import java.util.*;
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

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {}

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) throws InterruptedException {

    eeService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    if (!this.taniumExecutorConfig.isEnable()) {
      throw new RuntimeException("Fatal error: Tanium executor is not enabled");
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

    for (int callNumber = 0; callNumber < taniumAgents.size(); callNumber += 1) {
      int paginationLimit = this.taniumExecutorConfig.getApiBatchExecutionActionPagination();
      // Pagination with 1s wait because each action will call the Tanium API to execute the implant
      // and each implant will call OpenAEV API to set traces
      launchTaniumAction(taniumAgents.get(callNumber), inject, injector);
      if (callNumber + 1 % paginationLimit == 0) {
        Thread.sleep(SLEEP_INTERVAL_BATCH_EXECUTIONS);
      }
    }
    return taniumAgents;
  }

  private void launchTaniumAction(Agent agent, Inject inject, Injector injector) {
    Endpoint endpoint = (Endpoint) agent.getAsset();
    Endpoint.PLATFORM_TYPE platform = endpoint.getPlatform();
    Endpoint.PLATFORM_ARCH arch = endpoint.getArch();
    Integer packageId =
        platform.equals(Windows)
            ? this.taniumExecutorConfig.getWindowsPackageId()
            : this.taniumExecutorConfig.getUnixPackageId();
    String implantLocation =
        platform.equals(Windows)
            ? "$location="
                + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
                + ExecutorHelper.IMPLANT_BASE_NAME
                + UUID.randomUUID()
                + "\";md $location -ea 0;[Environment]::CurrentDirectory"
            : "location="
                + ExecutorHelper.IMPLANT_LOCATION_UNIX
                + ExecutorHelper.IMPLANT_BASE_NAME
                + UUID.randomUUID()
                + ";mkdir -p $location;filename=";

    String executorCommandKey = platform.name() + "." + arch.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    command = replaceArgs(platform, command, inject.getId(), agent.getId());
    command =
        platform.equals(Windows)
            ? command.replaceFirst(
                "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
                Matcher.quoteReplacement(implantLocation))
            : command.replaceFirst(
                "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));

    this.taniumExecutorClient.executeAction(
        agent.getExternalReference(),
        packageId,
        Base64.getEncoder().encodeToString(command.getBytes()));
  }
}
