package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Targets chosen in the install drawer (F12), given to every inject that has none yet. */
public record CatalogInstallInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds) {

  public static CatalogInstallInput empty() {
    return new CatalogInstallInput(List.of(), List.of(), List.of());
  }
}
