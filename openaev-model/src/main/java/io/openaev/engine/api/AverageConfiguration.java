package io.openaev.engine.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AverageConfiguration extends WidgetConfigurationWithSeries {

  @NotBlank @JsonIgnore private Map<String, String> field;

  public AverageConfiguration() {
    super(WidgetConfigurationType.AVERAGE);
  }
}
