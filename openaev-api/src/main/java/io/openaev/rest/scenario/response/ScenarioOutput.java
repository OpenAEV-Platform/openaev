package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.*;
import io.openaev.rest.inject.output.InjectOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ScenarioOutput {

  @JsonProperty("scenario_id")
  @NotBlank
  private String id;

  @JsonProperty("scenario_name")
  @NotBlank
  private String name;

  @JsonProperty("scenario_description")
  private String description;

  @JsonProperty("scenario_subtitle")
  private String subtitle;

  @JsonProperty("scenario_category")
  private String category;

  @JsonProperty("scenario_main_focus")
  private String mainFocus;

  @JsonProperty("scenario_severity")
  private Scenario.SEVERITY severity;

  @JsonProperty("scenario_external_reference")
  private String externalReference;

  @JsonProperty("scenario_external_url")
  private String externalUrl;

  @JsonProperty("scenario_recurrence")
  private String recurrence;

  @JsonProperty("scenario_recurrence_start")
  private Instant recurrenceStart;

  @JsonProperty("scenario_recurrence_end")
  private Instant recurrenceEnd;

  @JsonProperty("scenario_message_header")
  private String header;

  @JsonProperty("scenario_message_footer")
  private String footer;

  @JsonProperty("scenario_mail_from")
  @NotBlank
  private String from;

  @JsonProperty("scenario_mails_reply_to")
  private List<String> replyTos;

  @JsonProperty("scenario_created_at")
  @NotNull
  private Instant createdAt;

  @JsonProperty("scenario_updated_at")
  @NotNull
  private Instant updatedAt;

  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("scenario_custom_dashboard")
  private CustomDashboard customDashboard;

  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("scenario_injects")
  private List<InjectOutput> injects;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_teams")
  private List<Team> teams;

  @JsonProperty("scenario_teams_users")
  @JsonSerialize(using = MultiModelDeserializer.class)
  private List<ScenarioTeamUser> teamUsers;

  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("scenario_tags")
  private Set<Tag> tags;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_documents")
  private List<Document> documents;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_articles")
  private List<Article> articles;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_lessons_categories")
  private List<LessonsCategory> lessonsCategories;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_exercises")
  private List<Exercise> exercises;

  @JsonProperty("scenario_lessons_anonymized")
  private boolean lessonsAnonymized;

  @JsonProperty("scenario_planners")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  private List<User> planners;

  @JsonProperty("scenario_observers")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  private List<User> observers;

  @JsonProperty("scenario_injects_statistics")
  private Map<String, Long> injectStatistics;

  @JsonProperty("scenario_all_users_number")
  private long usersAllNumber;

  @JsonProperty("scenario_users_number")
  private long usersNumber;

  @JsonProperty("scenario_users")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  private List<User> users;

  @JsonProperty("scenario_communications_number")
  private long communicationsNumber;

  @JsonProperty("scenario_platforms")
  private List<Endpoint.PLATFORM_TYPE> platforms;

  @JsonProperty("scenario_kill_chain_phases")
  private List<KillChainPhase> killChainPhases;

  @JsonProperty("scenario_healthchecks")
  private List<HealthCheck> healthchecks = new ArrayList<>();
}
