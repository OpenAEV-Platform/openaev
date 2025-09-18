package io.openbas.rest.payload.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbas.database.model.DetectionRemediation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

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

  @JsonProperty("detection_remediation_author_rule")
  @Schema(description = "Author of rules: Human or AI ")
  @NotNull
  private DetectionRemediation.AUTHOR_RULE authorRule;
}
