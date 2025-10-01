package io.openaev.executors.tanium;

import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.service.TaniumGarbageCollectorService;
import io.openaev.service.AgentService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "executor.tanium", name = "enable")
@RequiredArgsConstructor
@Service
public class TaniumGarbageCollector {

  private final TaniumExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final TaniumExecutorClient client;
  private final AgentService agentService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      TaniumGarbageCollectorService service =
          new TaniumGarbageCollectorService(this.config, this.client, this.agentService);
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofHours(6));
    }
  }
}
