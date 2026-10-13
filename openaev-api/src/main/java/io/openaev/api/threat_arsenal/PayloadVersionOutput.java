package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record PayloadVersionOutput(
    @JsonProperty("payload_id") @NotBlank String id,
    @JsonProperty("payload_version") String version,
    @JsonProperty("payload_status") Payload.PAYLOAD_STATUS status,
    @JsonProperty("payload_is_latest") boolean latest,
    @JsonProperty("payload_revoked") boolean revoked,
    @JsonProperty("payload_current") boolean current,
    @JsonProperty("payload_created_at") Instant createdAt) {}
