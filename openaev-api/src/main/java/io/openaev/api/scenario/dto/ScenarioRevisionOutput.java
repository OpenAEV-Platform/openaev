package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.scenario.ScenarioRevision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Content a catalogue scenario had before an update or a rollback. */
public record ScenarioRevisionOutput(
    @JsonProperty("scenario_revision_id") @NotBlank String id,
    @JsonProperty("scenario_revision_number") @NotNull int number,
    @JsonProperty("scenario_revision_reason") @NotNull ScenarioRevision.Reason reason,
    @JsonProperty("scenario_revision_catalog_version") @NotBlank String catalogVersion,
    @JsonProperty("scenario_revision_user") String user,
    @JsonProperty("scenario_revision_created_at") @NotNull Instant createdAt) {}
