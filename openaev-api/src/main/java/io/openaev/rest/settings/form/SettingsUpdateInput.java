package io.openaev.rest.settings.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SettingsUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_name")
  @Schema(description = "Name of the platform")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_theme")
  @Schema(description = "Theme of the platform")
  private String theme;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("platform_lang")
  @Schema(description = "Language of the platform")
  private String lang;

  @JsonProperty("platform_home_dashboard")
  @Schema(description = "Default home dashboard of the platform")
  private String homeDashboard;

  @JsonProperty("platform_scenario_dashboard")
  @Schema(description = "Default scenario dashboard of the platform")
  private String scenarioDashboard;

  @JsonProperty("platform_simulation_dashboard")
  @Schema(description = "Default simulation dashboard of the platform")
  private String simulationDashboard;
}
