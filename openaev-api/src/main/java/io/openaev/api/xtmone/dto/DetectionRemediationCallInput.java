package io.openaev.api.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the XTM One detection-remediation proxy endpoint. {@code agentSlug} is optional:
 * when null/blank the service falls back to the first enabled {@code detection.generate} agent in
 * the discovered catalog.
 */
public record DetectionRemediationCallInput(
    @JsonProperty("agent_slug") @Nullable String agentSlug,
    @JsonProperty("content") @NotBlank String content,
    @JsonProperty("collector_type") @NotBlank String collectorType) {}
