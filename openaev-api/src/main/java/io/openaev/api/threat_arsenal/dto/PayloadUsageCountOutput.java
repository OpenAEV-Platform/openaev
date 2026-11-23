package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** How many scenario injects, atomic testings and chained steps run a payload version. */
public record PayloadUsageCountOutput(
    @JsonProperty("payload_id") @NotBlank String payloadId,
    @JsonProperty("payload_usage_count") @NotNull long count) {}
