package io.openaev.api.threat_arsenal.move.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayloadUsageMoveInput(
    @JsonProperty("target_payload_id") @NotBlank String targetPayloadId,
    @JsonProperty("inject_ids") @NotNull List<String> injectIds,
    @JsonProperty("step_ids") @NotNull List<String> stepIds) {}
