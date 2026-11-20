package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.catalog.scenario.update.ScenarioMerge;
import java.util.Map;

/** The side chosen for each conflict, by change ID. */
public record ScenarioUpdateInput(
    @JsonProperty("scenario_update_choices") Map<String, ScenarioMerge.Choice> choices) {

  public ScenarioUpdateInput {
    choices = choices == null ? Map.of() : choices;
  }
}
