package io.openaev.executors.crowdstrike;

import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.openaev.service.AgentService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.crowdstrike", name = "enable")
@RequiredArgsConstructor
@Service
public class CrowdStrikeGarbageCollector {

  private final CrowdStrikeExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CrowdStrikeExecutorClient client;
  private final AgentService agentService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      CrowdStrikeGarbageCollectorService service =
          new CrowdStrikeGarbageCollectorService(this.config, this.client, this.agentService);
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofHours(6));
    }
  }
}
