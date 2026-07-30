package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.ReportingSchedulePeriod;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Reporting schedule firing-time semantics")
class ReportingScheduleTimeUtilsTest {

  // 2026-07-20 is a Monday (ISO day of week 1)
  private static final Instant MONDAY_9AM = Instant.parse("2026-07-20T09:00:00Z");
  private static final Instant MONDAY_9_30AM = Instant.parse("2026-07-20T09:30:00Z");

  @Nested
  @DisplayName("HOUR period")
  class HourPeriod {

    @Test
    void fires_on_the_hour() {
      assertTrue(ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.HOUR, null, MONDAY_9AM));
    }

    @Test
    void does_not_fire_off_the_hour() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.HOUR, null, MONDAY_9_30AM));
    }

    @Test
    void ignores_the_trigger_time() {
      assertTrue(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.HOUR, "garbage", MONDAY_9AM));
    }
  }

  @Nested
  @DisplayName("DAY period")
  class DayPeriod {

    @Test
    void fires_at_configured_time() {
      assertTrue(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.DAY, "09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_at_other_times() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.DAY, "09:00", MONDAY_9_30AM));
    }

    @Test
    void malformed_trigger_time_never_fires() {
      assertFalse(ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.DAY, null, MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.DAY, "garbage", MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.DAY, "9:00", MONDAY_9AM));
    }
  }

  @Nested
  @DisplayName("WEEK period")
  class WeekPeriod {

    @Test
    void fires_on_configured_iso_day_and_time() {
      assertTrue(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.WEEK, "1-09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_on_other_days() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.WEEK, "2-09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_at_other_times() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.WEEK, "1-09:00", MONDAY_9_30AM));
    }

    @Test
    void malformed_trigger_time_never_fires() {
      assertFalse(ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.WEEK, null, MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.WEEK, "garbage", MONDAY_9AM));
    }
  }

  @Nested
  @DisplayName("MONTH period")
  class MonthPeriod {

    @Test
    void fires_on_configured_day_of_month_and_time() {
      assertTrue(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, "20-09:00", MONDAY_9AM));
    }

    @Test
    void does_not_fire_on_other_days_of_month() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, "21-09:00", MONDAY_9AM));
    }

    @Test
    void day_31_clamps_to_the_last_day_of_shorter_months() {
      // 2026-02-28 is the last day of February (2026 is not a leap year)
      assertTrue(
          ReportingScheduleTimeUtils.isDue(
              ReportingSchedulePeriod.MONTH, "31-09:00", Instant.parse("2026-02-28T09:00:00Z")));
    }

    @Test
    void clamped_day_does_not_fire_before_the_end_of_the_month() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(
              ReportingSchedulePeriod.MONTH, "31-09:00", Instant.parse("2026-02-27T09:00:00Z")));
    }

    @Test
    void day_31_still_fires_on_the_31st_of_long_months() {
      assertTrue(
          ReportingScheduleTimeUtils.isDue(
              ReportingSchedulePeriod.MONTH, "31-09:00", Instant.parse("2026-07-31T09:00:00Z")));
    }

    @Test
    void malformed_trigger_time_never_fires() {
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, null, MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, "garbage", MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, "-09:00", MONDAY_9AM));
      assertFalse(
          ReportingScheduleTimeUtils.isDue(ReportingSchedulePeriod.MONTH, "xx-09:00", MONDAY_9AM));
    }
  }

  @Test
  void null_period_never_fires() {
    assertFalse(ReportingScheduleTimeUtils.isDue(null, "09:00", MONDAY_9AM));
  }

  @Test
  void seconds_are_truncated_before_comparison() {
    assertTrue(
        ReportingScheduleTimeUtils.isDue(
            ReportingSchedulePeriod.DAY, "09:00", Instant.parse("2026-07-20T09:00:42Z")));
  }

  // -- DOUBLE-FIRE WINDOW GUARD --

  @Test
  void window_start_covers_one_period_back() {
    Instant to = Instant.parse("2026-07-20T09:00:00Z");
    assertEquals(
        to.minus(Duration.ofHours(1)),
        ReportingScheduleTimeUtils.windowStart(ReportingSchedulePeriod.HOUR, to));
    assertEquals(
        to.minus(Duration.ofDays(1)),
        ReportingScheduleTimeUtils.windowStart(ReportingSchedulePeriod.DAY, to));
    assertEquals(
        to.minus(Duration.ofDays(7)),
        ReportingScheduleTimeUtils.windowStart(ReportingSchedulePeriod.WEEK, to));
    assertEquals(
        Instant.parse("2026-06-20T09:00:00Z"),
        ReportingScheduleTimeUtils.windowStart(ReportingSchedulePeriod.MONTH, to));
  }

  @Test
  void month_window_start_is_calendar_aware() {
    // One calendar month before Mar 31 clamps to Feb 28 (2026 is not a leap year)
    assertEquals(
        Instant.parse("2026-02-28T09:00:00Z"),
        ReportingScheduleTimeUtils.windowStart(
            ReportingSchedulePeriod.MONTH, Instant.parse("2026-03-31T09:00:00Z")));
  }
}
