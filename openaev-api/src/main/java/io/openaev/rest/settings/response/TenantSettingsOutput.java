package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.settings.form.ThemeInput;
import jakarta.validation.constraints.NotBlank;

public record TenantSettingsOutput(
    @JsonProperty("platform_name") @NotBlank String platformName,
    @JsonProperty("platform_theme") @NotBlank String platformTheme,
    @JsonProperty("platform_lang") @NotBlank String platformLang,
    @JsonProperty("platform_home_dashboard") String platformHomeDashboard,
    @JsonProperty("platform_scenario_dashboard") String platformScenarioDashboard,
    @JsonProperty("platform_simulation_dashboard") String platformSimulationDashboard,
    @JsonProperty("platform_dark_theme") ThemeInput platformDarkTheme,
    @JsonProperty("platform_light_theme") ThemeInput platformLightTheme) {}
