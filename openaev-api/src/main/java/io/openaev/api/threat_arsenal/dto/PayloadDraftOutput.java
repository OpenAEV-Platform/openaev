package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A draft of a payload version, or of a new action: what the editor saved, and the version it
 * started from.
 */
public record PayloadDraftOutput(
    @JsonProperty("draft_id") @NotBlank String id,
    // The action of the version it started from; null once that version is deleted.
    @JsonProperty("draft_source_action_id") String sourceActionId,
    @JsonProperty("draft_source_version") String sourceVersion,
    // The draft fixes a rejected version: publishing it sends that version again, same number.
    @JsonProperty("draft_rework") boolean rework,
    // Why the version this draft fixes was rejected (latest rejection reason).
    @JsonProperty("draft_rework_reason") String reworkReason,
    // The action to open the draft from: the version it fixes or started from, else the payload's
    // latest version; null for a new action.
    @JsonProperty("draft_action_id") String actionId,
    // A draft of a new action: it has no version until it is published.
    @JsonProperty("draft_new_action") boolean newAction,
    @JsonProperty("draft_content") @NotNull JsonNode content,
    @JsonProperty("draft_updated_by") String updatedBy,
    @JsonProperty("draft_created_at") @NotNull Instant createdAt,
    @JsonProperty("draft_updated_at") @NotNull Instant updatedAt) {}
