package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** An atomic testing, scenario or simulation using the payload of an action. */
public record ThreatArsenalActionUsageItem(
    @Schema(description = "Identifier") @JsonProperty("id") @NotNull String id,
    @Schema(description = "Name") @JsonProperty("name") @NotNull String name) {}
