package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.catalog.scenario.update.ScenarioMerge;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * The version the preview was read on, the side chosen for each conflict, and the value typed for
 * those chosen to edit, by change ID.
 */
public record ScenarioUpdateInput(
    @JsonProperty("scenario_update_from_version") @NotBlank String fromVersion,
    @JsonProperty("scenario_update_choices") Map<String, ScenarioMerge.Choice> choices,
    @JsonProperty("scenario_update_edits") Map<String, String> edits) {

  public ScenarioUpdateInput {
    choices = choices == null ? Map.of() : choices;
    edits = edits == null ? Map.of() : edits;
  }
}
