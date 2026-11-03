package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Moves an approved or rejected payload version back to pending approval, to review it again. The
 * review is asked outside the platform: nobody is notified.
 */
public record ThreatArsenalReopenInput(
    @Schema(description = "Optional note recorded in the approval history")
        @JsonProperty("approval_comment")
        @Size(max = PayloadApprovalService.MAX_COMMENT_LENGTH)
        String comment) {}
