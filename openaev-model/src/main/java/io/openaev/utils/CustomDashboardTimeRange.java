package io.openaev.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public enum CustomDashboardTimeRange {
  @JsonProperty("DEFAULT")
  DEFAULT("DEFAULT"),
  @JsonProperty("ALL_TIME")
  ALL_TIME("ALL_TIME"),
  @JsonProperty("CUSTOM")
  CUSTOM("CUSTOM"),
  @JsonProperty("LAST_DAY")
  LAST_DAY("LAST_DAY"),
  @JsonProperty("LAST_WEEK")
  LAST_WEEK("LAST_WEEK"),
  @JsonProperty("LAST_MONTH")
  LAST_MONTH("LAST_MONTH"),
  @JsonProperty("LAST_QUARTER")
  LAST_QUARTER("LAST_QUARTER"),
  @JsonProperty("LAST_SEMESTER")
  LAST_SEMESTER("LAST_SEMESTER"),
  @JsonProperty("LAST_YEAR")
  LAST_YEAR("LAST_YEAR");

  private final String name;

  CustomDashboardTimeRange(String name) {
    this.name = name;
  }

  public static CustomDashboardTimeRange fromValue(final String value) {
    try {
      return CustomDashboardTimeRange.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      return LAST_QUARTER;
    }
  }
}
