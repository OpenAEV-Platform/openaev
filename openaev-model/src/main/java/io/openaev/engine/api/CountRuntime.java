package io.openaev.engine.api;

import io.openaev.database.model.CustomDashboardParameters;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CountRuntime extends Runtime {

  private FlatConfiguration config;

  public CountRuntime(
      FlatConfiguration config,
      Map<String, String> parameters,
      Map<String, CustomDashboardParameters> definitionParameters) {
    this.config = config;
    this.parameters = parameters;
    this.definitionParameters = definitionParameters;
  }
}
