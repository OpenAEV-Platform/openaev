package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record ScenarioStatistic(
    @JsonProperty("simulations_results_latest") @NotNull
        SimulationsResultsLatest simulationsResultsLatest) {}
