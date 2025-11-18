package io.openaev.executors.sentinelone;

import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.service.SentinelOneGarbageCollectorService;
import io.openaev.service.AgentService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.sentinelone", name = "enable")
@RequiredArgsConstructor
@Service
public class SentinelOneGarbageCollector {

  private final SentinelOneExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final SentinelOneExecutorClient client;
  private final AgentService agentService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      SentinelOneGarbageCollectorService service =
          new SentinelOneGarbageCollectorService(this.config, this.client, this.agentService);
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofHours(6));
    }
  }
}
