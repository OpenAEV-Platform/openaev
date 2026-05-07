package io.openaev.api.attack_path;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttackPathEdgeOutput(
    @JsonProperty("edge_id") @NotBlank String id,
    @JsonProperty("edge_source") @NotBlank String source,
    @JsonProperty("edge_target") @NotBlank String target,
    @JsonProperty("edge_type") @NotBlank String type,
    @JsonProperty("edge_label") String label) {}
