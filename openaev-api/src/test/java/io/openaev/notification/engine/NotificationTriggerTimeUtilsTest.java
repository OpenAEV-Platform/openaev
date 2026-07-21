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
  void period_durations_cover_the_aggregation_window() {
    assertEquals(
        Duration.ofHours(1),
        NotificationTriggerTimeUtils.periodDuration(NotificationTriggerPeriod.HOUR));
    assertEquals(
        Duration.ofDays(1),
        NotificationTriggerTimeUtils.periodDuration(NotificationTriggerPeriod.DAY));
    assertEquals(
        Duration.ofDays(7),
        NotificationTriggerTimeUtils.periodDuration(NotificationTriggerPeriod.WEEK));
    assertEquals(
        Duration.ofDays(31),
        NotificationTriggerTimeUtils.periodDuration(NotificationTriggerPeriod.MONTH));
  }
}
