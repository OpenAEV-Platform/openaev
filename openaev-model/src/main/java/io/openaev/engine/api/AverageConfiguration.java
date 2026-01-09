package io.openaev.engine.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AverageConfiguration extends WidgetConfiguration {

  @NotBlank private String field;

  public AverageConfiguration() {
    super(WidgetConfigurationType.AVERAGE);
  }
}
