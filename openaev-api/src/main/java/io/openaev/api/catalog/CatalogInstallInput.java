package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Targets chosen in the drawer (F24), added to every inject that takes their kind. */
public record CatalogInstallInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets) {

  public static CatalogInstallInput empty() {
    return new CatalogInstallInput(List.of(), List.of(), List.of(), List.of());
  }
}
