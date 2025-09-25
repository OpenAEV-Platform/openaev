package io.openbas.database.model;

import static io.openbas.database.model.Grant.GRANT_TYPE.OBSERVER;
import static io.openbas.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.openbas.helper.UserHelper.getUsersByType;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openbas.annotation.Queryable;
import io.openbas.database.audit.ModelBaseListener;
import io.openbas.database.model.Endpoint.PLATFORM_TYPE;
import io.openbas.helper.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "scenarios")
@EntityListeners(ModelBaseListener.class)
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Scenario.tags-injects",
      attributeNodes = {@NamedAttributeNode("tags"), @NamedAttributeNode("injects")})
})
@Grantable(Grant.GRANT_RESOURCE_TYPE.SCENARIO)
public class Scenario implements GrantableBase {

  public enum RECURRENCE_STATUS {
    SCHEDULED,
    NOT_PLANNED,
  }

  public enum SEVERITY {
    @JsonProperty("low")
    low,
    @JsonProperty("medium")
    medium,
    @JsonProperty("high")
    high,
    @JsonProperty("critical")
    critical,
  }

  public static final String MAIN_FOCUS_INCIDENT_RESPONSE = "incident-response";
  public static final String MAIN_FOCUS_ENDPOINT_PROTECTION = "endpoint-protection";
  public static final String MAIN_FOCUS_WEB_FILTERING = "web-filtering";
  public static final String MAIN_FOCUS_STANDARD_OPERATING_PROCEDURE =
      "standard-operating-procedure";
  public static final String MAIN_FOCUS_CRISIS_COMMUNICATION = "crisis-communication";
  public static final String MAIN_FOCUS_STRATEGIC_REACTION = "strategic-reaction";

  @Id
  @UuidGenerator
  @Column(name = "scenario_id")
  @JsonProperty("scenario_id")
  @NotBlank
  private String id;

  @Column(name = "scenario_name")
  @JsonProperty("scenario_name")
  @Queryable(filterable = true, searchable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "scenario_description")
  @JsonProperty("scenario_description")
  private String description;

  @Column(name = "scenario_subtitle")
  @JsonProperty("scenario_subtitle")
  private String subtitle;

  @Column(name = "scenario_category")
  @JsonProperty("scenario_category")
  @Queryable(filterable = true, sortable = true, dynamicValues = true)
  private String category;

  @Column(name = "scenario_main_focus")
  @JsonProperty("scenario_main_focus")
  private String mainFocus;

  @Column(name = "scenario_severity")
  @Enumerated(EnumType.STRING)
  @JsonProperty("scenario_severity")
  @Queryable(filterable = true, sortable = true)
  private SEVERITY severity;

  // -- OCTI GENERATION SCENARIO FROM HTTP CALL--

  @Column(name = "scenario_external_reference")
  @JsonProperty("scenario_external_reference")
  private String externalReference;

  @Column(name = "scenario_external_url")
  @JsonProperty("scenario_external_url")
  private String externalUrl;

  // -- OCTI GENERATION SCENARIO FROM STIX --

  @OneToOne(mappedBy = "scenario")
  @JsonProperty("scenario_security_coverage")
  @JsonIgnore
  private SecurityCoverage securityCoverage;

  // -- RECURRENCE --

  @Column(name = "scenario_recurrence")
  @JsonProperty("scenario_recurrence")
  @Queryable(filterable = true)
  private String recurrence;

  @Column(name = "scenario_recurrence_start")
  @JsonProperty("scenario_recurrence_start")
  private Instant recurrenceStart;

  @Column(name = "scenario_recurrence_end")
  @JsonProperty("scenario_recurrence_end")
  private Instant recurrenceEnd;

  // -- MESSAGE --

  @Column(name = "scenario_message_header")
  @JsonProperty("scenario_message_header")
  private String header = "SIMULATION HEADER";

  @Column(name = "scenario_message_footer")
  @JsonProperty("scenario_message_footer")
  private String footer = "SIMULATION FOOTER";

  @Column(name = "scenario_mail_from")
  @JsonProperty("scenario_mail_from")
  @Email
  @NotBlank
  private String from;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "scenario_mails_reply_to",
      joinColumns = @JoinColumn(name = "scenario_id"))
  @Column(name = "scenario_reply_to", nullable = false)
  @JsonProperty("scenario_mails_reply_to")
  private List<String> replyTos = new ArrayList<>();

  @Column(name = "from_starter_pack", nullable = false)
  @JsonProperty("from_starter_pack")
  @Queryable(filterable = true)
  private boolean fromStarterPack = false;

  // -- AUDIT --

  @Column(name = "scenario_created_at")
  @JsonProperty("scenario_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "scenario_updated_at")
  @JsonProperty("scenario_updated_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  // -- RELATION --

  @Getter
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scenario_custom_dashboard")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("scenario_custom_dashboard")
  @Schema(type = "string")
  private CustomDashboard customDashboard;

  @Getter
  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "scenario_id",
      insertable = false,
      updatable = false)
  @SQLRestriction(
      "grant_resource_type = 'SCENARIO'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "scenario", fetch = FetchType.LAZY)
  @JsonProperty("scenario_injects")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @Getter(NONE)
  private Set<Inject> injects = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setInjects(Set<Inject> injects) {
    this.updatedAt = now();
    this.injects = injects;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_teams",
      joinColumns = @JoinColumn(name = "scenario_id"),
      inverseJoinColumns = @JoinColumn(name = "team_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_teams")
  private List<Team> teams = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setTeams(List<Team> teams) {
    this.updatedAt = now();
    this.teams = teams;
  }

  @OneToMany(
      mappedBy = "scenario",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("scenario_teams_users")
  @JsonSerialize(using = MultiModelDeserializer.class)
  private List<ScenarioTeamUser> teamUsers = new ArrayList<>();

  @OneToMany(mappedBy = "scenario", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonIgnore
  private List<Objective> objectives = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_tags",
      joinColumns = @JoinColumn(name = "scenario_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("scenario_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_documents",
      joinColumns = @JoinColumn(name = "scenario_id"),
      inverseJoinColumns = @JoinColumn(name = "document_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_documents")
  private List<Document> documents = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "scenario", fetch = FetchType.LAZY)
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_articles")
  private List<Article> articles = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(mappedBy = "scenario", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @Getter
  @OneToMany(mappedBy = "scenario")
  @JsonIgnore
  public List<Variable> variables = new ArrayList<>();

  @ArraySchema(schema = @Schema(type = "string"))
  @OneToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "scenarios_exercises",
      joinColumns = @JoinColumn(name = "scenario_id"),
      inverseJoinColumns = @JoinColumn(name = "exercise_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_exercises")
  @Setter(NONE)
  private List<Exercise> exercises;

  public void setExercises(List<Exercise> exercises) {
    if (exercises != null) {
      for (Exercise exercise : exercises) {
        if (exercise != null) exercise.setUpdatedAt(now());
      }
    }
    this.exercises = exercises;
    this.setUpdatedAt(now());
  }

  @Getter
  @Column(name = "scenario_lessons_anonymized")
  @JsonProperty("scenario_lessons_anonymized")
  private boolean lessonsAnonymized = false;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.SCENARIO;

  // -- LESSONS --

  public List<Inject> getInjects() {
    return new ArrayList<>(this.injects);
  }

  // -- SECURITY --

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_planners")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  public List<User> getPlanners() {
    return getUsersByType(this.getGrants(), PLANNER);
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_observers")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  public List<User> getObservers() {
    return getUsersByType(this.getGrants(), PLANNER, OBSERVER);
  }

  // -- STATISTICS --

  @JsonProperty("scenario_injects_statistics")
  public Map<String, Long> getInjectStatistics() {
    return InjectStatisticsHelper.getInjectStatistics(this.getInjects());
  }

  @JsonProperty("scenario_all_users_number")
  public long usersAllNumber() {
    return getTeams().stream().mapToLong(Team::getUsersNumber).sum();
  }

  @JsonProperty("scenario_users_number")
  public long usersNumber() {
    return getTeamUsers().stream().map(ScenarioTeamUser::getUser).distinct().count();
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @JsonProperty("scenario_users")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  public List<User> getUsers() {
    return getTeamUsers().stream().map(ScenarioTeamUser::getUser).distinct().toList();
  }

  @JsonProperty("scenario_communications_number")
  public long getCommunicationsNumber() {
    return getInjects().stream().mapToLong(Inject::getCommunicationsNumber).sum();
  }

  // -- CHANNELS --

  public List<Article> getArticlesForChannel(Channel channel) {
    return this.articles.stream().filter(article -> article.getChannel().equals(channel)).toList();
  }

  // -- PLATFORMS --
  @JsonProperty("scenario_platforms")
  @Queryable(filterable = true, path = "injects.injectorContract.platforms", clazz = String[].class)
  public List<PLATFORM_TYPE> getPlatforms() {
    return getInjects().stream()
        .flatMap(
            inject -> inject.getInjectorContract().map(InjectorContract::getPlatforms).stream())
        .flatMap(Arrays::stream)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  // -- KILL CHAIN PHASES --
  @JsonProperty("scenario_kill_chain_phases")
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

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
