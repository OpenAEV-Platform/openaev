package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Single finding representation returned by the finding CRUD endpoints. It exists so the API never
 * serializes the {@link io.openaev.database.model.Finding} entity directly: the value carried here
 * is the redacted one when the finding is sensitive, while the database row keeps the cleartext.
 */
@Data
@SuperBuilder
@JsonInclude(NON_NULL)
public class FindingOutput {

  @Schema(description = "Finding Id")
  @JsonProperty("finding_id")
  @NotBlank
  private String id;

  @Schema(description = "Contract output field the finding was extracted from")
  @JsonProperty("finding_field")
  @NotBlank
  private String field;

  @Schema(
      description = "Represents the data type being extracted.",
      example = "text, number, port, portscan, ipv4, ipv6, credentials, cve")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(
      description =
          "Finding value. Redacted when the finding is sensitive: the API never discloses the"
              + " cleartext value of a sensitive finding.")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "Whether the finding holds sensitive material, hence a redacted value")
  @JsonProperty("finding_is_sensitive")
  private boolean sensitive;

  @Deprecated
  @Schema(description = "Deprecated, kept for backward compatibility")
  @JsonProperty("finding_labels")
  private String[] labels;

  @Schema(description = "Finding name")
  @JsonProperty("finding_name")
  private String name;

  @Schema(description = "Tag ids linked to the finding")
  @JsonProperty("finding_tags")
  private Set<String> tags;

  @Schema(description = "Inject that produced the finding")
  @JsonProperty("finding_inject_id")
  private String injectId;

  @Schema(description = "First time the finding was seen")
  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate;

  @Schema(description = "Last time the finding was seen")
  @JsonProperty("finding_updated_at")
  @NotNull
  private Instant updateDate;

  @Schema(description = "Asset ids linked to the finding")
  @JsonProperty("finding_assets")
  private List<String> assets;

  @Schema(description = "Team ids linked to the finding")
  @JsonProperty("finding_teams")
  private List<String> teams;

  @Schema(description = "User ids linked to the finding")
  @JsonProperty("finding_users")
  private List<String> users;

  @Schema(description = "Simulation the finding was produced in")
  @JsonProperty("finding_simulation")
  private Exercise simulation;

  @Schema(description = "Scenario the finding was produced in")
  @JsonProperty("finding_scenario")
  private Scenario scenario;

  @Schema(description = "Asset groups targeted by the inject that produced the finding")
  @JsonProperty("finding_asset_groups")
  private Set<AssetGroup> assetGroups;
}
