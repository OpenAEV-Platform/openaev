package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PayloadUsageOutput(
    @Schema(description = "inject: scenario inject; atomic_testing; step: chained scenario step")
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
}
