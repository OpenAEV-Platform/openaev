package io.openaev.cron;

import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;

/**
 * Enumeration representing the available scheduling frequencies for recurring tasks.
 *
 * <p>This enum defines the time intervals at which scheduled operations can be executed, such as
 * data collection, report generation, or synchronization tasks.
 *
 * <p>Available frequencies:
 *
 * <ul>
 *   <li>{@code DAILY} ("d") - Executes once per day
 *   <li>{@code WEEKLY} ("w") - Executes once per week
 *   <li>{@code MONTHLY} ("m") - Executes once per month
 *   <li>{@code HOURLY} ("h") - Executes once per hour
 *   <li>{@code ONESHOT} ("os") - Executes only once (non-recurring)
 * </ul>
 *
 * @see ScheduleFrequencyConverter
 */
public enum ScheduleFrequency {
  /** Executes once per day. */
  DAILY("d"),
  /** Executes once per week. */
  WEEKLY("w"),
  /** Executes once per month. */
  MONTHLY("m"),
  /** Executes once per hour. */
  HOURLY("h"),
  /** Executes only once (non-recurring). */
  ONESHOT("os");

  private final String value;

  /**
   * Constructs a schedule frequency with the specified short code.
   *
   * @param value the short code representing this frequency
   */
  ScheduleFrequency(String value) {
    this.value = value;
  }

  /**
   * Returns the short code representation of this frequency.
   *
   * @return the short code (e.g., "d" for DAILY, "h" for HOURLY)
   */
  @Override
  public String toString() {
    return this.value;
  }

  /**
   * Parses a string value to its corresponding {@link ScheduleFrequency} enum constant.
   *
   * @param value the short code to parse (case-insensitive)
   * @return the matching {@link ScheduleFrequency}
   * @throws IllegalArgumentException if the value does not match any known frequency
   */
  public static ScheduleFrequency fromString(@NotBlank final String value) {
    for (ScheduleFrequency prop : ScheduleFrequency.values()) {
      if (prop.value.equalsIgnoreCase(value)) {
        return prop;
      }
    }
    throw new IllegalArgumentException(
        "Could not find an option for value %s. Valid values are %s"
            .formatted(value, allValuesCommaSeparated()));
  }

  /**
   * Returns a comma-separated string of all valid frequency short codes.
   *
   * @return all valid values joined by commas
   */
  private static String allValuesCommaSeparated() {
    return String.join(
        ", ", Arrays.stream(ScheduleFrequency.values()).map(ScheduleFrequency::toString).toList());
  }
}
