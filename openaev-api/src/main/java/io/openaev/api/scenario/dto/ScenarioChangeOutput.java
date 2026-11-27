package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.catalog.merge.MergeStatus;
import io.openaev.service.catalog.scenario.update.ScenarioElementKind;
import io.openaev.service.catalog.scenario.update.ScenarioFieldKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A field of a scenario an update or a rollback changes, or a whole element when it has no field
 * kind, with its value in the content applied and in the scenario now.
 */
public record ScenarioChangeOutput(
    @JsonProperty("scenario_change_id") @NotBlank String id,
    @JsonProperty("scenario_change_element_kind") @NotNull ScenarioElementKind elementKind,
    @JsonProperty("scenario_change_field_kind") ScenarioFieldKind fieldKind,
    @JsonProperty("scenario_change_field_key") String fieldKey,
    @JsonProperty("scenario_change_title") String title,
    @JsonProperty("scenario_change_status") @NotNull MergeStatus status,
    @JsonProperty("scenario_change_editable") @NotNull boolean editable,
    @JsonProperty("scenario_change_applied") JsonNode applied,
    @JsonProperty("scenario_change_local") JsonNode local) {}
