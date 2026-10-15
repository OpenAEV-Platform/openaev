package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** What the install drawer shows (F12): each inject, its target kinds, and what carries over. */
public record CatalogInstallPreviewOutput(
    @JsonProperty("previous_version") String previousVersion,
    @JsonProperty("previous_scenario_id") String previousScenarioId,
    @JsonProperty("injects") @NotNull List<InjectPreview> injects) {

  public record InjectPreview(
      @JsonProperty("inject_catalog_id") String catalogId,
      @JsonProperty("inject_title") @NotNull String title,
      @JsonProperty("target_fields") @NotNull List<String> targetFields,
      @JsonProperty("is_new") boolean isNew,
      @JsonProperty("is_targeted") boolean isTargeted) {}
}
