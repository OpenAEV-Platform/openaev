package io.openbas.executors.tanium;

import io.openbas.executors.ExecutorService;
import io.openbas.executors.tanium.client.TaniumExecutorClient;
import io.openbas.executors.tanium.config.TaniumExecutorConfig;
import io.openbas.executors.tanium.service.TaniumExecutorService;
import io.openbas.service.EndpointService;
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
