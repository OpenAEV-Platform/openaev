package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.NotificationTriggerPeriod;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Notification digest firing-time semantics")
class NotificationTriggerTimeUtilsTest {

  // 2026-07-20 is a Monday (ISO day of week 1)
  private static final Instant MONDAY_9AM = Instant.parse("2026-07-20T09:00:00Z");
  private static final Instant MONDAY_9_30AM = Instant.parse("2026-07-20T09:30:00Z");

  @Nested
  @DisplayName("HOUR period")
  class HourPeriod {

    @Test
    void fires_on_the_hour() {
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.HOUR, null, MONDAY_9AM));
    }

    @Test
    void does_not_fire_off_the_hour() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.HOUR, null, MONDAY_9_30AM));
    }
  }

  @Nested
  @DisplayName("DAY period")
  class DayPeriod {

    @Test
    void fires_at_configured_time() {
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.DAY, "09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_at_other_times() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.DAY, "09:00", MONDAY_9_30AM));
    }
  }

  @Nested
  @DisplayName("WEEK period")
  class WeekPeriod {

    @Test
    void fires_on_configured_day_and_time() {
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.WEEK, "1-09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_on_other_days() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.WEEK, "2-09:00", MONDAY_9AM));
    }
  }

  @Nested
  @DisplayName("MONTH period")
  class MonthPeriod {

    @Test
    void fires_on_configured_day_of_month_and_time() {
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "20-09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_on_other_days_of_month() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "21-09:00", MONDAY_9AM));
    }

    @Test
    void day_31_clamps_to_the_last_day_of_shorter_months() {
      // 2026-02-28 is the last day of February
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "31-09:00", Instant.parse("2026-02-28T09:00:00Z")));
    }

    @Test
    void clamped_day_does_not_fire_before_the_end_of_the_month() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "31-09:00", Instant.parse("2026-02-27T09:00:00Z")));
    }

    @Test
    void day_31_still_fires_on_the_31st_of_long_months() {
      assertTrue(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "31-09:00", Instant.parse("2026-07-31T09:00:00Z")));
    }

    @Test
    void malformed_trigger_time_never_fires() {
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, null, MONDAY_9AM));
      assertFalse(
          NotificationTriggerTimeUtils.isTimeTrigger(
              NotificationTriggerPeriod.MONTH, "garbage", MONDAY_9AM));
    }
  }

  @Test
  void null_period_never_fires() {
    assertFalse(NotificationTriggerTimeUtils.isTimeTrigger(null, "09:00", MONDAY_9AM));
  }

  @Test
  void seconds_are_truncated_before_comparison() {
    assertTrue(
        NotificationTriggerTimeUtils.isTimeTrigger(
            NotificationTriggerPeriod.DAY, "09:00", Instant.parse("2026-07-20T09:00:42Z")));
  }

  @Test
  void window_start_covers_the_aggregation_window() {
    Instant to = Instant.parse("2026-07-20T09:00:00Z");
    assertEquals(
        to.minus(Duration.ofHours(1)),
        NotificationTriggerTimeUtils.windowStart(NotificationTriggerPeriod.HOUR, to));
    assertEquals(
        to.minus(Duration.ofDays(1)),
        NotificationTriggerTimeUtils.windowStart(NotificationTriggerPeriod.DAY, to));
    assertEquals(
        to.minus(Duration.ofDays(7)),
        NotificationTriggerTimeUtils.windowStart(NotificationTriggerPeriod.WEEK, to));
    assertEquals(
        Instant.parse("2026-06-20T09:00:00Z"),
        NotificationTriggerTimeUtils.windowStart(NotificationTriggerPeriod.MONTH, to));
  }

  @Test
  void month_window_start_is_calendar_aware() {
    // One calendar month before Mar 31 clamps to Feb 28 (2026 is not a leap year)
    assertEquals(
        Instant.parse("2026-02-28T09:00:00Z"),
        NotificationTriggerTimeUtils.windowStart(
            NotificationTriggerPeriod.MONTH, Instant.parse("2026-03-31T09:00:00Z")));
  }
}
