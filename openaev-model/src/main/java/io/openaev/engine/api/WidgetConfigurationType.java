package io.openaev.engine.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public enum WidgetConfigurationType {
  @JsonProperty(Values.FLAT)
  FLAT(Values.FLAT),
  @JsonProperty(Values.AVERAGE)
  AVERAGE(Values.AVERAGE),
  @JsonProperty(Values.LIST)
  LIST(Values.LIST),
  @JsonProperty(Values.TEMPORAL_HISTOGRAM)
  TEMPORAL_HISTOGRAM(Values.TEMPORAL_HISTOGRAM),
  @JsonProperty(Values.STRUCTURAL_HISTOGRAM)
  STRUCTURAL_HISTOGRAM(Values.STRUCTURAL_HISTOGRAM);

  public final String type;

  WidgetConfigurationType(@NotNull final String type) {
    this.type = type;
  }

  public static class Values {
    public static final String FLAT = "flat";
    public static final String AVERAGE = "average";
    public static final String LIST = "list";
    public static final String TEMPORAL_HISTOGRAM = "temporal-histogram";
    public static final String STRUCTURAL_HISTOGRAM = "structural-histogram";
  }
}
