package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ThreatArsenalActionIdOutput(@JsonProperty("action_id") @NotBlank String actionId) {}
