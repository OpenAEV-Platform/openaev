package io.openaev.api.threat_arsenal.customization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PayloadCustomizationUpdateOutput(
    @JsonProperty("update_payload_id") @NotBlank String payloadId,
    @JsonProperty("update_version") @NotBlank String version,
    @Schema(description = "The fields Filigran or the copy changed since the base version")
        @JsonProperty("update_fields")
        @NotNull
        List<PayloadFieldUpdateOutput> fields,
    @Schema(description = "The other keys Filigran changed: the copy takes them as they are")
        @JsonProperty("update_other_changes")
        @NotNull
        List<String> otherChanges,
    @Schema(description = "The release notes of the newer versions, newest first")
        @JsonProperty("update_release_notes")
        @NotNull
        List<PayloadReleaseNoteOutput> releaseNotes) {}
