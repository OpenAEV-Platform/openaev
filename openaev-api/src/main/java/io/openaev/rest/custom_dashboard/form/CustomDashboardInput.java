package io.openaev.rest.custom_dashboard.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;
import static java.util.Objects.requireNonNull;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.CustomDashboardParameters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomDashboardInput {

  @JsonProperty("custom_dashboard_name")
  @NotBlank(message = MANDATORY_MESSAGE)
  private String name;

  @JsonProperty("custom_dashboard_description")
  private String description;

  @JsonProperty("custom_dashboard_parameters")
  private List<CustomDashboardParametersInput> parameters;

  // -- METHOD --

  public CustomDashboard toCustomDashboard(@NotNull CustomDashboard customDashboard) {
    requireNonNull(customDashboard, "CustomDashboard must not be null.");

    customDashboard.setName(this.getName());
    customDashboard.setDescription(this.getDescription());
    if (this.getParameters() != null) {
      List<CustomDashboardParameters> params =
          this.getParameters().stream()
              .map(
                  p -> {
                    CustomDashboardParameters targetParam;
                    // Exists
                    if (hasText(p.getId())) {
                      targetParam =
                          customDashboard.getParameters().stream()
                              .filter(existing -> p.getId().equals(existing.getId()))
                              .findFirst()
                              .orElse(new CustomDashboardParameters());
                      // New one
                    } else {
                      targetParam = new CustomDashboardParameters();
                    }
                    CustomDashboardParameters param = p.toCustomDashboardParameter(targetParam);
                    param.setCustomDashboard(customDashboard);
                    return param;
                  })
              .collect(Collectors.toList());
      customDashboard.getParameters().clear(); // Due to orphanRemoval
      customDashboard.getParameters().addAll(params);
    }
    return customDashboard;
  }
}
