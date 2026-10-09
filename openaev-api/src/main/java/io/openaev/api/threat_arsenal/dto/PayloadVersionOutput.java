package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PayloadExecutableContent;
import io.openaev.database.model.PayloadVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** A version of the executable content of an action payload. */
public record PayloadVersionOutput(
    @Schema(description = "Version identifier") @JsonProperty("version_id") @NotNull String id,
    @Schema(description = "Version number (1 is the initial content of the payload)")
        @JsonProperty("version_number")
        int number,
    @Schema(description = "Status of the version") @JsonProperty("version_status") @NotNull
        PayloadVersion.STATUS status,
    @Schema(description = "What submitted the version") @JsonProperty("version_origin") @NotNull
        PayloadVersion.ORIGIN origin,
    @Schema(description = "Executable content of the version")
        @JsonProperty("version_content")
        @NotNull
        PayloadExecutableContent content,
    @Schema(
            description =
                "Fingerprint of the executable content, to send back when approving the version")
        @JsonProperty("version_fingerprint")
        @NotNull
        String fingerprint,
    @Schema(description = "Display name of the author, null for a collector")
        @JsonProperty("version_author_name")
        String authorName,
    @Schema(description = "Display name of the user who decided (approved, rejected, superseded)")
        @JsonProperty("version_decider_name")
        String deciderName,
    @Schema(description = "Comment of an approval, or reason of a rejection")
        @JsonProperty("version_comment")
        String comment,
    @Schema(description = "When the version was submitted")
        @JsonProperty("version_created_at")
        @NotNull
        Instant createdAt,
    @Schema(description = "When the version was decided") @JsonProperty("version_decided_at")
        Instant decidedAt) {

  public static PayloadVersionOutput from(PayloadVersion version) {
    return new PayloadVersionOutput(
        version.getId(),
        version.getNumber(),
        version.getStatus(),
        version.getOrigin(),
        version.getSnapshot(),
        version.getFingerprint(),
        version.getAuthorName(),
        version.getDeciderName(),
        version.getComment(),
        version.getCreatedAt(),
        version.getDecidedAt());
  }
}
