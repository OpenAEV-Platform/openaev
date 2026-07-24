package io.openaev.notification.engine;

import io.openaev.database.model.NotificationTriggerPeriod;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Digest firing-time semantics, ported from OpenCTI's {@code isTimeTrigger}.
 *
 * <p>All comparisons are made in UTC, truncated to the minute. {@code triggerTime} formats: DAY =
 * {@code "HH:mm"}, WEEK = {@code "<1-7>-HH:mm"} (ISO day of week), MONTH = {@code "<1-31>-HH:mm"}.
 * HOUR digests fire on the hour and ignore {@code triggerTime}.
 */
public final class NotificationTriggerTimeUtils {

  private NotificationTriggerTimeUtils() {}

  /** Returns true when a digest with the given period/time is due at {@code now}. */
  public static boolean isTimeTrigger(
      NotificationTriggerPeriod period, String triggerTime, Instant now) {
    if (period == null) {
      return false;
    }
    ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    String hourMinute = String.format("%02d:%02d", utcNow.getHour(), utcNow.getMinute());
    return switch (period) {
      case HOUR -> utcNow.getMinute() == 0;
      case DAY -> hourMinute.equals(triggerTime);
      case WEEK -> (utcNow.getDayOfWeek().getValue() + "-" + hourMinute).equals(triggerTime);
      case MONTH -> (utcNow.getDayOfMonth() + "-" + hourMinute).equals(triggerTime);
    };
  }

  /** Returns the aggregation window duration of the given period. */
  public static Duration periodDuration(NotificationTriggerPeriod period) {
    return switch (period) {
      case HOUR -> Duration.ofHours(1);
      case DAY -> Duration.ofDays(1);
      case WEEK -> Duration.ofDays(7);
      case MONTH -> Duration.ofDays(31);
    };
  }
}
