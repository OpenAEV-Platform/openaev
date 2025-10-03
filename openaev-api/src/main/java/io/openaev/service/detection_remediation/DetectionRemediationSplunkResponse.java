package io.openaev.service.detection_remediation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Response containing the SPL query detection rule for Splunk")
public class DetectionRemediationSplunkResponse implements DetectionRemediationAIResponse {

  @Schema(description = "Indicates whether the request was successful", example = "true")
  Boolean success;

  @JsonProperty("spl_query")
  @Schema(
      description = "SPL query to be used in Splunk for threat detection",
      example =
          "index=windows EventCode=4688 CommandLine=\"*Invoke-WebRequest*\" CommandLine=\"*AnyDesk*\" | stats count by Computer, User, CommandLine | sort -count")
  String splQuery;

  @Schema(
      description = "Informational message about the operation result",
      example = "SPL query generated successfully")
  String message;

  public String formateRules() {
    return splQuery;
  }
}
