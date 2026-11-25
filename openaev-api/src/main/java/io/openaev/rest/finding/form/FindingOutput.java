package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingStatus;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.scenario.form.ScenarioSimple;
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
 * is the masked one when the finding type holds secret material, while the database row keeps the
 * cleartext.
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
          "Finding value. Masked when the finding type holds secret material: the API never"
              + " discloses the cleartext value of a sensitive finding.")
  @JsonProperty("finding_value")
  @NotBlank
  private String value;

  @Schema(description = "Finding name")
  @JsonProperty("finding_name")
  private String name;

  @Schema(description = "Tag ids linked to the finding")
  @JsonProperty("finding_tags")
  private Set<String> tags;

  @Schema(description = "Inject that produced the finding")
  @JsonProperty("finding_inject_id")
  private String injectId;

  @Schema(
      description =
          "When the finding row was created. A storage time, not a detection: read"
              + " finding_first_seen for that.")
  @JsonProperty("finding_created_at")
  @NotNull
  private Instant creationDate;

  @Schema(
      description =
          "When the finding row was last written. Any write moves it (status, asset link,"
              + " migration): read finding_last_seen for the last detection.")
  @JsonProperty("finding_updated_at")
  @NotNull
  private Instant updateDate;

  @Schema(
      description =
          "First time the finding was detected; the creation time when the detection time is"
              + " unknown")
  @JsonProperty("finding_first_seen")
  private Instant firstSeen;

  @Schema(
      description =
          "Last time the finding was detected; the last write time when the detection time is"
              + " unknown")
  @JsonProperty("finding_last_seen")
  private Instant lastSeen;

  @Schema(description = "Lifecycle status: OPEN, NOT_REPRODUCED or RESOLVED")
  @JsonProperty("finding_status")
  private FindingStatus status;

  @Schema(description = "When a re-test last ended on the finding")
  @JsonProperty("finding_last_tested")
  private Instant lastTested;

  @Schema(description = "Inject whose run set the current status (a detection or a re-test)")
  @JsonProperty("finding_status_inject_id")
  private String statusInjectId;

  @Schema(description = "Re-test inject queued and not finished yet")
  @JsonProperty("finding_retest_inject_id")
  private String retestInjectId;

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
  private ExerciseSimple simulation;

  @Schema(description = "Scenario the finding was produced in")
  @JsonProperty("finding_scenario")
  private ScenarioSimple scenario;

  @Schema(description = "Asset groups targeted by the inject that produced the finding")
  @JsonProperty("finding_asset_groups")
  private Set<AssetGroupSimple> assetGroups;
}
