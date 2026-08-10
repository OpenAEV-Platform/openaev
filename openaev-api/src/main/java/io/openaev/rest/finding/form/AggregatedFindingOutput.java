package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.injector.output.InjectorSimple;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class AggregatedFindingOutput {

  @Schema(description = "Finding Id")
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials, cve")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(description = "Finding Value")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "First time the finding was seen")
  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate;

  @Schema(description = "Last time the finding was seen")
  @JsonProperty("finding_updated_at")
  @NotNull
  private Instant updateDate;

  @Schema(description = "Assets linked to the finding (any asset type, not only endpoints)")
  @JsonProperty("finding_assets")
  @NotNull
  private Set<EndpointSimple> assets;

  @Schema(description = "Asset groups linked to assets")
  @JsonProperty("finding_asset_groups")
  private Set<AssetGroupSimple> assetGroups;

  @Schema(
      description =
          "Current triage status of the finding (UNTRIAGED if no triage decision has been made"
              + " yet)")
  @JsonProperty("finding_triage_status")
  @NotNull
  private FindingTriageStatus findingTriageStatus;

  @Schema(
      description =
          "Injector that produced this finding (null if the finding was created manually, e.g."
              + " via the API, without a real inject/injector behind it)")
  @JsonProperty("finding_source")
  private InjectorSimple source;

  // -- CLOUD MISCONFIGURATION (OCSF) -- all null unless populated by OCSFOutputProcessor; see
  // Finding's cloud misconfiguration fields for the source OCSF mapping.

  @Schema(description = "Severity of the cloud misconfiguration (OCSF findings only)")
  @JsonProperty("finding_severity")
  private String severity;

  @Schema(
      description = "Scanned cloud resource identifier, e.g. an S3 bucket ARN (OCSF findings only)")
  @JsonProperty("finding_resource")
  private String resource;

  @Schema(description = "Cloud account identifier the resource belongs to (OCSF findings only)")
  @JsonProperty("finding_cloud_account")
  private String cloudAccount;

  @Schema(description = "Cloud region of the resource (OCSF findings only)")
  @JsonProperty("finding_cloud_region")
  private String cloudRegion;

  @Schema(description = "Remediation guidance for the cloud misconfiguration (OCSF findings only)")
  @JsonProperty("finding_remediation")
  private String remediation;

  @Schema(description = "Comma-joined violated compliance requirements (OCSF findings only)")
  @JsonProperty("finding_compliance")
  private String compliance;
}
