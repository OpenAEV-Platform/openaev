package io.openaev.rest.custom_dashboard.form;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CustomDashboardParameters;
import io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomDashboardParametersInput {

  @JsonProperty("custom_dashboards_parameter_id")
  private String id;

  @NotNull
  @JsonProperty("custom_dashboards_parameter_name")
  private String name;

  @NotNull
  @JsonProperty("custom_dashboards_parameter_type")
  private CustomDashboardParameterType type;

  // -- METHOD --

  public CustomDashboardParameters toCustomDashboardParameter(
      @NotNull CustomDashboardParameters customDashboardParameters) {
    requireNonNull(customDashboardParameters, "CustomDashboardParameters must not be null.");

    customDashboardParameters.setName(this.getName());
    customDashboardParameters.setType(this.getType());
    return customDashboardParameters;
  }
}
