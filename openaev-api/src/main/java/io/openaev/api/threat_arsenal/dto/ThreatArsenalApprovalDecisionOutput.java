package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Payload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Result of an approval decision on a payload version: its new status and, after an approval, the
 * version it replaces whose injects the approver may move to it (the "new version" dialog).
 */
public record ThreatArsenalApprovalDecisionOutput(
    @Schema(description = "Action of the decided version") @NotBlank @JsonProperty("action_id")
        String actionId,
    @Schema(description = "Approval status of the version after the decision")
        @JsonProperty("action_approval_status")
        Payload.PAYLOAD_APPROVAL_STATUS approvalStatus,
    @Schema(description = "The decision, as recorded in the approval history")
        @JsonProperty("action_approval_latest")
        PayloadApprovalOutput approvalLatest,
    @Schema(description = "Id of the decided payload version") @JsonProperty("action_payload_id")
        String payloadId,
    @Schema(description = "Number of the decided payload version (e.g. 1.2.0)")
        @JsonProperty("action_payload_version")
        String payloadVersion,
    @Schema(
            description =
                "After an approval: the action of the approved version this one replaces, still"
                    + " run by injects the approver may move to it")
        @JsonProperty("action_previous_version_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String previousVersionActionId,
    @Schema(
            description =
                "After an approval: scenario injects, atomic tests and chained steps still on the"
                    + " replaced version")
        @JsonProperty("action_previous_version_usages")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer previousVersionUsages) {}
