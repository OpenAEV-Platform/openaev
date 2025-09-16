package io.openbas.engine.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FlatConfiguration extends WidgetConfiguration {

  public FlatConfiguration() {
    super(WidgetConfigurationType.FLAT);
  }
}
