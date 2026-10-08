package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of the 409 answer to an action update sent with {@code check_approval_impact}: the edit
 * would send the approved payload back to pending while it is used. Nothing was saved.
 */
public record ThreatArsenalApprovalImpactOutput(
    @Schema(description = "What saving would do") @JsonProperty("message") @NotNull String message,
    @Schema(description = "Where the payload is used") @JsonProperty("usage") @NotNull
        ThreatArsenalActionUsageOutput usage) {}
