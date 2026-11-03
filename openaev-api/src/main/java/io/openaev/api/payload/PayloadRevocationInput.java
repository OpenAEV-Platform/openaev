package io.openaev.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PayloadRevocationInput(
    @JsonProperty("content_revocation_reason") @NotBlank String reason) {}
