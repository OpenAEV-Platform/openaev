package io.openaev.api.findings.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record FindingOccurrenceContext(
    @JsonProperty("context_id") @NotBlank String id,
    @JsonProperty("context_name") String name) {}
