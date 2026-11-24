package io.openaev.api.scenario.dto;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.List;

/** Targets added to the injects that take their kind: of a scenario at install, of a launch. */
public record ScenarioTargetsInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets) {

  /** No input, or a list left out, means nothing chosen of that kind. */
  public static TargetChoice choiceOf(ScenarioTargetsInput input) {
    return input == null
        ? TargetChoice.NONE
        : new TargetChoice(
            emptyIfNull(input.assetIds),
            emptyIfNull(input.assetGroupIds),
            emptyIfNull(input.teamIds),
            emptyIfNull(input.manualTargets));
  }
}
