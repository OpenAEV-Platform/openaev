package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "CWE input used in CVE creation/update")
public class CweInput {

  @NotBlank
  @JsonProperty("cwe_external_id")
  @Schema(description = "External CWE identifier", example = "CWE-79")
  private String externalId;

  @JsonProperty("cwe_source")
  @Schema(description = "Source of the CWE", example = "NIST")
  private String source;
}
