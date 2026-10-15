package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/** The latest version of a catalogue item, with every version and what the tenant installed. */
public record CatalogItemOutput(
    @JsonProperty("catalog_item_type") ContentType type,
    @JsonProperty("catalog_item_external_id") @NotBlank String externalId,
    @JsonProperty("catalog_item_version") @NotBlank String version,
    @JsonProperty("catalog_item_status") String status,
    @JsonProperty("catalog_item_name") String name,
    @JsonProperty("catalog_item_description") String description,
    @JsonProperty("catalog_item_author") String author,
    @JsonProperty("catalog_item_severity") String severity,
    @JsonProperty("catalog_item_category") String category,
    @JsonProperty("catalog_item_main_focus") String mainFocus,
    @JsonProperty("catalog_item_scenario_type") String scenarioType,
    @JsonProperty("catalog_item_platforms") List<String> platforms,
    @JsonProperty("catalog_item_injects_count") Integer injectsCount,
    @JsonProperty("catalog_item_changelog") String changelog,
    @JsonProperty("catalog_item_published_at") Instant publishedAt,
    @JsonProperty("catalog_item_versions") List<String> versions,
    @JsonProperty("catalog_item_installed_versions") List<String> installedVersions,
    @JsonProperty("catalog_item_installed_scenarios") List<InstalledScenario> installedScenarios) {

  public record InstalledScenario(
      @JsonProperty("version") @NotBlank String version,
      @JsonProperty("scenario_id") @NotBlank String scenarioId) {}

  public static CatalogItemOutput of(
      CatalogItem latest,
      List<String> versions,
      List<String> installedVersions,
      List<InstalledScenario> installedScenarios) {
    return new CatalogItemOutput(
        latest.getType(),
        latest.getExternalId(),
        latest.getVersion(),
        latest.getStatus(),
        latest.getName(),
        latest.getDescription(),
        latest.getAuthor(),
        latest.getSeverity(),
        latest.getCategory(),
        latest.getMainFocus(),
        latest.getScenarioType(),
        latest.getPlatforms() == null ? List.of() : List.of(latest.getPlatforms()),
        latest.getInjectsCount(),
        latest.getChangelog(),
        latest.getPublishedAt(),
        versions,
        installedVersions,
        installedScenarios);
  }
}
