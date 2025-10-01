package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.expectation.ExpectationType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record SimulationsResultsLatest(
    @JsonProperty("global_scores_by_expectation_type") @NotNull
        Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> globalScoresByExpectationType) {}
