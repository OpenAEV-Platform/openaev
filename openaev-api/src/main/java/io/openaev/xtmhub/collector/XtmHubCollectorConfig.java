package io.openaev.xtmhub.collector;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class XtmHubCollectorConfig {

  @Value("${openbas.xtm.hub.collector.enable:${openaev.xtm.hub.collector.enable:false}}")
  private boolean enable;

  @NotBlank
  @Value("${openbas.xtm.hub.collector.id:${openaev.xtm.hub.collector.id:#{null}}}")
  private String id;

  public static final int DEFAULT_INTERVAL = 3600;

  // period between two connectivity checks, in seconds, default to 1 hour.
  @Value(
      "${openbas.xtm.hub.collector.connectivity-check-interval:${openaev.xtm.hub.collector.connectivity-check-interval:3600}}")
  private Integer connectivityCheckInterval;

  // period between two news feed syncs, in seconds, default to 1 hour.
  @Value(
      "${openbas.xtm.hub.collector.news-feed-interval:${openaev.xtm.hub.collector.news-feed-interval:3600}}")
  private Integer newsFeedInterval;

  /** The connectivity check interval, or its default when configured with a non-positive value. */
  public int getEffectiveConnectivityCheckInterval() {
    return effective(connectivityCheckInterval);
  }

  /** The news feed interval, or its default when configured with a non-positive value. */
  public int getEffectiveNewsFeedInterval() {
    return effective(newsFeedInterval);
  }

  private static int effective(Integer interval) {
    return interval != null && interval > 0 ? interval : DEFAULT_INTERVAL;
  }
}
