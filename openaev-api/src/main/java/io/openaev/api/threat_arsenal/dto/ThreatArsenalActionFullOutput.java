package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Full-detail output DTO for a single threat arsenal action. */
public record ThreatArsenalActionFullOutput(
    @Schema(description = "Action unique identifier") @NotBlank @JsonProperty("action_id")
        String id,
    @Schema(description = "Action implementation type") @JsonProperty("action_type") String type,
    @Schema(description = "Action display name") @NotBlank @JsonProperty("action_labels")
        Map<String, String> name,
    @Schema(description = "Action description") @JsonProperty("action_description")
        String description,
    @Schema(description = "Supported endpoint platforms for this action")
        @JsonProperty("action_platforms")
        Endpoint.PLATFORM_TYPE[] platforms,
    @Schema(description = "Executor used for cleanup operations")
        @JsonProperty("action_cleanup_executor")
        String cleanupExecutor,
    @Schema(description = "Cleanup command executed after action run")
        @JsonProperty("action_cleanup_command")
        String cleanupCommand,
    @Schema(description = "Action input arguments definition") @JsonProperty("action_arguments")
        List<PayloadArgument> arguments,
    @Schema(description = "Prerequisites required before action execution")
        @JsonProperty("action_prerequisites")
        List<PayloadPrerequisite> prerequisites,
    @Schema(description = "External reference identifier") @JsonProperty("action_external_id")
        String externalId,
    @Schema(description = "Action source origin") @NotNull @JsonProperty("action_source")
        Payload.PAYLOAD_SOURCE source,
    @Schema(description = "Expected output types for action execution")
        @JsonProperty("action_expectations")
        InjectExpectation.EXPECTATION_TYPE[] expectations,
    @Schema(description = "Current action lifecycle status") @NotNull @JsonProperty("action_status")
        Payload.PAYLOAD_STATUS status,
    @Schema(description = "CPU architecture targeted for action execution")
        @NotNull
        @JsonProperty("action_execution_arch")
        Payload.PAYLOAD_EXECUTION_ARCH executionArch,
    @Schema(description = "Collector type associated with this action")
        @JsonProperty("action_collector_type")
        String collectorType,
    @Schema(description = "Detection and remediation mappings for this action")
        @JsonProperty("action_detection_remediations")
        List<DetectionRemediation> detectionRemediations,
    @Schema(description = "Parsers used to process action outputs")
        @JsonProperty("action_output_parsers")
        Set<OutputParser> outputParsers,
    @Schema(description = "Tags attached to the action") @JsonProperty("action_tags")
        List<String> tags,
    @Schema(description = "Domains related to the action") @JsonProperty("action_domains")
        List<String> domains,
    @Schema(description = "MITRE ATT&CK patterns associated with the action")
        @JsonProperty("action_attack_patterns")
        List<String> attackPatterns,
    @Schema(description = "Executor used for command actions") @JsonProperty("command_executor")
        String commandExecutor,
    @Schema(description = "Command content for command actions") @JsonProperty("command_content")
        String commandContent,
    @Schema(description = "Hostname resolved by DNS resolution actions")
        @JsonProperty("dns_resolution_hostname")
        String dnsResolutionHostname,
    @Schema(description = "Dropped file path for file-drop actions") @JsonProperty("file_drop_file")
        String fileDropFile,
    @Schema(description = "Executable file path for executable actions")
        @JsonProperty("executable_file")
        String executableFile,
    @Schema(description = "Action creation timestamp") @NotNull @JsonProperty("action_created_at")
        Instant createdAt,
    @Schema(description = "Action last update timestamp")
        @NotNull
        @JsonProperty("action_updated_at")
        Instant updatedAt) {}
