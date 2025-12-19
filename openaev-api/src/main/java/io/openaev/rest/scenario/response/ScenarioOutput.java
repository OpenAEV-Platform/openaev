package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.kill_chain_phase.response.KillChainPhaseOutput;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
  private String severity;

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

  @JsonProperty("scenario_created_at")
  @NotNull
  @Schema(description = "Creation date of the scenario")
  private Instant createdAt;

  @JsonProperty("scenario_updated_at")
  @NotNull
  @Schema(description = "Update date of the scenario")
  private Instant updatedAt;

  @JsonProperty("scenario_custom_dashboard")
  @Schema(description = "Custom dashboard of the scenario")
  private String customDashboard;

  @JsonProperty("scenario_teams_users")
  @ArraySchema(schema = @Schema(description = "Enabled users of the scenario"))
  private Set<ScenarioTeamUserOutput> teamUsers;

  @JsonProperty("scenario_tags")
  @ArraySchema(schema = @Schema(description = "Tags ids of the scenario"))
  private Set<String> tags;

  @JsonProperty("scenario_exercises")
  @ArraySchema(schema = @Schema(description = "Exercises ids of the scenario"))
  private Set<String> exercises;

  @Column(name = "scenario_lessons_anonymized")
  @Schema(description = "Lesson anonymized state of the scenario")
  private boolean lessonsAnonymized;

  @JsonProperty("scenario_dependencies")
  @ArraySchema(schema = @Schema(description = "Dependencies of the scenario"))
  private Set<String> dependencies;

  @JsonProperty("scenario_kill_chain_phases")
  @ArraySchema(schema = @Schema(description = "Kill chain phases of the scenario"))
  private Set<KillChainPhaseOutput> killChainPhases;

  @JsonProperty("scenario_platforms")
  @ArraySchema(schema = @Schema(description = "Platforms of the scenario"))
  private Set<String> platforms;

  @JsonProperty("scenario_users_number")
  @Schema(description = "Active total number of users of the scenario")
  private long scenarioUsersNumber;

  @JsonProperty("scenario_all_users_number")
  @Schema(description = "Total number of users of the scenario")
  private long scenarioAllUsersNumber;
}
