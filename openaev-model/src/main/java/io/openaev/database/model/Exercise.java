package io.openaev.database.model;

import static io.openaev.database.model.Grant.GRANT_TYPE.OBSERVER;
import static io.openaev.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.openaev.helper.UserHelper.getUsersByType;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.Scenario.SEVERITY;
import io.openaev.helper.InjectStatisticsHelper;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Entity
@Table(name = "exercises")
@EntityListeners(ModelBaseListener.class)
@Grantable(Grant.GRANT_RESOURCE_TYPE.SIMULATION)
public class Exercise implements GrantableBase {

  @Getter
  @Id
  @Column(name = "exercise_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("exercise_id")
  @NotBlank
  private String id;

  @Getter
  @Column(name = "exercise_name")
  @JsonProperty("exercise_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Getter
  @Column(name = "exercise_description")
  @JsonProperty("exercise_description")
  private String description;

  @Getter
  @Column(name = "exercise_status")
  @JsonProperty("exercise_status")
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  @NotNull
  private ExerciseStatus status = ExerciseStatus.SCHEDULED;

  @Getter
  @Column(name = "exercise_subtitle")
  @JsonProperty("exercise_subtitle")
  private String subtitle;

  @Getter
  @Column(name = "exercise_category")
  @JsonProperty("exercise_category")
  private String category;

  @Getter
  @Column(name = "exercise_main_focus")
  @JsonProperty("exercise_main_focus")
  private String mainFocus;

  @Getter
  @Column(name = "exercise_severity")
  @Enumerated(EnumType.STRING)
  @JsonProperty("exercise_severity")
  private SEVERITY severity;

  @Column(name = "exercise_pause_date")
  @JsonIgnore
  private Instant currentPause;

  @Column(name = "exercise_start_date")
  @JsonProperty("exercise_start_date")
  @Queryable(filterable = true, sortable = true)
  private Instant start;

  @Column(name = "exercise_launch_order", insertable = false, updatable = false)
  @JsonIgnore
  @Getter
  @Setter(NONE)
  private Long launchOrder;

  @Column(name = "exercise_end_date")
  @JsonProperty("exercise_end_date")
  @Queryable(filterable = true, sortable = true)
  private Instant end;

  @Getter
  @Column(name = "exercise_message_header")
  @JsonProperty("exercise_message_header")
  private String header = "SIMULATION HEADER";

  @Getter
  @Column(name = "exercise_message_footer")
  @JsonProperty("exercise_message_footer")
  private String footer = "SIMULATION FOOTER";

  @Getter
  @Column(name = "exercise_mail_from")
  @JsonProperty("exercise_mail_from")
  @Email
  @NotBlank
  private String from;

  @Getter
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "exercise_mails_reply_to",
      joinColumns = @JoinColumn(name = "exercise_id"))
  @Column(name = "exercise_reply_to", nullable = false)
  @JsonProperty("exercise_mails_reply_to")
  private List<String> replyTos = new ArrayList<>();

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exercise_logo_dark")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("exercise_logo_dark")
  @Schema(type = "string")
  private Document logoDark;

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exercise_logo_light")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("exercise_logo_light")
  @Schema(type = "string")
  private Document logoLight;

  @Getter
  @Column(name = "exercise_lessons_anonymized")
  @JsonProperty("exercise_lessons_anonymized")
  private boolean lessonsAnonymized = false;

  // -- SCENARIO --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_exercises",
      joinColumns = @JoinColumn(name = "exercise_id"),
      inverseJoinColumns = @JoinColumn(name = "scenario_id"))
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("exercise_scenario")
  @Queryable(filterable = true, dynamicValues = true)
  @Schema(type = "string")
  @Setter(NONE)
  private Scenario scenario;

  public void setScenario(Scenario scenario) {
    if (scenario != null) scenario.setUpdatedAt(now());
    this.scenario = scenario;
    this.setUpdatedAt(now());
  }

  // STIX
  @Getter
  @ManyToOne
  @JoinColumn(name = "exercise_security_coverage")
  @JsonIgnore
  private SecurityCoverage securityCoverage;

  // -- AUDIT --

  @Getter
  @Column(name = "exercise_created_at")
  @JsonProperty("exercise_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Getter
  @Column(name = "exercise_updated_at")
  @JsonProperty("exercise_updated_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  // -- RELATION --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "exercise_custom_dashboard")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonProperty("exercise_custom_dashboard")
  @Schema(type = "string")
  private CustomDashboard customDashboard;

  @Getter
  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "exercise_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'SIMULATION'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY)
  @JsonProperty("exercise_injects")
  @JsonSerialize(using = MultiIdListSerializer.class)
  private List<Inject> injects = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setInjects(List<Inject> injects) {
    this.updatedAt = now();
    this.injects = injects;
  }

  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "exercises_teams",
      joinColumns = @JoinColumn(name = "exercise_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("exercise_teams")
  @ArraySchema(schema = @Schema(type = "string"))
  private List<Team> teams = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setTeams(List<Team> teams) {
    this.updatedAt = now();
    this.teams = teams;
  }

  @Getter
  @OneToMany(
      mappedBy = "exercise",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("exercise_teams_users")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<ExerciseTeamUser> teamUsers = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private List<Objective> objectives = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY)
  @JsonIgnore
  private List<Log> logs = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY)
  @JsonProperty("exercise_pauses")
  @JsonSerialize(using = MultiIdListSerializer.class)
  private List<Pause> pauses = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "exercises_tags",
      joinColumns = @JoinColumn(name = "exercise_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonProperty("exercise_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "exercises_documents",
      joinColumns = @JoinColumn(name = "exercise_id"),
      inverseJoinColumns = @JoinColumn(name = "document_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("exercise_documents")
  private List<Document> documents = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("exercise_articles")
  private List<Article> articles = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("exercise_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @Getter
  @OneToMany(mappedBy = "exercise", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("exercise_variables")
  private List<Variable> variables = new ArrayList<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SIMULATION;

  // region transient
  @JsonProperty("exercise_injects_statistics")
  public Map<String, Long> getInjectStatistics() {
    return InjectStatisticsHelper.getInjectStatistics(this.getInjects());
  }

  @JsonProperty("exercise_lessons_answers_number")
  public Long getLessonsAnswersNumbers() {
    return getLessonsCategories().stream()
        .flatMap(
            lessonsCategory ->
                lessonsCategory.getQuestions().stream()
                    .flatMap(lessonsQuestion -> lessonsQuestion.getAnswers().stream()))
        .count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("exercise_planners")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getPlanners() {
    return getUsersByType(this.getGrants(), PLANNER);
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("exercise_observers")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getObservers() {
    return getUsersByType(this.getGrants(), PLANNER, OBSERVER);
  }

  @JsonProperty("exercise_next_inject_date")
  public Optional<Instant> getNextInjectExecution() {
    return getInjects().stream()
        .filter(inject -> inject.getStatus().isEmpty())
        .filter(inject -> inject.getDate().isPresent())
        .filter(inject -> inject.getDate().get().isAfter(now()))
        .findFirst()
        .flatMap(Inject::getDate);
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin() || getObservers().contains(user);
  }

  @JsonProperty("exercise_all_users_number")
  public long usersAllNumber() {
    return getTeams().stream().mapToLong(Team::getUsersNumber).sum();
  }

  @JsonProperty("exercise_users_number")
  public long usersNumber() {
    return getTeamUsers().stream().map(ExerciseTeamUser::getUser).distinct().count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("exercise_users")
  @JsonSerialize(using = MultiIdListSerializer.class)
  public List<User> getUsers() {
    return getTeamUsers().stream().map(ExerciseTeamUser::getUser).distinct().toList();
  }

  @JsonProperty("exercise_score")
  public Double getEvaluationAverage() {
    double evaluationAverage =
        getObjectives().stream().mapToDouble(Objective::getEvaluationAverage).average().orElse(0D);
    return Math.round(evaluationAverage * 100.0) / 100.0;
  }

  @JsonProperty("exercise_logs_number")
  public long getLogsNumber() {
    return getLogs().size();
  }

  @JsonProperty("exercise_communications_number")
  public long getCommunicationsNumber() {
    return getInjects().stream().mapToLong(Inject::getCommunicationsNumber).sum();
  }

  // -- PLATFORMS --
  @JsonProperty("exercise_platforms")
  public List<PLATFORM_TYPE> getPlatforms() {
    return getInjects().stream()
        .flatMap(
            inject ->
                inject.getInjectorContract().map(InjectorContract::getPlatforms).stream()
                    .flatMap(Arrays::stream))
        .distinct()
        .toList();
  }

  // -- KILL CHAIN PHASES --
  @JsonProperty("exercise_kill_chain_phases")
  @Queryable(
      filterable = true,
      dynamicValues = true,
      path = "injects.injectorContract.attackPatterns.killChainPhases.id")
  public List<KillChainPhase> getKillChainPhases() {
    return getInjects().stream()
        .flatMap(
            inject ->
                inject.getInjectorContract().map(InjectorContract::getAttackPatterns).stream()
                    .flatMap(Collection::stream)
                    .flatMap(attackPattern -> attackPattern.getKillChainPhases().stream()))
        .distinct()
        .toList();
  }

  @JsonProperty("exercise_next_possible_status")
  public List<ExerciseStatus> nextPossibleStatus() {
    if (ExerciseStatus.CANCELED.equals(status)) {
      return List.of(ExerciseStatus.SCHEDULED); // Via reset
    }
    if (ExerciseStatus.FINISHED.equals(status)) {
      return List.of(ExerciseStatus.SCHEDULED); // Via reset
    }
    if (ExerciseStatus.SCHEDULED.equals(status)) {
      return List.of(ExerciseStatus.RUNNING);
    }
    if (ExerciseStatus.RUNNING.equals(status)) {
      return List.of(ExerciseStatus.CANCELED, ExerciseStatus.PAUSED);
    }
    if (ExerciseStatus.PAUSED.equals(status)) {
      return List.of(ExerciseStatus.CANCELED, ExerciseStatus.RUNNING);
    }
    return List.of();
  }

  // endregion

  public Optional<Instant> getStart() {
    return ofNullable(start);
  }

  public Optional<Instant> getEnd() {
    return ofNullable(end);
  }

  public Optional<Instant> getCurrentPause() {
    return ofNullable(currentPause);
  }

  public List<Inject> getInjects() {
    return injects.stream()
        .sorted(Inject.executionComparator)
        .collect(Collectors.toList()); // Should be modifiable
  }

  public List<Article> getArticlesForChannel(Channel channel) {
    return articles.stream().filter(article -> article.getChannel().equals(channel)).toList();
  }

  public void addReplyTos(List<String> replyTos) {
    getReplyTos().addAll(replyTos);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
