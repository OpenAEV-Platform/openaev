package io.openaev.rest.reporting.service;

import io.openaev.database.model.ReportingSchedulePeriod;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Firing-time semantics of {@link io.openaev.database.model.ReportingSchedule}, mirroring the
 * notification digest engine ({@code NotificationTriggerTimeUtils}, itself ported from OpenCTI's
 * {@code isTimeTrigger}).
 *
 * <p>All comparisons are made in UTC, truncated to the minute. {@code triggerTime} formats: DAY =
 * {@code "HH:mm"}, WEEK = {@code "<1-7>-HH:mm"} (ISO day of week), MONTH = {@code "<1-31>-HH:mm"}.
 * HOUR schedules fire on the hour and ignore {@code triggerTime}. MONTH days exceeding the current
 * month's length are clamped to its last day, so a schedule configured for the 31st still fires in
 * shorter months.
 */
public final class ReportingScheduleTimeUtils {

  private ReportingScheduleTimeUtils() {}

  /** Returns true when a schedule with the given period/time is due at {@code now}. */
  public static boolean isDue(ReportingSchedulePeriod period, String triggerTime, Instant now) {
    if (period == null) {
      return false;
    }
    ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    String hourMinute = String.format("%02d:%02d", utcNow.getHour(), utcNow.getMinute());
    return switch (period) {
      case HOUR -> utcNow.getMinute() == 0;
      case DAY -> hourMinute.equals(triggerTime);
      case WEEK -> (utcNow.getDayOfWeek().getValue() + "-" + hourMinute).equals(triggerTime);
      case MONTH -> isMonthDue(triggerTime, utcNow, hourMinute);
    };
  }

  private static boolean isMonthDue(String triggerTime, ZonedDateTime utcNow, String hourMinute) {
    if (triggerTime == null) {
      return false;
    }
    int dash = triggerTime.indexOf('-');
    if (dash <= 0) {
      return false;
    }
    int configuredDay;
    try {
      configuredDay = Integer.parseInt(triggerTime.substring(0, dash));
    } catch (NumberFormatException e) {
      return false;
    }
    // Clamp to the last day of the current month so days 29-31 fire in shorter months too
    int effectiveDay = Math.min(configuredDay, utcNow.toLocalDate().lengthOfMonth());
    return utcNow.getDayOfMonth() == effectiveDay
        && hourMinute.equals(triggerTime.substring(dash + 1));
  }

  /**
   * Returns the start of the period window ending at {@code to}. Calendar-aware for MONTH (one
   * calendar month back) so the window matches the calendar-based firing cadence instead of a fixed
   * 31 days. Used as the double-fire guard: a schedule whose lastRunAt falls strictly after this
   * boundary already ran for the current period.
   */
  public static Instant windowStart(ReportingSchedulePeriod period, Instant to) {
    return switch (period) {
      case HOUR -> to.minus(Duration.ofHours(1));
      case DAY -> to.minus(Duration.ofDays(1));
      case WEEK -> to.minus(Duration.ofDays(7));
      case MONTH -> to.atZone(ZoneOffset.UTC).minusMonths(1).toInstant();
    };
  }
}
