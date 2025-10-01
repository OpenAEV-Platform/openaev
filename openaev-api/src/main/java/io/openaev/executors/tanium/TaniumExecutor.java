package io.openaev.executors.tanium;

import io.openaev.executors.ExecutorService;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.service.TaniumExecutorService;
import io.openaev.service.EndpointService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TaniumExecutor {

  private final TaniumExecutorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final TaniumExecutorClient client;
  private final EndpointService endpointService;
  private final ExecutorService executorService;

  @PostConstruct
  public void init() {
    TaniumExecutorService service =
        new TaniumExecutorService(
            this.executorService, this.client, this.config, this.endpointService);
    if (this.config.isEnable()) {
      this.taskScheduler.scheduleAtFixedRate(service, Duration.ofSeconds(60));
    }
  }
}
