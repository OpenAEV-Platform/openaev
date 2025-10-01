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

@Getter
@Setter
@Schema(description = "Common base input for CVE creation and update")
public class CveInput {

  @JsonProperty("cve_source_identifier")
  @Schema(description = "Identifier of the CVE source", example = "MITRE")
  private String sourceIdentifier;

  @JsonProperty("cve_published")
  @Schema(description = "Publication date of the CVE")
  private Instant published;

  @JsonProperty("cve_description")
  @Schema(description = "Description of the CVE")
  private String description;

  @Enumerated(EnumType.STRING)
  @JsonProperty("cve_vuln_status")
  @Schema(description = "Vulnerability status", example = "ANALYZED")
  private Cve.VulnerabilityStatus vulnStatus;

  @JsonProperty("cve_cisa_exploit_add")
  @Schema(description = "Date when CISA added the CVE to the exploited list")
  private Instant cisaExploitAdd;

  @JsonProperty("cve_cisa_action_due")
  @Schema(description = "Date when action is due by CISA")
  private Instant cisaActionDue;

  @JsonProperty("cve_cisa_required_action")
  @Schema(description = "Action required by CISA")
  private String cisaRequiredAction;

  @JsonProperty("cve_cisa_vulnerability_name")
  @Schema(description = "Vulnerability name used by CISA")
  private String cisaVulnerabilityName;

  @JsonProperty("cve_remediation")
  @Schema(description = "Suggested remediation")
  private String remediation;

  @JsonProperty("cve_reference_urls")
  @Schema(description = "List of reference URLs")
  private List<String> referenceUrls;

  @JsonProperty("cve_cwes")
  @Schema(description = "List of linked CWEs")
  private List<CweInput> cwes;
}
