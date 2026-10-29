package io.openaev.xtmhub.collector;

import io.openaev.xtmhub.XtmHubService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class XtmHubCollector {

  private final XtmHubCollectorConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final XtmHubService xtmHubService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      this.taskScheduler.scheduleAtFixedRate(
          this.xtmHubService::refreshConnectivityAllTenants,
          interval(
              "connectivity-check-interval",
              this.config.getConnectivityCheckInterval(),
              this.config.getEffectiveConnectivityCheckInterval()));

      // Fixed delay: a run lasts as long as the number of users and items, so the next one starts
      // an interval after the end of the previous one rather than catching up.
      this.taskScheduler.scheduleWithFixedDelay(
          this.xtmHubService::syncNewsFeed,
          interval(
              "news-feed-interval",
              this.config.getNewsFeedInterval(),
              this.config.getEffectiveNewsFeedInterval()));
    }
  }

  private Duration interval(String property, Integer configured, int effective) {
    if (!Objects.equals(configured, effective)) {
      log.warn(
          "Invalid openaev.xtm.hub.collector.{} {}, using {} seconds",
          property,
          configured,
          effective);
    }
    return Duration.ofSeconds(effective);
  }
}
