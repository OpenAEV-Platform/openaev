package io.openaev.engine.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AverageConfiguration extends WidgetConfiguration {

  @NotBlank private Map<String, String> field;

  public AverageConfiguration() {
    super(WidgetConfigurationType.AVERAGE);
  }
}
