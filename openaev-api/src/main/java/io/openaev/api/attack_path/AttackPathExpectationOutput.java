package io.openaev.api.attack_path;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AttackPathExpectationOutput(
    @JsonProperty("expectation_id") @NotBlank String id,
    @JsonProperty("expectation_type") @NotBlank String type,
    @JsonProperty("expectation_status") @NotBlank String status,
    @JsonProperty("expectation_score") Integer score,
    @JsonProperty("expectation_expected_score") Integer expectedScore) {}
