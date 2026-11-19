package io.openaev.rest.custom_dashboard.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * What a custom dashboard is used by, read before deleting it: the delete itself is never refused
 * for these, the references are cleared, so a client shows this usage and asks for confirmation.
 */
@Schema(description = "Scenarios, simulations and tenant defaults that use a custom dashboard")
public record CustomDashboardUsageOutput(
    @JsonProperty("custom_dashboard_id") @NotNull String customDashboardId,
    @Schema(description = "Scenarios that show this dashboard in their Analysis tab")
        @JsonProperty("scenarios_count")
        @NotNull
        long scenariosCount,
    @Schema(description = "Simulations that show this dashboard in their Analysis tab")
        @JsonProperty("simulations_count")
        @NotNull
        long simulationsCount,
    @Schema(description = "The dashboard is the tenant default home dashboard (delete refused)")
        @JsonProperty("default_home_dashboard")
        @NotNull
        boolean defaultHomeDashboard,
    @Schema(description = "The dashboard is the tenant default scenario dashboard")
        @JsonProperty("default_scenario_dashboard")
        @NotNull
        boolean defaultScenarioDashboard,
    @Schema(description = "The dashboard is the tenant default simulation dashboard")
        @JsonProperty("default_simulation_dashboard")
        @NotNull
        boolean defaultSimulationDashboard) {}
