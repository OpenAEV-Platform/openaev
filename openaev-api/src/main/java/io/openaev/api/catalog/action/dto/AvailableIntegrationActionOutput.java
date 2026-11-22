package io.openaev.api.catalog.action.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/** An action that comes with a catalogue integration the tenant does not run. */
public record AvailableIntegrationActionOutput(
    @JsonProperty("action_id") @NotBlank String id,
    @JsonProperty("action_labels") @NotNull Map<String, String> labels,
    @JsonProperty("action_domain") String domain,
    @JsonProperty("action_platforms") @NotNull List<String> platforms,
    @JsonProperty("action_attack_patterns") @NotNull List<String> attackPatterns,
    @JsonProperty("action_integration_id") @NotBlank String integrationId,
    @JsonProperty("action_integration_title") @NotBlank String integrationTitle,
    @JsonProperty("action_integration_logo_url") String integrationLogoUrl)
    implements AvailableActionOutput {}
