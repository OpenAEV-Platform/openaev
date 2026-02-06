package io.openaev.rest.payload.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Data
public class PayloadUpsertInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_name")
  private String name;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_source")
  private Payload.PAYLOAD_SOURCE source;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_status")
  private Payload.PAYLOAD_STATUS status;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_external_id")
  private String externalId;

  @JsonProperty("payload_collector")
  private String collector;

  @JsonProperty("payload_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @JsonProperty("payload_execution_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH executionArch =
      Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES;

  @JsonProperty("payload_expectations")
  @NotNull
  private InjectExpectation.EXPECTATION_TYPE[] expectations =
      new InjectExpectation.EXPECTATION_TYPE[] {
        InjectExpectation.EXPECTATION_TYPE.PREVENTION, InjectExpectation.EXPECTATION_TYPE.DETECTION
      };

  @JsonProperty("payload_description")
  private String description;

  @JsonProperty("command_executor")
  @Schema(nullable = true)
  private String executor;

  @JsonProperty("command_content")
  @Schema(nullable = true)
  private String content;

  @JsonProperty("executable_file")
  private String executableFile;

  @JsonProperty("file_drop_file")
  private String fileDropFile;

  @JsonProperty("dns_resolution_hostname")
  private String hostname;

  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments;

  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites;

  @JsonProperty("payload_cleanup_executor")
  @Schema(nullable = true)
  private String cleanupExecutor;

  @JsonProperty("payload_cleanup_command")
  @Schema(nullable = true)
  private String cleanupCommand;

  @JsonProperty("payload_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("payload_attack_patterns")
  private List<String> attackPatternsExternalIds = new ArrayList<>();

  @JsonProperty("payload_detection_remediations")
  @Schema(description = "List of detection remediation gaps for collectors")
  private List<DetectionRemediationInput> detectionRemediations = new ArrayList<>();

  @JsonProperty("payload_elevation_required")
  private boolean elevationRequired;

  @JsonProperty("payload_output_parsers")
  @Schema(description = "Set of output parsers")
  private Set<OutputParserInput> outputParsers = new HashSet<>();

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_domains")
  @Schema(description = "Update list of domains")
  private Set<InjectorContractDomainDTO> domains = new HashSet<>();
}
