package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** A payload version an update or a rollback runs. */
public record ScenarioPayloadVersionOutput(
    @JsonProperty("payload_name") String name,
    @JsonProperty("payload_version") @NotBlank String version) {}
