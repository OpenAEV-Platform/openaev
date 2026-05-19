package io.openaev.rest.payload.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayloadOutput {

  @Schema(description = "Payload unique identifier")
  @NotBlank
  @JsonProperty("payload_id")
  private String id;

  @Schema(description = "Payload implementation type")
  @JsonProperty("payload_type")
  private String type;

  @Schema(description = "Payload display name")
  @NotBlank
  @JsonProperty("payload_name")
  private String name;

  @Schema(description = "Payload description")
  @JsonProperty("payload_description")
  private String description;

  @Schema(description = "Supported endpoint platforms for this payload")
  @JsonProperty("payload_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @Schema(description = "Executor used for cleanup operations")
  @JsonProperty("payload_cleanup_executor")
  private String cleanupExecutor;

  @Schema(description = "Cleanup command executed after payload run")
  @JsonProperty("payload_cleanup_command")
  private String cleanupCommand;

  @Schema(description = "Payload input arguments definition")
  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments;

  @Schema(description = "Prerequisites required before payload execution")
  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites;

  @Schema(description = "External reference identifier")
  @JsonProperty("payload_external_id")
  private String externalId;

  @Schema(description = "Payload source origin")
  @JsonProperty("payload_source")
  @NotNull
  private Payload.PAYLOAD_SOURCE source;

  @Schema(description = "Expected output types for payload execution")
  @JsonProperty("payload_expectations")
  private InjectExpectation.EXPECTATION_TYPE[] expectations;

  @Schema(description = "Current payload lifecycle status")
  @JsonProperty("payload_status")
  @NotNull
  private Payload.PAYLOAD_STATUS status;

  @Schema(description = "CPU architecture targeted for payload execution")
  @NotNull
  @JsonProperty("payload_execution_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH executionArch;

  @Schema(description = "Collector type associated with this payload")
  @JsonProperty("payload_collector_type")
  private String collectorType;

  @Schema(description = "Detection and remediation mappings for this payload")
  @JsonProperty("payload_detection_remediations")
  private List<DetectionRemediation> detectionRemediations;

  @Schema(description = "Parsers used to process payload outputs")
  @JsonProperty("payload_output_parsers")
  private Set<OutputParser> outputParsers;

  @Schema(description = "Tags attached to the payload")
  @JsonProperty("payload_tags")
  private List<String> tags;

  @Schema(description = "Domains related to the payload")
  @JsonProperty("payload_domains")
  private List<String> domains;

  @Schema(description = "MITRE ATT&CK patterns associated with the payload")
  @JsonProperty("payload_attack_patterns")
  private List<String> attackPatterns;

  @Schema(description = "Executor used for command payloads")
  @JsonProperty("command_executor")
  private String commandExecutor;

  @Schema(description = "Command content for command payloads")
  @JsonProperty("command_content")
  private String commandContent;

  @Schema(description = "Hostname resolved by DNS resolution payloads")
  @JsonProperty("dns_resolution_hostname")
  private String dnsResolutionHostname;

  @Schema(description = "Dropped file path for file-drop payloads")
  @JsonProperty("file_drop_file")
  private String fileDropFile;

  @Schema(description = "Executable file path for executable payloads")
  @JsonProperty("executable_file")
  private String executableFile;

  @Schema(description = "Payload creation timestamp")
  @JsonProperty("payload_created_at")
  @NotNull
  private Instant createdAt;

  @Schema(description = "Payload last update timestamp")
  @JsonProperty("payload_updated_at")
  @NotNull
  private Instant updatedAt;
}
