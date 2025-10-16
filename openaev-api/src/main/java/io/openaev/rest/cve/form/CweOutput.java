package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
@Schema(description = "CWE output data")
public class CweOutput {

  @NotBlank
  @JsonProperty("cwe_external_id")
  @Schema(description = "CWE identifier", example = "CWE-79")
  private String externalId;

  @JsonProperty("cwe_source")
  @Schema(description = "Source of the CWE")
  private String source;
}
