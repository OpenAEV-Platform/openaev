package io.openaev.api.threat_arsenal.move.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayloadUsageMoveInput(
    @JsonProperty("target_payload_id") @NotBlank String targetPayloadId,
    @JsonProperty("usage_ids") @NotNull List<String> usageIds) {}
