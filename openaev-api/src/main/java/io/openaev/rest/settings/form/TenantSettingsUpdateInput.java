package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TenantSettingsUpdateInput(
    @JsonProperty("platform_home_dashboard") String platformHomeDashboard,
    @JsonProperty("platform_scenario_dashboard") String platformScenarioDashboard,
    @JsonProperty("platform_simulation_dashboard") String platformSimulationDashboard) {}
