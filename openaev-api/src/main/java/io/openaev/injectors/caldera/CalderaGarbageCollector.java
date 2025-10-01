package io.openaev.injectors.caldera;

import io.openaev.injectors.caldera.client.CalderaInjectorClient;
import io.openaev.injectors.caldera.config.CalderaInjectorConfig;
import io.openaev.injectors.caldera.service.CalderaGarbageCollectorService;
import io.openaev.service.AgentService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "injector.caldera", name = "enable")
@RequiredArgsConstructor
@Service
public class CalderaGarbageCollector {

  private final CalderaInjectorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CalderaInjectorClient client;
  private final AgentService agentService;

  @PostConstruct
  public void init() {
    // If enabled, scheduled every X seconds
    if (this.config.isEnable()) {
      CalderaGarbageCollectorService service =
          new CalderaGarbageCollectorService(this.client, this.agentService);
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofSeconds(120));
    }
  }
}
