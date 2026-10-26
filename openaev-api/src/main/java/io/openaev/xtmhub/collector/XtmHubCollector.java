package io.openaev.xtmhub.collector;

import io.openaev.service.NewsFeedItemService;
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
  private final NewsFeedItemService newsFeedItemService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      XtmHubConnectivityCollectorService service =
          new XtmHubConnectivityCollectorService(this.xtmHubService);

      this.taskScheduler.scheduleAtFixedRate(
          service, Duration.ofSeconds(this.config.getConnectivityCheckInterval()));

      XtmHubNewsFeedCollectorService newsFeedService =
          new XtmHubNewsFeedCollectorService(this.xtmHubService, this.newsFeedItemService);

      int newsFeedInterval = this.config.getEffectiveNewsFeedInterval();
      if (!Objects.equals(this.config.getNewsFeedInterval(), newsFeedInterval)) {
        log.warn(
            "Invalid openaev.xtm.hub.collector.news-feed-interval {}, using {} seconds",
            this.config.getNewsFeedInterval(),
            XtmHubCollectorConfig.DEFAULT_NEWS_FEED_INTERVAL);
      }
      this.taskScheduler.scheduleAtFixedRate(newsFeedService, Duration.ofSeconds(newsFeedInterval));
    }
  }
}
