package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.raw.RawTeam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TeamSimple {

  @JsonProperty("team_id")
  @NotBlank
  @Schema(description = "ID of the team")
  private String id;

  @NotBlank
  @JsonProperty("team_name")
  @Schema(description = "Name of the team")
  private String name;

  @JsonProperty("team_description")
  @Schema(description = "Description of the team")
  private String description;

  @JsonProperty("team_created_at")
  @Schema(description = "Creation date of the team", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt = now();

  @JsonProperty("team_updated_at")
  @Schema(description = "Update date of the team", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt = now();

  @JsonProperty("team_tags")
  @Schema(description = "List of tag IDs of the team")
  private Set<String> tags;

  @JsonProperty("team_organization")
  @Schema(description = "Organization ID of the team")
  private String organization;

  @JsonProperty("team_users")
  @Schema(description = "List of user IDs of the team")
  private Set<String> users;

  @JsonProperty("team_exercises")
  @Schema(description = "List of simulation IDs of the team")
  private Set<String> exercises;

  @JsonProperty("team_scenarios")
  @Schema(description = "List of scenario IDs of the team")
  private Set<String> scenarios;

  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual;

  @JsonProperty("team_exercises_users")
  @Schema(description = "List of 3-tuple linking simulation IDs and user IDs to this team ID")
  private Set<ExerciseTeamUser> exerciseTeamUsers = new HashSet<>();

  @JsonProperty("team_users_number")
  @Schema(description = "Number of users of the team")
  public long getUsersNumber() {
    return this.users.size();
  }

  // region transient
  @JsonProperty("team_exercise_injects")
  @Schema(description = "List of inject IDs from all simulations of the team")
  private Set<String> exercisesInjects;

  @JsonProperty("team_exercise_injects_number")
  @Schema(description = "Number of injects of all simulations of the team")
  public long getExercisesInjectsNumber() {
    return this.exercisesInjects.size();
  }

  @JsonProperty("team_scenario_injects")
  @Schema(description = "List of inject IDs from all scenarios of the team")
  Set<String> scenariosInjects = new HashSet<>();

  @JsonProperty("team_scenario_injects_number")
  @Schema(description = "Number of injects of all scenarios of the team")
  public long getScenariosInjectsNumber() {
    return this.scenariosInjects.size();
  }

  @JsonIgnore private List<InjectExpectation> injectExpectations = new ArrayList<>();

  @JsonProperty("team_inject_expectations")
  @Schema(description = "List of expectation ids linked to this team")
  private Set<String> getInjectExpectationsAsStringList() {
    return getInjectExpectations().stream()
        .map(InjectExpectation::getId)
        .collect(Collectors.toSet());
  }

  @JsonProperty("team_injects_expectations_number")
  @Schema(description = "Number of expectations linked to this team")
  public long getInjectExpectationsNumber() {
    return getInjectExpectations().size();
  }

  @JsonProperty("team_injects_expectations_total_score")
  @NotNull
  @Schema(description = "Total score of expectations linked to this team")
  public double getInjectExpectationsTotalScore() {
    return getInjectExpectations().stream()
        .filter((inject) -> inject.getScore() != null)
        .mapToDouble(InjectExpectation::getScore)
        .sum();
  }

  @JsonProperty("team_injects_expectations_total_score_by_exercise")
  @NotNull
  @Schema(description = "Total score of expectations by simulation linked to this team")
  public Map<String, Double> getInjectExpectationsTotalScoreByExercise() {
    return getInjectExpectations().stream()
        .filter(
            expectation ->
                Objects.nonNull(expectation.getExercise())
                    && Objects.nonNull(expectation.getScore()))
        .collect(
            Collectors.groupingBy(
                expectation -> expectation.getExercise().getId(),
                Collectors.summingDouble(InjectExpectation::getScore)));
  }

  @JsonProperty("team_injects_expectations_total_expected_score")
  @NotNull
  @Schema(description = "Total expected score of expectations linked to this team")
  public double getInjectExpectationsTotalExpectedScore() {
    return getInjectExpectations().stream()
        .filter(expectation -> Objects.nonNull(expectation.getExpectedScore()))
        .mapToDouble(InjectExpectation::getExpectedScore)
        .sum();
  }

  @JsonProperty("team_injects_expectations_total_expected_score_by_exercise")
  @NotNull
  @Schema(description = "Total expected score of expectations by simulation linked to this team")
  public Map<String, Double> getInjectExpectationsTotalExpectedScoreByExercise() {
    return getInjectExpectations().stream()
        .filter(expectation -> Objects.nonNull(expectation.getExercise()))
        .collect(
            Collectors.groupingBy(
                expectation -> expectation.getExercise().getId(),
                Collectors.summingDouble(InjectExpectation::getExpectedScore)));
  }

  // endregion

  @JsonProperty("team_communications")
  @Schema(description = "List of communications of this team")
  List<Communication> communications = new ArrayList<>();

  public TeamSimple(RawTeam raw) {
    super();
    this.id = raw.getTeam_id();
    this.scenarios = Optional.ofNullable(raw.getTeam_scenarios()).orElse(new HashSet<>());
    this.exercisesInjects =
        Optional.ofNullable(raw.getTeam_exercise_injects()).orElse(new HashSet<>());
    this.contextual = raw.getTeam_contextual();
    this.exercises = Optional.ofNullable(raw.getTeam_exercises()).orElse(new HashSet<>());
    this.createdAt = raw.getTeam_created_at();
    this.description = raw.getTeam_description();
    this.updatedAt = raw.getTeam_updated_at();
    this.name = raw.getTeam_name();
    this.organization = raw.getTeam_organization();
    this.users = raw.getTeam_users();
    this.tags = raw.getTeam_tags();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TeamSimple that = (TeamSimple) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
