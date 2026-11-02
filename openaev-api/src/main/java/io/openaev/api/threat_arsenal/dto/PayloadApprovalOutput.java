package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One entry of the approval history of an action payload. */
public record PayloadApprovalOutput(
    @Schema(description = "Entry identifier") @JsonProperty("approval_id") @NotNull String id,
    @Schema(description = "Status set by this entry") @JsonProperty("approval_status") @NotNull
        Payload.PAYLOAD_APPROVAL_STATUS status,
    @Schema(description = "What produced this entry") @JsonProperty("approval_origin") @NotNull
        PayloadApproval.ORIGIN origin,
    @Schema(
            description =
                "True when set by the approval rules (automatic approval of an approver's own"
                    + " write), false for an explicit approve or reject")
        @JsonProperty("approval_automatic")
        boolean automatic,
    @Schema(description = "User who caused the entry, null for platform or collector writes")
        @JsonProperty("approval_actor")
        String actorId,
    @Schema(description = "Display name of that user at the time")
        @JsonProperty("approval_actor_name")
        String actorName,
    @Schema(description = "Comment of an approval, or reason of a rejection")
        @JsonProperty("approval_comment")
        String comment,
    @Schema(description = "When the entry was recorded")
        @JsonProperty("approval_created_at")
        @NotNull
        Instant createdAt) {

  public static PayloadApprovalOutput from(PayloadApproval entry) {
    return new PayloadApprovalOutput(
        entry.getId(),
        entry.getStatus(),
        entry.getOrigin(),
        entry.isAutomatic(),
        entry.getActor() != null ? entry.getActor().getId() : null,
        entry.getActorName(),
        entry.getComment(),
        entry.getCreatedAt());
  }
}
