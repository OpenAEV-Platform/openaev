package io.openaev.rest.finding.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * Group-wide summary of a finding, deduplicated across every occurrence sharing the same (type,
 * value) in the tenant. Unlike a single {@code Finding} row (one occurrence per inject), the dates
 * and counts here span the whole group, so the finding overview hero shows the true first/last seen
 * and the real impact spread (assets, teams, persons, asset groups).
 */
@Data
@Builder
@JsonInclude(NON_NULL)
public class FindingSummaryOutput {

  @Schema(description = "Representative finding id used to resolve the (type, value) group")
  @JsonProperty("finding_id")
  private String id;

  @Schema(description = "Finding type")
  @JsonProperty("finding_type")
  @NotNull
  private ContractOutputType type;

  @Schema(description = "Finding value, redacted when the finding is sensitive")
  @JsonProperty("finding_value")
  private String value;

  @Schema(description = "Whether the finding holds sensitive material, hence a redacted value")
  @JsonProperty("finding_is_sensitive")
  private boolean sensitive;

  @Schema(description = "First time this finding was seen across all occurrences")
  @JsonProperty("finding_first_seen")
  private Instant firstSeen;

  @Schema(description = "Last time this finding was seen across all occurrences")
  @JsonProperty("finding_last_seen")
  private Instant lastSeen;

  @Schema(description = "Number of occurrences (one per inject that produced this finding)")
  @JsonProperty("finding_occurrences")
  private long occurrences;

  @Schema(description = "Number of distinct impacted assets across all occurrences")
  @JsonProperty("finding_assets_count")
  private long assetsCount;

  @Schema(description = "Number of distinct impacted teams across all occurrences")
  @JsonProperty("finding_teams_count")
  private long teamsCount;

  @Schema(description = "Number of distinct impacted persons across all occurrences")
  @JsonProperty("finding_users_count")
  private long usersCount;

  @Schema(description = "Number of distinct impacted asset groups across all occurrences")
  @JsonProperty("finding_asset_groups_count")
  private long assetGroupsCount;
}
