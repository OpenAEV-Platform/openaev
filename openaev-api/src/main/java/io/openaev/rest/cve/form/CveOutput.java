package io.openaev.rest.cve.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Cve;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
@Schema(description = "Full CVE output including references and CWEs")
public class CveOutput extends CveSimple {

  @JsonProperty("cve_source_identifier")
  @Schema(description = "Source identifier")
  private String sourceIdentifier;

  @JsonProperty("cve_description")
  @Schema(description = "Detailed CVE description")
  private String description;

  @Enumerated(EnumType.STRING)
  @JsonProperty("cve_vuln_status")
  @Schema(description = "Status of the vulnerability")
  private Cve.VulnerabilityStatus vulnStatus;

  @JsonProperty("cve_cisa_exploit_add")
  @Schema(description = "CISA exploit addition date")
  private Instant cisaExploitAdd;

  @JsonProperty("cve_cisa_action_due")
  @Schema(description = "CISA required action due date")
  private Instant cisaActionDue;

  @JsonProperty("cve_cisa_required_action")
  @Schema(description = "Action required by CISA")
  private String cisaRequiredAction;

  @JsonProperty("cve_cisa_vulnerability_name")
  @Schema(description = "Name used by CISA for the vulnerability")
  private String cisaVulnerabilityName;

  @JsonProperty("cve_remediation")
  @Schema(description = "Remediation suggestions")
  private String remediation;

  @JsonProperty("cve_reference_urls")
  @Schema(description = "External references")
  private List<String> referenceUrls;

  @JsonProperty("cve_cwes")
  @Schema(description = "List of CWE outputs")
  private List<CweOutput> cwes;
}
