package io.openaev.rest.exercise.exports;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.export.FileExportBase;
import io.openaev.service.ArticleService;
import io.openaev.service.ChallengeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

@Getter
@Setter
@JsonInclude(NON_NULL)
public class ExerciseFileExport extends FileExportBase {
  @JsonProperty("exercise_information")
  private final Exercise exercise;

  @JsonProperty("exercise_teams")
  private List<Team> teams;

  public List<Team> getTeams() {
    if (teams == null) {
      return this.exercise != null
              && ExportOptions.has(ExportOptions.WITH_TEAMS, this.exportOptionsMask)
          ? this.exercise.getTeams().stream().toList()
          : new ArrayList<>();
    }
    return teams;
  }

  @JsonProperty("exercise_objectives")
  private List<Objective> objectives;

  public List<Objective> getObjectives() {
    if (objectives == null) {
      return this.exercise == null
          ? new ArrayList<>()
          : this.exercise.getObjectives().stream().toList();
    }
    return objectives;
  }

  @JsonProperty("exercise_users")
  private List<User> users;

  public List<User> getUsers() {
    if (users == null) {
      return this.exercise != null
              && ExportOptions.has(ExportOptions.WITH_PLAYERS, this.exportOptionsMask)
          ? this.exercise.getTeams().stream()
              .flatMap(team -> team.getUsers().stream())
              .distinct()
              .toList()
          : new ArrayList<>();
    }
    return users;
  }

  @JsonProperty("exercise_organizations")
  private List<Organization> organizations;

  public List<Organization> getOrganizations() {
    if (organizations == null) {
      if (this.exercise == null) {
        return new ArrayList<>();
      }
      List<Organization> orgs = new ArrayList<>();
      orgs.addAll(
          this.exercise.getUsers().stream()
              .map(user -> (Organization) Hibernate.unproxy(user.getOrganization()))
              .filter(Objects::nonNull)
              .toList());
      orgs.addAll(
          this.exercise.getTeams().stream()
              .map(team -> (Organization) Hibernate.unproxy(team.getOrganization()))
              .filter(Objects::nonNull)
              .toList());
      return orgs;
    }
    return organizations;
  }

  @JsonProperty("exercise_injects")
  private List<Inject> injects;

  public List<Inject> getInjects() {
    if (injects == null) {
      return this.exercise == null ? new ArrayList<>() : this.exercise.getInjects();
    }
    return injects;
  }

  @JsonProperty("exercise_tags")
  private List<Tag> tags;

  public List<Tag> getTags() {
    if (tags == null) {
      if (this.exercise == null) {
        return new ArrayList<>();
      } else {
        List<Tag> allTags = new ArrayList<>();
        allTags.addAll(this.exercise.getTags().stream().toList());
        allTags.addAll(this.getTeams().stream().flatMap(team -> team.getTags().stream()).toList());
        allTags.addAll(this.getUsers().stream().flatMap(user -> user.getTags().stream()).toList());
        allTags.addAll(
            this.getOrganizations().stream()
                .flatMap(organization -> organization.getTags().stream())
                .toList());
        allTags.addAll(
            this.getDocuments().stream().flatMap(doc -> doc.getTags().stream()).toList());
        allTags.addAll(
            this.getChallenges().stream()
                .flatMap(challenge -> challenge.getTags().stream())
                .toList());
        this.getInjects()
            .forEach(
                inject -> {
                  allTags.addAll(inject.getTags());
                  inject
                      .getInjectorContract()
                      .ifPresent(
                          injectorContract -> {
                            if (injectorContract.getPayload() != null) {
                              allTags.addAll(injectorContract.getPayload().getTags());
                            }
                          });
                });

        return allTags;
      }
    }
    return tags;
  }

  @JsonProperty("exercise_documents")
  private List<Document> documents;

  public List<Document> getDocuments() {
    if (documents == null) {
      if (this.exercise == null) {
        return new ArrayList<>();
      }
      List<Document> docs = new ArrayList<>();
      docs.addAll(this.exercise.getDocuments());
      docs.addAll(
          this.exercise.getInjects().stream()
              .flatMap(
                  inject -> {
                    if (inject.getPayload().isEmpty()) {
                      return Stream.of();
                    }
                    if (inject.getPayload().get().getAttachedDocument().isPresent()) {
                      return Stream.of(inject.getPayload().get().getAttachedDocument().get());
                    }
                    return Stream.of();
                  })
              .toList());
      return docs;
    }
    return documents;
  }

  @JsonProperty("exercise_channels")
  private List<Channel> channels;

  public List<Channel> getChannels() {
    if (channels == null) {
      return this.exercise == null
          ? new ArrayList<>()
          : this.exercise.getArticles().stream().map(Article::getChannel).distinct().toList();
    }
    return channels;
  }

  @JsonProperty("exercise_articles")
  private List<Article> articles;

  public List<Article> getArticles() {
    if (articles == null) {
      return this.exercise == null ? new ArrayList<>() : this.exercise.getArticles();
    }
    return articles;
  }

  @JsonProperty("exercise_challenges")
  private List<Challenge> challenges;

  public List<Challenge> getChallenges() {
    if (challenges == null) {
      return this.exercise == null
          ? new ArrayList<>()
          // this forces export to first persist to database before this can work
          // TODO: refactor to allow for pure in-memory simulation export
          : fromIterable(challengeService.getExerciseChallenges(this.exercise.getId()));
    }
    return challenges;
  }

  @JsonProperty("exercise_lessons_categories")
  private List<LessonsCategory> lessonsCategories;

  public List<LessonsCategory> getLessonsCategories() {
    if (lessonsCategories == null) {
      return this.exercise == null
          ? new ArrayList<>()
          : this.exercise.getLessonsCategories().stream().toList();
    }
    return lessonsCategories;
  }

  @JsonProperty("exercise_lessons_questions")
  private List<LessonsQuestion> lessonsQuestions;

  public List<LessonsQuestion> getLessonsQuestions() {
    if (lessonsQuestions == null) {
      return this.exercise == null
          ? new ArrayList<>()
          : this.getLessonsCategories().stream()
              .flatMap(category -> category.getQuestions().stream())
              .toList();
    }
    return lessonsQuestions;
  }

  @JsonIgnore public static final String EXERCISE_VARIABLES = "exercise_variables";

  @JsonProperty(EXERCISE_VARIABLES)
  private List<Variable> variables;

  public List<Variable> getVariables() {
    if (variables == null) {
      return this.exercise == null
          ? new ArrayList<>()
          : this.exercise.getVariables().stream().toList();
    }
    return variables;
  }

  @JsonIgnore
  public List<String> getAllDocumentIds() {
    List<String> documentIds = new ArrayList<>();
    documentIds.addAll(this.getDocuments().stream().map(Document::getId).toList());
    documentIds.addAll(
        this.getChannels().stream()
            .flatMap(channel -> channel.getLogos().stream())
            .map(Document::getId)
            .toList());
    documentIds.addAll(
        this.getChallenges().stream()
            .flatMap(challenge -> challenge.getDocuments().stream())
            .map(Document::getId)
            .toList());
    return documentIds;
  }

  private ExerciseFileExport(
      Exercise exercise,
      ObjectMapper objectMapper,
      ChallengeService challengeService,
      ArticleService articleService) {
    super(objectMapper, challengeService, articleService);
    this.exercise = exercise;
  }

  public static ExerciseFileExport fromExercise(
      Exercise exercise,
      ObjectMapper objectMapper,
      ChallengeService challengeService,
      ArticleService articleService) {
    return new ExerciseFileExport(exercise, objectMapper, challengeService, articleService);
  }

  @Override
  public ExerciseFileExport withOptions(int exportOptionsMask) {
    return (ExerciseFileExport) super.withOptions(exportOptionsMask);
  }
}
