package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.raw.RawExerciseTeamUser;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "exercises_teams_users")
public class ExerciseTeamUser {
  @EmbeddedId @JsonIgnore private ExerciseTeamUserId compositeId = new ExerciseTeamUserId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("exerciseId")
  @JoinColumn(name = "exercise_id")
  @JsonProperty("exercise_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Exercise exercise;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("teamId")
  @JoinColumn(name = "team_id")
  @JsonProperty("team_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Team team;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("userId")
  @JoinColumn(name = "user_id")
  @JsonProperty("user_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private User user;

  public ExerciseTeamUserId getCompositeId() {
    return compositeId;
  }

  public void setCompositeId(ExerciseTeamUserId compositeId) {
    this.compositeId = compositeId;
  }

  public Exercise getExercise() {
    return exercise;
  }

  public void setExercise(Exercise exercise) {
    this.exercise = exercise;
  }

  public Team getTeam() {
    return team;
  }

  public void setTeam(Team team) {
    this.team = team;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExerciseTeamUser that = (ExerciseTeamUser) o;
    return compositeId.equals(that.compositeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }

  public static ExerciseTeamUser fromRawExerciseTeamUser(RawExerciseTeamUser rawExerciseTeamUser) {
    ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
    exerciseTeamUser.setTeam(new Team());
    exerciseTeamUser.getTeam().setId(rawExerciseTeamUser.getTeam_id());
    exerciseTeamUser.setExercise(new Exercise());
    exerciseTeamUser.getExercise().setId(rawExerciseTeamUser.getExercise_id());
    exerciseTeamUser.setUser(new User());
    exerciseTeamUser.getUser().setId(rawExerciseTeamUser.getUser_id());
    exerciseTeamUser.setCompositeId(new ExerciseTeamUserId());
    exerciseTeamUser.getCompositeId().setExerciseId(rawExerciseTeamUser.getExercise_id());
    exerciseTeamUser.getCompositeId().setTeamId(rawExerciseTeamUser.getTeam_id());
    exerciseTeamUser.getCompositeId().setUserId(rawExerciseTeamUser.getUser_id());
    return exerciseTeamUser;
  }
}
