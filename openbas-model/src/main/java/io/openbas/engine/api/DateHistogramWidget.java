package io.openbas.engine.api;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DateHistogramWidget extends HistogramWidget {

  public static final String TEMPORAL_MODE = "temporal";

  @NotNull private HistogramInterval interval = HistogramInterval.day;

  public DateHistogramWidget() {
    super(TEMPORAL_MODE);
  }
}
