package io.openaev.rest.exercise.form;

import static io.openaev.database.model.ExerciseStatus.valueOf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.ExerciseTeamUser;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Scenario.SEVERITY;
import io.openaev.database.raw.RawSimulation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class SimulationDetails {

  @JsonProperty("exercise_id")
  @NotBlank
  private String id;

  @JsonProperty("exercise_name")
  @NotBlank
  private String name;

  @JsonProperty("exercise_description")
  private String description;

  @JsonProperty("exercise_status")
  @NotNull
  private ExerciseStatus status;

  @JsonProperty("exercise_subtitle")
  private String subtitle;

  @JsonProperty("exercise_category")
  private String category;

  @JsonProperty("exercise_main_focus")
  private String mainFocus;

  @JsonProperty("exercise_severity")
  private SEVERITY severity;

  @JsonProperty("exercise_start_date")
  private Instant start;

  @JsonProperty("exercise_end_date")
  private Instant end;

  @JsonProperty("exercise_message_header")
  private String header;

  @JsonProperty("exercise_message_footer")
  private String footer;

  @JsonProperty("exercise_mail_from")
  @NotBlank
  private String from;

  @JsonProperty("exercise_mails_reply_to")
  private List<String> replyTo;

  @JsonProperty("exercise_lessons_anonymized")
  private boolean lessonsAnonymized;

  // -- SCENARIO --

  @JsonProperty("exercise_scenario")
  private String scenario;

  // -- AUDIT --

  @JsonProperty("exercise_created_at")
  private Instant createAt;

  @JsonProperty("exercise_updated_at")
  private Instant updatedAt;

  // -- RELATION --

  @JsonProperty("exercise_teams_users")
  private Set<ExerciseTeamUser> exerciseTeamUsers;

  @JsonProperty("exercise_tags")
  private Set<String> tags = new HashSet<>();

  @JsonProperty("exercise_users")
  private Set<String> users = new HashSet<>();

  @JsonProperty("exercise_observers")
  private Set<String> observers = new HashSet<>();

  @JsonProperty("exercise_lessons_answers_number")
  private long lessonsAnswersNumber;

  @JsonProperty("exercise_planners")
  private Set<String> planners = new HashSet<>();

  @JsonProperty("exercise_all_users_number")
  private long allUsersNumber;

  @JsonProperty("exercise_users_number")
  private long usersNumber;

  @JsonProperty("exercise_logs_number")
  private long logsNumber;

  @JsonProperty("exercise_communications_number")
  public long communicationsNumber;

  @JsonProperty("exercise_custom_dashboard")
  private String customDashboard;

  // -- PLATFORMS --

  @JsonProperty("exercise_platforms")
  public List<String> platforms;

  // -- KILL CHAIN PHASES --

  @JsonProperty("exercise_kill_chain_phases")
  public List<KillChainPhase> killChainPhases;

  @JsonProperty("exercise_score")
  public Double getEvaluationAverage() {
    double evaluationAverage =
        getObjectives().stream().mapToDouble(Objective::getEvaluationAverage).average().orElse(0D);
    return Math.round(evaluationAverage * 100.0) / 100.0;
  }

  @JsonIgnore private List<Objective> objectives;

  /**
   * Create an Exercise Details object different from the one used in the lists from a Raw one
   *
   * @param exercise the raw exercise
   * @return an Exercise Simple object
   */
  public static SimulationDetails fromRawExercise(
      RawSimulation exercise,
      List<ExerciseTeamUser> exerciseTeamsUsers,
      List<Objective> objectives) {
    SimulationDetailsBuilder details =
        SimulationDetails.builder()
            .id(exercise.getExercise_id())
            .name(exercise.getExercise_name())
            .description(exercise.getExercise_description())
            .status(valueOf(exercise.getExercise_status()))
            .subtitle(exercise.getExercise_subtitle())
            .category(exercise.getExercise_category())
            .mainFocus(exercise.getExercise_main_focus())
            .customDashboard(exercise.getExercise_custom_dashboard());

    if (exercise.getExercise_severity() != null) {
      details.severity(SEVERITY.valueOf(exercise.getExercise_severity()));
    }
    details
        .start(exercise.getExercise_start_date())
        .end(exercise.getExercise_end_date())
        .header(exercise.getExercise_message_header())
        .footer(exercise.getExercise_message_footer())
        .from(exercise.getExercise_mail_from());
    if (exercise.getExercise_reply_to() != null) {
      details.replyTo(exercise.getExercise_reply_to().stream().toList());
    }
    details
        .lessonsAnonymized(exercise.getExercise_lessons_anonymized())
        .scenario(exercise.getScenario_id())
        .createAt(exercise.getExercise_created_at())
        .updatedAt(exercise.getExercise_updated_at());
    if (exerciseTeamsUsers != null) {
      details
          .exerciseTeamUsers(new HashSet<>(exerciseTeamsUsers))
          .usersNumber(
              exerciseTeamsUsers.stream().map(ExerciseTeamUser::getUser).distinct().count());
    }
    details
        .tags(new HashSet<>(exercise.getExercise_tags()))
        .users(exercise.getExercise_users())
        .objectives(objectives)
        .lessonsAnswersNumber(exercise.getLessons_answers().stream().distinct().toList().size())
        .allUsersNumber(exercise.getExercise_users().stream().distinct().toList().size())
        .logsNumber(exercise.getLogs().stream().distinct().toList().size());

    return details.build();
  }
}
