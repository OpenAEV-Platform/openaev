package io.openaev.xtmhub.collector;

import io.openaev.xtmhub.XtmHubService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class XtmHubCollector {

  private final XtmHubCollectorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final XtmHubService xtmHubService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      XtmHubConnectivityCollectorService service =
          new XtmHubConnectivityCollectorService(this.xtmHubService);

      this.taskScheduler.scheduleAtFixedRate(
          service, Duration.ofSeconds(this.config.getConnectivityCheckInterval()));
    }
  }
}
