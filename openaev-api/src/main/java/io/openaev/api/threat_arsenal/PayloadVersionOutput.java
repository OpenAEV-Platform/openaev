package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One version of a payload, installed or only in the catalogue (F7), as scenarios show theirs. */
public record PayloadVersionOutput(
    @JsonProperty("payload_id") String id,
    @JsonProperty("payload_action_id") String actionId,
    @JsonProperty("payload_external_id") String externalId,
    @JsonProperty("payload_version") String version,
    @JsonProperty("payload_status") Payload.PAYLOAD_STATUS status,
    @JsonProperty("payload_is_latest") boolean latest,
    @JsonProperty("payload_revoked") boolean revoked,
    @JsonProperty("payload_revoked_in_tenant") boolean revokedInTenant,
    @JsonProperty("payload_current") boolean current,
    @JsonProperty("payload_installed") boolean installed,
    @JsonProperty("payload_changelog") String changelog,
    @JsonProperty("payload_published_at") Instant publishedAt,
    @JsonProperty("payload_usage_count") @NotNull Integer usageCount,
    @JsonProperty("payload_created_at") Instant createdAt) {}
