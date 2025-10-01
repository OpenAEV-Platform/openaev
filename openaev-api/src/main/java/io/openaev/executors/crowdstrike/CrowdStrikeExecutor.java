package io.openaev.executors.crowdstrike;

import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService;
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
public class CrowdStrikeExecutor {

  private final CrowdStrikeExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CrowdStrikeExecutorClient client;
  private final EndpointService endpointService;
  private final ExecutorService executorService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;

  @PostConstruct
  public void init() {
    CrowdStrikeExecutorService service =
        new CrowdStrikeExecutorService(
            this.executorService,
            this.client,
            this.config,
            this.endpointService,
            this.agentService,
            this.assetGroupService);
    if (this.config.isEnable()) {
      // Get and create/update the Crowdstrike asset groups, assets and agents each 20 minutes
      // (by default)
      this.taskScheduler.scheduleAtFixedRate(
          service, Duration.ofSeconds(this.config.getApiRegisterInterval()));
    }
  }
}
