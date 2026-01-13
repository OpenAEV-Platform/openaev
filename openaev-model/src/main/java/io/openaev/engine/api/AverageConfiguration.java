package io.openaev.engine.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AverageConfiguration extends WidgetConfiguration {

  @NotBlank private Map<String,String> field;

  public AverageConfiguration() {
    super(WidgetConfigurationType.AVERAGE);
  }
}
