package io.openaev.cron;

import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;

public enum ScheduleFrequency {
  DAILY("d"),
  WEEKLY("w"),
  MONTHLY("m"),
  ONESHOT("os");

  private final String value;

  ScheduleFrequency(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return this.value;
  }

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

  private static String allValuesCommaSeparated() {
    return String.join(
        ", ", Arrays.stream(ScheduleFrequency.values()).map(ScheduleFrequency::toString).toList());
  }
}
