package io.openaev.api.scenario.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** An inject running a revoked payload version, with the action to move it to another version. */
public record RevokedInjectOutput(
    @JsonProperty("inject_id") @NotBlank String id,
    @JsonProperty("inject_title") @NotBlank String title,
    @JsonProperty("inject_action_id") @NotBlank String actionId,
    @JsonProperty("inject_action_name") @NotBlank String actionName) {}
