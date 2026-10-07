package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Approval of a pending action payload, bound to the content the approver saw. */
public record ThreatArsenalApproveInput(
    @Schema(
            description =
                "Content fingerprint shown to the approver (action_approval_fingerprint): the"
                    + " approval is refused if the payload changed since")
        @JsonProperty("approval_fingerprint")
        @NotBlank
        String fingerprint,
    @Schema(description = "Optional comment recorded in the approval history")
        @JsonProperty("approval_comment")
        @Size(max = PayloadApprovalService.MAX_COMMENT_LENGTH)
        String comment) {}
