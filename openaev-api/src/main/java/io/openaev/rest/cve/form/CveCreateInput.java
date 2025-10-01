package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Payload to create a CVE")
public class CveCreateInput extends CveInput {

  @NotBlank
  @JsonProperty("cve_external_id")
  @Schema(description = "External Unique CVE identifier", example = "CVE-2024-0001")
  private String externalId;

  @NotNull
  @DecimalMin("0.0")
  @DecimalMax("10.0")
  @JsonProperty("cve_cvss_v31")
  @Schema(description = "CVSS score", example = "7.5", minimum = "0.0", maximum = "10.0")
  private BigDecimal cvssV31;
}
