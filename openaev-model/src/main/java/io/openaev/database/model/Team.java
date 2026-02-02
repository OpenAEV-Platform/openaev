package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(name = "teams")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(name = "Team.tags", attributeNodes = @NamedAttributeNode("tags"))
})
public class Team implements Base {

  @Id
  @Column(name = "team_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("team_id")
  @NotBlank
  @Schema(description = "ID of the team")
  private String id;

  @Column(name = "team_name")
  @JsonProperty("team_name")
  @Queryable(searchable = true, sortable = true, filterable = true)
  @NotBlank
  @Schema(description = "Name of the team")
  private String name;

  @Queryable(searchable = true, sortable = true)
  @Column(name = "team_description")
  @JsonProperty("team_description")
  @Schema(description = "Description of the team")
  private String description;

  @Column(name = "team_created_at")
  @JsonProperty("team_created_at")
  @NotNull
  @Schema(description = "Creation date of the team", accessMode = Schema.AccessMode.READ_ONLY)
  @CreationTimestamp
  private Instant createdAt = now();

  @Queryable(sortable = true)
  @Column(name = "team_updated_at")
  @JsonProperty("team_updated_at")
  @NotNull
  @Schema(description = "Update date of the team", accessMode = Schema.AccessMode.READ_ONLY)
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ArraySchema(schema = @Schema(description = "IDs of the tags of the team", type = "string"))
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "teams_tags",
      joinColumns = @JoinColumn(name = "team_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("team_tags")
  private Set<Tag> tags = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_organization")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("team_organization")
  @Schema(description = "Organization of the team", type = "string")
  private Organization organization;

  @ArraySchema(schema = @Schema(description = "IDs of the users of the team", type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "users_teams",
      joinColumns = @JoinColumn(name = "team_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("team_users")
  private List<User> users = new ArrayList<>();

  @ArraySchema(
      schema = @Schema(description = "IDs of the simulations linked to the team", type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "exercises_teams",
      joinColumns = @JoinColumn(name = "team_id"),
      inverseJoinColumns = @JoinColumn(name = "exercise_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("team_exercises")
  private List<Exercise> exercises = new ArrayList<>();

  @ArraySchema(
      schema = @Schema(description = "IDs of the scenarios linked to the team", type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_teams",
      joinColumns = @JoinColumn(name = "team_id"),
      inverseJoinColumns = @JoinColumn(name = "scenario_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("team_scenarios")
  private List<Scenario> scenarios = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "injects_teams",
      joinColumns = @JoinColumn(name = "team_id"),
      inverseJoinColumns = @JoinColumn(name = "inject_id"))
  @JsonProperty("team_injects")
  @JsonIgnore
  @Queryable(filterable = true, dynamicValues = true, path = "injects.id")
  private List<Inject> injects = new ArrayList<>();

  @Column(name = "team_contextual")
  @JsonProperty("team_contextual")
  @Schema(
      description =
          "True if the team is contextual (exists only in the scenario/simulation it is linked to)")
  private Boolean contextual = false;

  @Transient private final ResourceType resourceType = ResourceType.TEAM;

  @ArraySchema(
      schema =
          @Schema(
              description = "List of 3-tuple linking simulation IDs and user IDs to this team ID",
              type = "string"))
  @OneToMany(
      mappedBy = "team",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("team_exercises_users")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<ExerciseTeamUser> exerciseTeamUsers = new ArrayList<>();

  @JsonProperty("team_users_number")
  @Schema(description = "Number of users of the team")
  public long getUsersNumber() {
    return getUsers().size();
  }

  // region transient
  @ArraySchema(
      schema =
          @Schema(
              description = "List of inject IDs from all simulations of the team",
              type = "string"))
  @JsonProperty("team_exercise_injects")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<Inject> getExercisesInjects() {
    Predicate<Inject> selectedInject =
        inject -> inject.isAllTeams() || inject.getTeams().contains(this);
    return getExercises().stream()
        .map(exercise -> exercise.getInjects().stream().filter(selectedInject).distinct().toList())
        .flatMap(List::stream)
        .toList();
  }

  @JsonProperty("team_exercise_injects_number")
  @Schema(description = "Number of injects of all simulations of the team")
  public long getExercisesInjectsNumber() {
    return getExercisesInjects().size();
  }

  @ArraySchema(
      schema =
          @Schema(
              description = "List of inject IDs from all scenarios of the team",
              type = "string"))
  @JsonProperty("team_scenario_injects")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<Inject> getScenariosInjects() {
    Predicate<Inject> selectedInject =
        inject -> inject.isAllTeams() || inject.getTeams().contains(this);
    return getScenarios().stream()
        .map(scenario -> scenario.getInjects().stream().filter(selectedInject).distinct().toList())
        .flatMap(List::stream)
        .toList();
  }

  @JsonProperty("team_scenario_injects_number")
  @Schema(description = "Number of injects of all scenarios of the team")
  public long getScenariosInjectsNumber() {
    return getScenariosInjects().size();
  }

  @ArraySchema(
      schema =
          @Schema(description = "List of expectations id linked to this team", type = "string"))
  @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("team_inject_expectations")
  private List<InjectExpectation> injectExpectations = new ArrayList<>();

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
  public List<Communication> getCommunications() {
    return getExercisesInjects().stream()
        .flatMap(inject -> inject.getCommunications().stream())
        .distinct()
        .toList();
  }

  public long getUsersNumberInExercise(String exerciseId) {
    return exerciseId == null
        ? 0
        : getExerciseTeamUsers().stream()
            .filter(exerciseTeamUser -> exerciseTeamUser.getExercise().getId().equals(exerciseId))
            .toList()
            .size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
