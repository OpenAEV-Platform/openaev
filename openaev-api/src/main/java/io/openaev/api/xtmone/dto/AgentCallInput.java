package io.openaev.api.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the synchronous and streaming XTM One agent proxy endpoints. The {@code
 * agentSlug} is validated against the discovered intent catalog by the service layer before any
 * upstream call. The optional {@code intent} is only used for telemetry: calls carrying a known
 * feature intent are counted in the same backend-agnostic per-feature counter as the legacy
 * endpoints; it never influences routing.
 */
public record AgentCallInput(
    @JsonProperty("agent_slug") @NotBlank String agentSlug,
    @JsonProperty("content") @NotBlank String content,
    @JsonProperty("intent") String intent) {}
