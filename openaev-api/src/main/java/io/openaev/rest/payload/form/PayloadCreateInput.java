package io.openaev.rest.payload.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Payload.PAYLOAD_SOURCE;
import io.openaev.database.model.Payload.PAYLOAD_STATUS;
import io.openaev.rest.payload.output_parser.OutputParserInput;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;

@Schema(
    discriminatorProperty = "payload_type",
    oneOf = {
      Command.class,
      Executable.class,
      FileDrop.class,
      DnsResolution.class,
      NetworkTraffic.class
    },
    discriminatorMapping = {
      @DiscriminatorMapping(value = Command.COMMAND_TYPE, schema = Command.class),
      @DiscriminatorMapping(value = Executable.EXECUTABLE_TYPE, schema = Executable.class),
      @DiscriminatorMapping(value = FileDrop.FILE_DROP_TYPE, schema = FileDrop.class),
      @DiscriminatorMapping(
          value = DnsResolution.DNS_RESOLUTION_TYPE,
          schema = DnsResolution.class),
      @DiscriminatorMapping(
          value = NetworkTraffic.NETWORK_TRAFFIC_TYPE,
          schema = NetworkTraffic.class)
    })
@Data
public class PayloadCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_type")
  private String type;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_name")
  private String name;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_source")
  private PAYLOAD_SOURCE source;

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_status")
  private PAYLOAD_STATUS status;

  @NotEmpty(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_platforms")
  private PLATFORM_TYPE[] platforms;

  @JsonProperty("payload_execution_arch")
  @NotNull
  private Payload.PAYLOAD_EXECUTION_ARCH executionArch =
      Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES;

  @JsonProperty("payload_expectations")
  @NotNull
  private EXPECTATION_TYPE[] expectations =
      new EXPECTATION_TYPE[] {EXPECTATION_TYPE.PREVENTION, EXPECTATION_TYPE.DETECTION};

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
  private List<String> attackPatternsIds = new ArrayList<>();

  @JsonProperty("payload_detection_remediations")
  @Schema(description = "List of detection remediation gaps for collectors")
  private List<DetectionRemediationInput> detectionRemediations = new ArrayList<>();

  @JsonProperty("payload_output_parsers")
  @Schema(description = "Set of output parsers")
  private Set<OutputParserInput> outputParsers = new HashSet<>();

  @NotNull(message = MANDATORY_MESSAGE)
  @JsonProperty("payload_domains")
  @Schema(description = "Set list of domains")
  private List<String> domainIds = new ArrayList<>();
}
