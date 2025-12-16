package io.openaev.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Set;
import lombok.Data;

@Data
public class ScenarioDTO {

  @JsonProperty("scenario_id")
  private String id;

  @JsonProperty("scenario_name")
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
  private String severity;

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
  private String from;

  @JsonProperty("scenario_created_at")
  private Instant createdAt;

  @JsonProperty("scenario_updated_at")
  private Instant updatedAt;

  @JsonProperty("scenario_custom_dashboard")
  private String customDashboard;

  @JsonProperty("scenario_teams_users")
  private Set<ScenarioTeamUser> teamUsers;

  @JsonProperty("scenario_tags")
  private Set<String> tags;

  @JsonProperty("scenario_exercises")
  private Set<String> exercises;

  @Column(name = "scenario_lessons_anonymized")
  private boolean lessonsAnonymized;

  @JsonProperty("scenario_dependencies")
  private Set<String> dependencies;

  @JsonProperty("scenario_kill_chain_phases")
  private Set<KillChainPhaseDTO> killChainPhases;

  @JsonProperty("scenario_platforms")
  private Set<String> platforms;

  @JsonProperty("scenario_users_number")
  private long scenarioUsersNumber;

  @JsonProperty("scenario_all_users_number")
  private long scenarioAllUsersNumber;
}
