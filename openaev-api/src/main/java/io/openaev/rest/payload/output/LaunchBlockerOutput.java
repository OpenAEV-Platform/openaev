package io.openaev.rest.payload.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import io.openaev.service.payload_approval.BlockedPayloadsException.BlockedPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * A payload that keeps an atomic testing, a scenario or a simulation from being launched: not
 * approved, or its content changed since its approval.
 */
public record LaunchBlockerOutput(
    @Schema(description = "Payload identifier") @JsonProperty("id") @NotNull String id,
    @Schema(description = "Payload name") @JsonProperty("name") @NotNull String name,
    @Schema(description = "Approval status of the payload (APPROVED when its content changed)")
        @JsonProperty("approval_status")
        @NotNull
        Payload.PAYLOAD_APPROVAL_STATUS approvalStatus,
    @Schema(description = "Why it blocks the launch") @JsonProperty("reason") @NotNull
        String reason) {

  public static List<LaunchBlockerOutput> from(List<BlockedPayload> blocked) {
    return blocked.stream()
        .map(
            b ->
                new LaunchBlockerOutput(
                    b.payloadId(), b.payloadName(), b.approvalStatus(), b.reason()))
        .toList();
  }
}
