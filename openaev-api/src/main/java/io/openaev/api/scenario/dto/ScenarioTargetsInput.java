package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Targets added to the injects of the launched simulation that take their kind. */
public record ScenarioTargetsInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets) {}
