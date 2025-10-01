package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "scenarios_teams_users")
public class ScenarioTeamUser {

  @EmbeddedId @JsonIgnore private ScenarioTeamUserId compositeId = new ScenarioTeamUserId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("scenarioId")
  @JoinColumn(name = "scenario_id")
  @JsonProperty("scenario_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string")
  private Scenario scenario;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("teamId")
  @JoinColumn(name = "team_id")
  @JsonProperty("team_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string")
  private Team team;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  @JsonProperty("user_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @Schema(type = "string")
  private User user;
}
