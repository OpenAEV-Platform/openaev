package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rejection of a pending action payload. */
public record ThreatArsenalRejectInput(
    @Schema(
            description =
                "Reason of the rejection, kept in the history and sent with the notification")
        @JsonProperty("approval_reason")
        @NotBlank
        @Size(max = PayloadApprovalService.MAX_COMMENT_LENGTH)
        String reason) {}
