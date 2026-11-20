package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.catalog.CatalogItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A catalogue version of an installed scenario. */
public record ScenarioVersionOutput(
    @JsonProperty("scenario_version") @NotBlank String version,
    @JsonProperty("scenario_version_status") @NotNull CatalogItemStatus status,
    @JsonProperty("scenario_version_changelog") String changelog,
    @JsonProperty("scenario_version_published_at") Instant publishedAt,
    @JsonProperty("scenario_version_current") @NotNull boolean current,
    @JsonProperty("scenario_version_latest") @NotNull boolean latest) {}
