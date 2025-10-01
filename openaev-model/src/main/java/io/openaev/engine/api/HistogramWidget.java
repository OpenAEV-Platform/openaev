package io.openaev.engine.api;

import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class HistogramWidget extends WidgetConfiguration {

  @Setter(NONE)
  @NotNull
  private final String mode;

  @NotBlank
  @JsonProperty("date_attribute")
  private String dateAttribute;

  private boolean stacked;

  @JsonProperty("display_legend")
  private boolean displayLegend;

  HistogramWidget(String mode) {
    super(
        "temporal".equals(mode)
            ? WidgetConfigurationType.TEMPORAL_HISTOGRAM
            : WidgetConfigurationType.STRUCTURAL_HISTOGRAM);
    this.mode = mode;
  }
}
