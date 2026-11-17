package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** A scenario inject, an atomic testing or a chained scenario step running a payload version. */
public record PayloadUsageOutput(
    @Schema(allowableValues = {TYPE_INJECT, TYPE_ATOMIC_TESTING, TYPE_STEP})
        @JsonProperty("usage_type")
        @NotBlank
        String type,
    @JsonProperty("usage_id") @NotBlank String id,
    @JsonProperty("usage_name") String name,
    @JsonProperty("usage_scenario_id") String scenarioId,
    @JsonProperty("usage_scenario_name") String scenarioName) {

  public static final String TYPE_INJECT = "inject";
  public static final String TYPE_ATOMIC_TESTING = "atomic_testing";
  public static final String TYPE_STEP = "step";

  public static PayloadUsageOutput of(Inject inject) {
    Scenario scenario = inject.getScenario();
    return scenario == null
        ? new PayloadUsageOutput(TYPE_ATOMIC_TESTING, inject.getId(), inject.getTitle(), null, null)
        : new PayloadUsageOutput(
            TYPE_INJECT, inject.getId(), inject.getTitle(), scenario.getId(), scenario.getName());
  }

  public static PayloadUsageOutput of(Step step, String title) {
    Scenario scenario = step.getWorkflow().getScenario();
    return new PayloadUsageOutput(
        TYPE_STEP, step.getId(), title, scenario.getId(), scenario.getName());
  }
}
