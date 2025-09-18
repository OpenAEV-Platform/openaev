package io.openbas.rest.payload.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class DetectionRemediationOutput {

  @JsonProperty("detection_remediation_id")
  private String id;

  @JsonProperty("detection_remediation_collector")
  @Schema(description = "Collector type")
  @NotNull
  private String collectorType;

  @JsonProperty("detection_remediation_payload")
  @Schema(description = "Payload id")
  @NotNull
  private String payloadId;

  @JsonProperty("detection_remediation_values")
  @Schema(description = "Value of detection remediation, for exemple: query for sentinel")
  @NotNull
  private String values;

  @JsonProperty("detection_remediation_ai_rule_creation_date")
  @Schema(description = "AI rule creation date. If null, the rule was manually created. " +
          "If a value is present, it indicates when the AI generated this rule, " +
          "allowing comparison with the last update date to identify post-generation modifications.")
  private Instant aiRuleCreationDate;
}
