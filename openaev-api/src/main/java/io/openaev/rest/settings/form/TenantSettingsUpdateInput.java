package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TenantSettingsUpdateInput(
    @JsonProperty("platform_name") @NotBlank String platformName,
    @JsonProperty("platform_theme") @NotBlank String platformTheme,
    @JsonProperty("platform_lang") @NotBlank String platformLang,
    @JsonProperty("platform_home_dashboard") String platformHomeDashboard,
    @JsonProperty("platform_scenario_dashboard") String platformScenarioDashboard,
    @JsonProperty("platform_simulation_dashboard") String platformSimulationDashboard) {}
