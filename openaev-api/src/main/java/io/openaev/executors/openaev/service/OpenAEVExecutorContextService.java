package io.openaev.executors.openaev.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.openaev.OpenAEVExecutor.OPENAEV_EXECUTOR_NAME;

import io.openaev.database.model.*;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorContextService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service(OPENAEV_EXECUTOR_NAME)
@RequiredArgsConstructor
public class OpenAEVExecutorContextService extends ExecutorContextService {

  private final AssetAgentJobRepository assetAgentJobRepository;

  private String computeCommand(
      @NotNull final Inject inject,
      String agentId,
      Endpoint.PLATFORM_TYPE platform,
      Endpoint.PLATFORM_ARCH arch) {
    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    return switch (platform) {
      case Windows, Linux, MacOS -> {
        String executorCommandKey = platform.name() + "." + arch.name();
        String cmd = injector.getExecutorCommands().get(executorCommandKey);
        yield replaceArgs(platform, cmd, inject.getId(), agentId);
      }
      default -> throw new RuntimeException("Unsupported platform: " + platform);
    };
  }

  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {
    Endpoint.PLATFORM_TYPE platform = assetEndpoint.getPlatform();
    Endpoint.PLATFORM_ARCH arch = assetEndpoint.getArch();
    if (platform == null) {
      throw new RuntimeException("Unsupported null platform");
    }
    AssetAgentJob assetAgentJob = new AssetAgentJob();
    assetAgentJob.setCommand(computeCommand(inject, agent.getId(), platform, arch));
    assetAgentJob.setAgent(agent);
    assetAgentJob.setInject(inject);
    assetAgentJobRepository.save(assetAgentJob);
  }

  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) {
    return new ArrayList<>();
  }
}
