package io.openaev.executors.tanium.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.tanium.service.TaniumExecutorService.TANIUM_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.rest.exception.AgentException;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service(TaniumExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class TaniumExecutorContextService extends ExecutorContextService {

  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final TaniumExecutorConfig taniumExecutorConfig;
  private final TaniumExecutorClient taniumExecutorClient;
  public static final String SERVICE_NAME = TANIUM_EXECUTOR_NAME;

  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent)
      throws AgentException {

    InjectStatus status = inject.getStatus().orElseThrow();
    eeService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, status);

    if (!this.taniumExecutorConfig.isEnable()) {
      throw new AgentException("Fatal error: Tanium executor is not enabled", agent);
    }

    Endpoint.PLATFORM_TYPE platform = assetEndpoint.getPlatform();
    Endpoint.PLATFORM_ARCH arch = assetEndpoint.getArch();
    if (platform == null || arch == null) {
      throw new RuntimeException("Unsupported platform: " + platform + " (arch:" + arch + ")");
    }

    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    Integer packageId =
        switch (platform) {
          case Windows -> this.taniumExecutorConfig.getWindowsPackageId();
          case Linux, MacOS -> this.taniumExecutorConfig.getUnixPackageId();
          default -> throw new RuntimeException("Unsupported platform: " + platform);
        };

    String implantLocation =
        switch (platform) {
          case Windows ->
              "$location="
                  + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
                  + ExecutorHelper.IMPLANT_BASE_NAME
                  + UUID.randomUUID()
                  + "\";md $location -ea 0;[Environment]::CurrentDirectory";
          case Linux, MacOS ->
              "location="
                  + ExecutorHelper.IMPLANT_LOCATION_UNIX
                  + ExecutorHelper.IMPLANT_BASE_NAME
                  + UUID.randomUUID()
                  + ";mkdir -p $location;filename=";
          default -> throw new RuntimeException("Unsupported platform: " + platform);
        };

    String executorCommandKey = platform.name() + "." + arch.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    command = replaceArgs(platform, command, inject.getId(), agent.getId());
    command =
        switch (platform) {
          case Windows ->
              command.replaceFirst(
                  "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
                  Matcher.quoteReplacement(implantLocation));
          case Linux, MacOS ->
              command.replaceFirst(
                  "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
          default -> throw new RuntimeException("Unsupported platform: " + platform);
        };

    this.taniumExecutorClient.executeAction(
        agent.getExternalReference(),
        packageId,
        Base64.getEncoder().encodeToString(command.getBytes()));
  }

  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) {
    return new ArrayList<>();
  }
}
