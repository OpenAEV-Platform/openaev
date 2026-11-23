package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ThreatArsenalActionIdsInput(
    @JsonProperty("action_ids") @NotNull List<String> actionIds) {}
