package io.openaev.rest.attack_pattern.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AttackPattern;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Attack pattern as returned by the read endpoints")
public record AttackPatternOutput(
    @JsonProperty("attack_pattern_id") @Schema(description = "Id of the attack pattern") String id,
    @JsonProperty("attack_pattern_stix_id") @Schema(description = "STIX id") String stixId,
    @JsonProperty("attack_pattern_external_id")
        @Schema(description = "External id, e.g. the MITRE technique id")
        String externalId,
    @JsonProperty("attack_pattern_name") @Schema(description = "Name of the attack pattern")
        String name,
    @JsonProperty("attack_pattern_description")
        @Schema(description = "Description of the attack pattern")
        String description,
    @JsonProperty("attack_pattern_platforms")
        @ArraySchema(schema = @Schema(description = "Targeted platforms"))
        String[] platforms,
    @JsonProperty("attack_pattern_permissions_required")
        @ArraySchema(schema = @Schema(description = "Permissions the technique requires"))
        String[] permissionsRequired,
    @JsonProperty("attack_pattern_parent") @Schema(description = "Id of the parent attack pattern")
        String parent,
    @JsonProperty("attack_pattern_created_at") @Schema(description = "Creation date")
        Instant createdAt,
    @JsonProperty("attack_pattern_updated_at") @Schema(description = "Last update date")
        Instant updatedAt,
    @JsonProperty("attack_pattern_kill_chain_phases")
        @ArraySchema(schema = @Schema(description = "Ids of the linked kill chain phases"))
        List<String> killChainPhases) {

  public static AttackPatternOutput from(
      AttackPattern attackPattern, List<String> killChainPhaseIds) {
    return new AttackPatternOutput(
        attackPattern.getId(),
        attackPattern.getStixId(),
        attackPattern.getExternalId(),
        attackPattern.getName(),
        attackPattern.getDescription(),
        attackPattern.getPlatforms(),
        attackPattern.getPermissionsRequired(),
        attackPattern.getParent() == null ? null : attackPattern.getParent().getId(),
        attackPattern.getCreatedAt(),
        attackPattern.getUpdatedAt(),
        killChainPhaseIds);
  }
}
