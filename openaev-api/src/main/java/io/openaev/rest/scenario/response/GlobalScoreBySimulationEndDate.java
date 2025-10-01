package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record GlobalScoreBySimulationEndDate(
    @JsonProperty("simulation_end_date") @NotNull Instant simulationEndDate,
    @JsonProperty("global_score_success_percentage") @NotNull float globalScoreSuccessPercentage) {}
