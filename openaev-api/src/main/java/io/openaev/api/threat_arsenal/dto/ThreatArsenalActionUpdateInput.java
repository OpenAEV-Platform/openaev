package io.openaev.api.threat_arsenal.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadArgument;
import io.openaev.database.model.PayloadPrerequisite;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public record ThreatArsenalActionUpdateInput(
    @NotBlank(message = MANDATORY_MESSAGE) @JsonProperty("action_name") String name,
    @JsonProperty("action_platforms") Endpoint.PLATFORM_TYPE[] platforms,
    @JsonProperty("action_description") String description,
    @JsonProperty("command_executor") @Schema(types = {"string", "null"}) String executor,
    @JsonProperty("command_content") @Schema(types = {"string", "null"}) String content,
    @NotNull @JsonProperty("action_execution_arch") Payload.PAYLOAD_EXECUTION_ARCH executionArch,
    @NotNull @JsonProperty("action_expectations") InjectExpectation.EXPECTATION_TYPE[] expectations,
    @JsonProperty("executable_file") String executableFile,
    @JsonProperty("file_drop_file") String fileDropFile,
    @JsonProperty("dns_resolution_hostname") String hostname,
    @JsonProperty("action_arguments") List<PayloadArgument> arguments,
    @JsonProperty("action_prerequisites") List<PayloadPrerequisite> prerequisites,
    @JsonProperty("action_cleanup_executor") @Schema(types = {"string", "null"})
        String cleanupExecutor,
    @JsonProperty("action_cleanup_command") @Schema(types = {"string", "null"})
        String cleanupCommand,
    @JsonProperty("action_tags") List<String> tagIds,
    @JsonProperty("action_attack_patterns") List<String> attackPatternsIds,
    @JsonProperty("action_detection_remediations")
        @Schema(description = "List of detection remediation gaps for collectors")
        List<DetectionRemediationInput> detectionRemediations,
    @JsonProperty("action_output_parsers") @Schema(description = "Set of output parsers")
        Set<OutputParserInput> outputParsers,
    @NotNull(message = MANDATORY_MESSAGE)
        @JsonProperty("action_domains")
        @Schema(description = "Update list of domains")
        List<String> domainIds)
    implements CommonActionInput {}
