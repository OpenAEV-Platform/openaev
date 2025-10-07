package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.helper.*;
import io.openaev.rest.inject.output.InjectOutput;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScenarioOutput {

  @JsonProperty("scenario_id")
  @NotBlank
  @Schema(description = "ID of the scenario")
  private String id;

  @JsonProperty("scenario_name")
  @NotBlank
  @Schema(description = "Name of the scenario")
  private String name;

  @JsonProperty("scenario_description")
  @Schema(description = "Description of the scenario")
  private String description;

  @JsonProperty("scenario_subtitle")
  @Schema(description = "Subtitle of the scenario")
  private String subtitle;

  @JsonProperty("scenario_category")
  @Schema(description = "Category of the scenario")
  private String category;

  @JsonProperty("scenario_main_focus")
  @Schema(description = "Main focus value of the scenario")
  private String mainFocus;

  @JsonProperty("scenario_severity")
  @Schema(description = "Severity of the scenario")
  private Scenario.SEVERITY severity;

  @JsonProperty("scenario_external_reference")
  @Schema(description = "External reference of the scenario")
  private String externalReference;

  @JsonProperty("scenario_external_url")
  @Schema(description = "External URL of the scenario")
  private String externalUrl;

  @JsonProperty("scenario_recurrence")
  @Schema(description = "Recurrence of the scenario")
  private String recurrence;

  @JsonProperty("scenario_recurrence_start")
  @Schema(description = "Recurrence start date of the scenario")
  private Instant recurrenceStart;

  @JsonProperty("scenario_recurrence_end")
  @Schema(description = "Recurrence end date of the scenario")
  private Instant recurrenceEnd;

  @JsonProperty("scenario_message_header")
  @Schema(description = "Header of the scenario")
  private String header;

  @JsonProperty("scenario_message_footer")
  @Schema(description = "Footer of the scenario")
  private String footer;

  @JsonProperty("scenario_mail_from")
  @NotBlank
  @Schema(description = "From value of the scenario")
  private String from;

  @JsonProperty("scenario_mails_reply_to")
  @ArraySchema(schema = @Schema(description = "Replies to of the scenario"))
  private List<String> replyTos;

  @JsonProperty("scenario_created_at")
  @NotNull
  @Schema(description = "Creation date of the scenario")
  private Instant createdAt;

  @JsonProperty("scenario_updated_at")
  @NotNull
  @Schema(description = "Update date of the scenario")
  private Instant updatedAt;

  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("scenario_custom_dashboard")
  @Schema(description = "Custom dashboard of the scenario")
  private CustomDashboard customDashboard;

  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("scenario_injects")
  @ArraySchema(schema = @Schema(description = "Injects of the scenario"))
  private List<InjectOutput> injects;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_teams")
  @ArraySchema(schema = @Schema(description = "Teams of the scenario"))
  private List<Team> teams;

  @JsonProperty("scenario_teams_users")
  @JsonSerialize(using = MultiModelDeserializer.class)
  @ArraySchema(schema = @Schema(description = "Enabled users of the scenario"))
  private List<ScenarioTeamUser> teamUsers;

  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("scenario_tags")
  @Schema(description = "Tag of the scenario")
  private Set<Tag> tags;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_documents")
  @ArraySchema(schema = @Schema(description = "Documents of the scenario"))
  private List<Document> documents;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_articles")
  @ArraySchema(schema = @Schema(description = "Articles of the scenario"))
  private List<Article> articles;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_lessons_categories")
  @ArraySchema(schema = @Schema(description = "Lesson categories of the scenario"))
  private List<LessonsCategory> lessonsCategories;

  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("scenario_exercises")
  @ArraySchema(schema = @Schema(description = "Exercices of the scenario"))
  private List<Exercise> exercises;

  @JsonProperty("scenario_lessons_anonymized")
  @Schema(description = "Lesson anonymized state of the scenario")
  private boolean lessonsAnonymized;

  @JsonProperty("scenario_planners")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(description = "Planners of the scenario"))
  private List<User> planners;

  @JsonProperty("scenario_observers")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(description = "Observers of the scenario"))
  private List<User> observers;

  @JsonProperty("scenario_injects_statistics")
  @Schema(description = "Inject statisctics of the scenario")
  private Map<String, Long> injectStatistics;

  @JsonProperty("scenario_all_users_number")
  @Schema(description = "Total number of users of the scenario")
  private long usersAllNumber;

  @JsonProperty("scenario_users_number")
  @Schema(description = "Active total number of users of the scenario")
  private long usersNumber;

  @JsonProperty("scenario_users")
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @ArraySchema(schema = @Schema(description = "Users of the scenario"))
  private List<User> users;

  @JsonProperty("scenario_communications_number")
  @Schema(description = "Communications number of the scenario")
  private long communicationsNumber;

  @JsonProperty("scenario_platforms")
  @ArraySchema(schema = @Schema(description = "Platforms of the scenario"))
  private List<Endpoint.PLATFORM_TYPE> platforms;

  @JsonProperty("scenario_kill_chain_phases")
  @ArraySchema(schema = @Schema(description = "Kill chain phases of the scenario"))
  private List<KillChainPhase> killChainPhases;

  @JsonProperty("scenario_healthchecks")
  @ArraySchema(schema = @Schema(description = "Healthchecks of the scenario"))
  private List<HealthCheck> healthchecks = new ArrayList<>();
}
