package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Targets added to every inject of the installed scenario that takes their kind. */
public record TimeBasedScenarioInstallInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets)
    implements CatalogScenarioInstallInput {}
