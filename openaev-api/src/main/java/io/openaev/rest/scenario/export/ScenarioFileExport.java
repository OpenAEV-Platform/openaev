package io.openaev.rest.scenario.export;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(NON_NULL)
public class ScenarioFileExport {

  @JsonProperty("export_version")
  private int version;

  @JsonProperty("scenario_information")
  private Scenario scenario;

  @JsonProperty("scenario_teams")
  private List<Team> teams = new ArrayList<>();

  @JsonProperty("scenario_objectives")
  private List<Objective> objectives = new ArrayList<>();

  @JsonProperty("scenario_users")
  private List<User> users = new ArrayList<>();

  @JsonProperty("scenario_organizations")
  private List<Organization> organizations = new ArrayList<>();

  @JsonProperty("scenario_injects")
  private List<Inject> injects = new ArrayList<>();

  @JsonProperty("scenario_tags")
  private List<Tag> tags = new ArrayList<>();

  @JsonProperty("scenario_documents")
  private List<Document> documents = new ArrayList<>();

  @JsonProperty("scenario_channels")
  private List<Channel> channels = new ArrayList<>();

  @JsonProperty("scenario_articles")
  private List<Article> articles = new ArrayList<>();

  @JsonProperty("scenario_challenges")
  private List<Challenge> challenges = new ArrayList<>();

  @JsonProperty("scenario_lessons_categories")
  private List<LessonsCategory> lessonsCategories = new ArrayList<>();

  @JsonProperty("scenario_lessons_questions")
  private List<LessonsQuestion> lessonsQuestions = new ArrayList<>();

  @JsonIgnore public static final String SCENARIO_VARIABLES = "scenario_variables";

  @JsonProperty(SCENARIO_VARIABLES)
  private List<Variable> variables;
}
