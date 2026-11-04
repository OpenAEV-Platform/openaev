package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Targets chosen in the drawer (F24), added to every inject that takes their kind; a chained
 * scenario takes its scope and limits instead.
 */
public record CatalogInstallInput(
    @JsonProperty("asset_ids") List<String> assetIds,
    @JsonProperty("asset_group_ids") List<String> assetGroupIds,
    @JsonProperty("team_ids") List<String> teamIds,
    @JsonProperty("manual_targets") List<String> manualTargets,
    @JsonProperty("workflow_configuration") @Valid
        WorkflowConfigurationInput workflowConfiguration) {

  public static CatalogInstallInput empty() {
    return new CatalogInstallInput(List.of(), List.of(), List.of(), List.of(), null);
  }
}
