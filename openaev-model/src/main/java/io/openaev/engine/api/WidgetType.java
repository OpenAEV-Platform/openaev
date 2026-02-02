package io.openaev.engine.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public enum WidgetType {
  @JsonProperty("vertical-barchart")
  VERTICAL_BAR_CHART("vertical-barchart"),
  @JsonProperty("horizontal-barchart")
  HORIZONTAL_BAR_CHART("horizontal-barchart"),
  @JsonProperty("security-coverage")
  SECURITY_COVERAGE_CHART("security-coverage"),
  @JsonProperty("line")
  LINE("line"),
  @JsonProperty("donut")
  DONUT("donut"),
  @JsonProperty("list")
  LIST("list"),
  @JsonProperty("attack-path")
  ATTACK_PATH("attack-path"),
  @JsonProperty("number")
  NUMBER("number"),
  @JsonProperty("average")
  AVERAGE("average");

  public final String type;

  WidgetType(@NotNull final String type) {
    this.type = type;
  }
}
