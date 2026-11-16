package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** What an admin chooses to install a scenario, by the type of the scenario. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "scenario_type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TimeBasedScenarioInstallInput.class, name = "time-based"),
  @JsonSubTypes.Type(value = ChainedScenarioInstallInput.class, name = "chained")
})
public sealed interface CatalogScenarioInstallInput
    permits TimeBasedScenarioInstallInput, ChainedScenarioInstallInput {}
