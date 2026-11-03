package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Approval of a pending payload version. A version never changes once created, so the approval
 * covers exactly the content the approver saw.
 */
public record ThreatArsenalApproveInput(
    @Schema(description = "Optional comment recorded in the approval history")
        @JsonProperty("approval_comment")
        @Size(max = PayloadApprovalService.MAX_COMMENT_LENGTH)
        String comment) {}
