package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@Schema(description = "Simplified CVE representation")
public class CveSimple {

  @NotBlank
  @JsonProperty("cve_id")
  @Schema(description = "Id")
  private String id;

  @NotBlank
  @JsonProperty("cve_external_id")
  @Schema(description = "External CVE identifier", example = "CVE-2024-0001")
  private String externalId;

  @NotNull
  @JsonProperty("cve_cvss_v31")
  @Schema(description = "CVSS score", example = "7.8")
  private BigDecimal cvssV31;

  @JsonProperty("cve_published")
  @Schema(description = "CVE published date")
  private Instant published;
}
