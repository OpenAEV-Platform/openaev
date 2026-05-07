package io.openaev.api.attack_path;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttackPathNodeOutput(
    @JsonProperty("node_id") @NotBlank String id,
    @JsonProperty("node_type") @NotBlank String type,
    @JsonProperty("node_label") @NotBlank String label,
    @JsonProperty("node_status") String status,
    @JsonProperty("node_hostname") String hostname,
    @JsonProperty("node_ip") String ip,
    @JsonProperty("node_platform") String platform,
    @JsonProperty("node_payload_name") String payloadName,
    @JsonProperty("node_executed_at") Instant executedAt,
    @JsonProperty("node_expectations") List<AttackPathExpectationOutput> expectations) {}
