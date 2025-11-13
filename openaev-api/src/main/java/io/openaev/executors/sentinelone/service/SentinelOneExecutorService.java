package io.openaev.executors.sentinelone.service;

import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.sentinelone", name = "enable")
@Slf4j
@Service
public class SentinelOneExecutorService implements Runnable {

  public static final String SENTINELONE_EXECUTOR_TYPE = "openaev_sentinelone";
  public static final String SENTINELONE_EXECUTOR_NAME = "SentinelOne";
  private static final String SENTINELONE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#sentinelone-agent";

  private static final String SENTINELONE_EXECUTOR_BACKGROUND_COLOR = "#6001FC";

  private final SentinelOneExecutorClient client;
  private final SentinelOneExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  private Executor executor = null;

  public static Endpoint.PLATFORM_TYPE toPlatform(@NotBlank final String platform) {
    return switch (platform.toLowerCase()) {
      case "linux" -> Endpoint.PLATFORM_TYPE.Linux;
      case "windows" -> Endpoint.PLATFORM_TYPE.Windows;
      case "macos" -> Endpoint.PLATFORM_TYPE.MacOS;
      default -> Endpoint.PLATFORM_TYPE.Unknown;
    };
  }

  public static Endpoint.PLATFORM_ARCH toArch(@NotBlank final String arch) {
    return switch (arch.toLowerCase()) {
      case "64 bit" -> Endpoint.PLATFORM_ARCH.x86_64;
      case "arm64" -> Endpoint.PLATFORM_ARCH.arm64;
      default -> Endpoint.PLATFORM_ARCH.Unknown;
    };
  }

  @Autowired
  public SentinelOneExecutorService(
      ExecutorService executorService,
      SentinelOneExecutorClient client,
      SentinelOneExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService) {
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    try {
      if (config.isEnable()) {
        this.executor =
            executorService.register(
                config.getId(),
                SENTINELONE_EXECUTOR_TYPE,
                SENTINELONE_EXECUTOR_NAME,
                SENTINELONE_EXECUTOR_DOCUMENTATION_LINK,
                SENTINELONE_EXECUTOR_BACKGROUND_COLOR,
                getClass().getResourceAsStream("/img/icon-sentinelone.png"),
                getClass().getResourceAsStream("/img/banner-sentinelone.png"),
                new String[] {
                  Endpoint.PLATFORM_TYPE.Windows.name(),
                  Endpoint.PLATFORM_TYPE.Linux.name(),
                  Endpoint.PLATFORM_TYPE.MacOS.name()
                });
      } else {
        if (executor != null) {
          executorService.remove(config.getId());
        }
      }
    } catch (Exception e) {
      log.error(String.format("Error creating SentinelOne executor: %s", e.getMessage()), e);
    }
  }

  @Override
  public void run() {}
}
