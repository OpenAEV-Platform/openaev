package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class ThreatArsenalActionWithContentOutput extends InjectorContractBaseOutput {
  @Schema(description = "Action implementation payload type")
  @JsonProperty("action_payload_type")
  private String payloadType;

  @Schema(description = "Action implementation injector type")
  @JsonProperty("action_injector_type")
  private String injectorType;

  @Schema(description = "Action injectors names")
  @NotBlank
  @JsonProperty("action_injector_name")
  private String injectorName;

  @Schema(description = "Action display labels")
  @NotBlank
  @JsonProperty("action_labels")
  private Map<String, String> labels;

  @Schema(description = "CPU architecture targeted for action execution")
  @NotNull
  @JsonProperty("action_arch")
  private Payload.PAYLOAD_EXECUTION_ARCH executionArch;

  @Schema(description = "Supported endpoint platforms for this action")
  @JsonProperty("action_platforms")
  private Endpoint.PLATFORM_TYPE[] platforms;

  @Schema(description = "Action content")
  @JsonProperty("action_content")
  private String content;

  public ThreatArsenalActionWithContentOutput(
      String id,
      String payloadType,
      String injectorType,
      String injectorName,
      Map<String, String> labels,
      Payload.PAYLOAD_EXECUTION_ARCH executionArch,
      Endpoint.PLATFORM_TYPE[] platforms,
      String content) {
    super(id, null, null);
    this.payloadType = payloadType;
    this.injectorType = injectorType;
    this.injectorName = injectorName;
    this.labels = labels;
    this.executionArch = executionArch;
    this.platforms = platforms;
    this.content = content;
  }
}
