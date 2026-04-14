package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TenantSettingsOutput(
    @JsonProperty("platform_home_dashboard") String platformHomeDashboard,
    @JsonProperty("platform_scenario_dashboard") String platformScenarioDashboard,
    @JsonProperty("platform_simulation_dashboard") String platformSimulationDashboard) {}
