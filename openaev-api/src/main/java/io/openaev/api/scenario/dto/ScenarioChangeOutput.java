package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.service.catalog.scenario.update.ScenarioMerge;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A field of a scenario an update or a rollback changes, or a whole element when the field is
 * empty, with its value in the installed version, in the content applied and in the scenario now.
 */
public record ScenarioChangeOutput(
    @JsonProperty("scenario_change_id") @NotBlank String id,
    @JsonProperty("scenario_change_element") @NotBlank String element,
    @JsonProperty("scenario_change_field") String field,
    @JsonProperty("scenario_change_title") String title,
    @JsonProperty("scenario_change_status") @NotNull ScenarioMerge.Status status,
    @JsonProperty("scenario_change_editable") @NotNull boolean editable,
    @JsonProperty("scenario_change_base") JsonNode base,
    @JsonProperty("scenario_change_applied") JsonNode applied,
    @JsonProperty("scenario_change_local") JsonNode local) {}
