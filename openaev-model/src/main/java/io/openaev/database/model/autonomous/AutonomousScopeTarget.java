package io.openaev.database.model.autonomous;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry of an autonomous run's scope: a single targetable entity, tagged by kind. The scope is
 * a heterogeneous list of the scope kinds an OpenAEV inject can target. {@code type} uses the
 * {@code io.openaev.utils.TargetType} vocabulary ({@code ASSETS}, {@code ASSETS_GROUPS}, {@code
 * TEAMS}); {@code id} is the entity id of that kind (asset id / asset-group id / team id). Humans
 * are targeted by placing their team in scope, never as a bare person.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "One targetable entity in an autonomous run's scope")
public class AutonomousScopeTarget {

  @JsonProperty("type")
  @Schema(description = "Target kind: ASSETS, ASSETS_GROUPS or TEAMS")
  private String type;

  @JsonProperty("id")
  @Schema(description = "Entity id of that kind (asset / asset-group / team id)")
  private String id;

  public AutonomousScopeTarget(String type, String id) {
    this.type = type;
    this.id = id;
  }
}
