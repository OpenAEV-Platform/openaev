package io.openaev.api.payload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PayloadRevocationInput(
    @JsonProperty("payload_revocation_reason") @NotBlank String reason) {}
