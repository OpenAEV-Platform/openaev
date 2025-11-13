package io.openaev.executors.sentinelone;

import io.openaev.executors.ExecutorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SentinelOneExecutor {

  private final SentinelOneExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final SentinelOneExecutorClient client;
  private final EndpointService endpointService;
  private final ExecutorService executorService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  @PostConstruct
  public void init() {
    SentinelOneExecutorService service =
        new SentinelOneExecutorService(
            this.executorService,
            this.client,
            this.config,
            this.endpointService,
            this.agentService,
            this.assetGroupService);
    if (this.config.isEnable()) {
      // Get and create/update the SentinelOne asset groups, assets and agents each 20 minutes
      // (by default)
      this.taskScheduler.scheduleAtFixedRate(
          service, Duration.ofSeconds(this.config.getApiRegisterInterval()));
    }
  }
}
