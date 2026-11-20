package io.openaev.api.catalog.dto.scenario;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.List;

/** Targets added to the injects that take their kind: of a scenario at install, of a launch. */
// A launch sends the targets alone, without the scenario type an install names.
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public record ScenarioTargetsInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets)
    implements CatalogScenarioInstallInput {

  @Override
  public TargetChoice toChoice() {
    return new TargetChoice(
        emptyIfNull(assetIds),
        emptyIfNull(assetGroupIds),
        emptyIfNull(teamIds),
        emptyIfNull(manualTargets));
  }
}
