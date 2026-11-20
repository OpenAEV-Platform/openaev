package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Every catalogue version of an installed scenario, newest first, and the simulations it ran. */
public record ScenarioVersionsOutput(
    @JsonProperty("scenario_versions") @NotNull List<ScenarioVersionOutput> versions,
    @JsonProperty("scenario_simulations_count") @NotNull long simulationsCount) {}
