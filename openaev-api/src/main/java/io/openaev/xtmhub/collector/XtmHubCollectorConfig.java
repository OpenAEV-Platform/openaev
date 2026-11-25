package io.openaev.xtmhub.collector;

import jakarta.validation.constraints.NotBlank;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
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

  public static final int DEFAULT_NEWS_FEED_CLEANUP_VALUE = 180;
  public static final String DEFAULT_NEWS_FEED_CLEANUP_UNIT = "days";

  private static final Map<String, ChronoUnit> NEWS_FEED_CLEANUP_UNITS =
      Map.of(
          "seconds", ChronoUnit.SECONDS,
          "minutes", ChronoUnit.MINUTES,
          "hours", ChronoUnit.HOURS,
          "days", ChronoUnit.DAYS,
          "weeks", ChronoUnit.WEEKS,
          "months", ChronoUnit.MONTHS,
          "years", ChronoUnit.YEARS);

  // age after which a news feed item is cleaned up, with the unit of the value (seconds, minutes,
  // hours, days, weeks, months or years), default to 180 days. Read as text so that an invalid
  // value cannot fail the startup, it falls back to the default instead.
  @Value(
      "${openbas.xtm.hub.collector.news-feed-cleanup-interval-value:${openaev.xtm.hub.collector.news-feed-cleanup-interval-value:180}}")
  private String newsFeedCleanupIntervalValue;

  @Value(
      "${openbas.xtm.hub.collector.news-feed-cleanup-interval-unit:${openaev.xtm.hub.collector.news-feed-cleanup-interval-unit:days}}")
  private String newsFeedCleanupIntervalUnit;

  /** The connectivity check interval, or its default when configured with a non-positive value. */
  public int getEffectiveConnectivityCheckInterval() {
    return effective(connectivityCheckInterval);
  }

  /** The news feed interval, or its default when configured with a non-positive value. */
  public int getEffectiveNewsFeedInterval() {
    return effective(newsFeedInterval);
  }

  /**
   * Whether the news feed cleanup is configured with a positive integer value and a known unit.
   * Otherwise the effective value and unit are both the defaults.
   */
  public boolean isNewsFeedCleanupIntervalValid() {
    return parsedCleanupValue() != null && parsedCleanupUnit() != null;
  }

  public int getEffectiveNewsFeedCleanupIntervalValue() {
    return isNewsFeedCleanupIntervalValid()
        ? parsedCleanupValue()
        : DEFAULT_NEWS_FEED_CLEANUP_VALUE;
  }

  public ChronoUnit getEffectiveNewsFeedCleanupIntervalUnit() {
    return isNewsFeedCleanupIntervalValid()
        ? parsedCleanupUnit()
        : NEWS_FEED_CLEANUP_UNITS.get(DEFAULT_NEWS_FEED_CLEANUP_UNIT);
  }

  /**
   * The moment before which the news feed items are cleaned up: the age they are kept for before
   * the given moment, computed on the UTC calendar since an {@link Instant} has no month nor year.
   * A retention too long to be represented keeps everything: nothing is older than the epoch.
   */
  public Instant newsFeedCleanupCutoff(Instant now) {
    try {
      Instant cutoff =
          now.atZone(ZoneOffset.UTC)
              .minus(
                  getEffectiveNewsFeedCleanupIntervalValue(),
                  getEffectiveNewsFeedCleanupIntervalUnit())
              .toInstant();
      return cutoff.isBefore(Instant.EPOCH) ? Instant.EPOCH : cutoff;
    } catch (DateTimeException | ArithmeticException e) {
      return Instant.EPOCH;
    }
  }

  private Integer parsedCleanupValue() {
    if (newsFeedCleanupIntervalValue == null) {
      return null;
    }
    try {
      int value = Integer.parseInt(newsFeedCleanupIntervalValue.trim());
      return value > 0 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private ChronoUnit parsedCleanupUnit() {
    return newsFeedCleanupIntervalUnit == null
        ? null
        : NEWS_FEED_CLEANUP_UNITS.get(newsFeedCleanupIntervalUnit.trim().toLowerCase(Locale.ROOT));
  }

  private static int effective(Integer interval) {
    return interval != null && interval > 0 ? interval : DEFAULT_INTERVAL;
  }
}
