package io.openaev.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.injectors.channel.ChannelContract;
import io.openaev.injectors.channel.model.ChannelContent;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.utils.CopyObjectListUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScenarioToExerciseService {

  private final ExerciseRepository exerciseRepository;
  private final GrantRepository grantRepository;
  private final TeamRepository teamRepository;
  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final ObjectiveRepository objectiveRepository;
  private final DocumentRepository documentRepository;
  private final ArticleRepository articleRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final InjectRepository injectRepository;
  private final InjectDocumentRepository injectDocumentRepository;
  private final VariableService variableService;
  private final TeamService teamService;
  private final PlatformSettingsService platformSettingsService;
  private final CustomDashboardService customDashboardService;
  @Resource protected ObjectMapper mapper;

  @Transactional(rollbackFor = Exception.class)
  public Exercise toExercise(
      @NotBlank final Scenario scenario, @Nullable final Instant start, final boolean isRunning) {
    Exercise exercise = new Exercise();
    exercise.setScenario(scenario);
    exercise.setName(scenario.getName());
    exercise.setDescription(scenario.getDescription());
    exercise.setSubtitle(scenario.getSubtitle());
    exercise.setCategory(scenario.getCategory());
    exercise.setMainFocus(scenario.getMainFocus());
    exercise.setSeverity(scenario.getSeverity());
    exercise.setHeader(scenario.getHeader());
    exercise.setFooter(scenario.getFooter());
    exercise.setFrom(scenario.getFrom());
    exercise.addReplyTos(scenario.getReplyTos());
    exercise.setStart(start);
    exercise.setSecurityCoverage(scenario.getSecurityCoverage());
    if (isRunning) {
      exercise.setStatus(ExerciseStatus.RUNNING);
    }

    // Tags
    exercise.setTags(CopyObjectListUtils.copy(scenario.getTags(), Tag.class));

    // Custom Dashboard
    exercise.setCustomDashboard(
        this.platformSettingsService
            .setting(SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key())
            .map(Setting::getValue)
            .filter(v -> !v.isEmpty())
            .map(this.customDashboardService::customDashboard)
            .orElse(null));

    Exercise exerciseSaved = this.exerciseRepository.save(exercise);

    // Grants
    Grant.GRANT_RESOURCE_TYPE grantResourceTypeForSimulation =
        GrantableBase.getGrantResourceType(Exercise.class);
    List<Grant> exerciseGrants =
        scenario.getGrants().stream()
            .map(
                scenarioGrant -> {
                  Grant grant = new Grant();
                  grant.setName(scenarioGrant.getName());
                  grant.setGroup(scenarioGrant.getGroup());
                  grant.setResourceId(exerciseSaved.getId());
                  grant.setGrantResourceType(grantResourceTypeForSimulation);
                  return this.grantRepository.save(grant);
                })
            .toList();
    exerciseSaved.setGrants(exerciseGrants);

    // Teams
    Map<String, Team> contextualTeams = new HashMap<>();
    scenario
        .getTeams()
        .forEach(
            scenarioTeam -> {
              if (scenarioTeam.getContextual()) {
                Team team = teamService.copyContextualTeam(scenarioTeam);
                team.setExercises(
                    new ArrayList<>() {
                      {
                        add(exerciseSaved);
                      }
                    });
                Team teamSaved = this.teamRepository.save(team);
                contextualTeams.put(scenarioTeam.getId(), teamSaved);
              } else {
                List<Exercise> exercises = scenarioTeam.getExercises();
                exercises.add(exercise);
                scenarioTeam.setExercises(exercises);
                this.teamRepository.save(scenarioTeam);
              }
            });

    // TeamUsers
    List<ExerciseTeamUser> exerciseTeamUsers =
        scenario.getTeamUsers().stream()
            .map(
                scenarioTeamUser -> {
                  ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
                  exerciseTeamUser.setExercise(exerciseSaved);
                  exerciseTeamUser.setUser(scenarioTeamUser.getUser());
                  exerciseTeamUser.setTeam(
                      computeTeam(scenarioTeamUser.getTeam(), contextualTeams));
                  return exerciseTeamUser;
                })
            .toList();
    this.exerciseTeamUserRepository.saveAll(exerciseTeamUsers);

    // Objectives
    List<Objective> scenarioObjectives = scenario.getObjectives();
    List<Objective> exerciseObjectives =
        scenarioObjectives.stream()
            .map(
                scenarioObjective -> {
                  Objective exerciseObjective = new Objective();
                  exerciseObjective.setExercise(exerciseSaved);
                  exerciseObjective.setTitle(scenarioObjective.getTitle());
                  exerciseObjective.setDescription(scenarioObjective.getDescription());
                  exerciseObjective.setPriority(scenarioObjective.getPriority());
                  return exerciseObjective;
                })
            .toList();
    this.objectiveRepository.saveAll(exerciseObjectives);

    // Documents
    List<Document> scenarioDocuments =
        addExerciseToDocuments(scenario.getDocuments(), exerciseSaved);
    this.documentRepository.saveAll(scenarioDocuments);

    // Articles
    Map<String, Article> articles = new HashMap<>();
    List<Article> scenarioArticles = scenario.getArticles();
    List<Article> exerciseArticles =
        scenarioArticles.stream()
            .map(
                scenarioArticle -> {
                  Article exerciseArticle = new Article();
                  exerciseArticle.setName(scenarioArticle.getName());
                  exerciseArticle.setContent(scenarioArticle.getContent());
                  exerciseArticle.setAuthor(scenarioArticle.getAuthor());
                  exerciseArticle.setShares(scenarioArticle.getShares());
                  exerciseArticle.setLikes(scenarioArticle.getLikes());
                  exerciseArticle.setComments(scenarioArticle.getComments());
                  exerciseArticle.setExercise(exerciseSaved);
                  exerciseArticle.setChannel(scenarioArticle.getChannel());

                  List<Document> articleDocuments =
                      addExerciseToDocuments(scenarioArticle.getDocuments(), exerciseSaved);
                  exerciseArticle.setDocuments(articleDocuments);
                  articles.put(scenarioArticle.getId(), exerciseArticle);
                  return exerciseArticle;
                })
            .toList();
    this.articleRepository.saveAll(exerciseArticles);

    // Lessons
    exercise.setLessonsAnonymized(scenario.isLessonsAnonymized());

    // Lessons categories
    List<LessonsCategory> scenarioLessonCategories = scenario.getLessonsCategories();
    scenarioLessonCategories.forEach(
        scenarioLessonCategory -> {
          LessonsCategory exerciseLessonCategory = new LessonsCategory();
          exerciseLessonCategory.setExercise(exerciseSaved);
          exerciseLessonCategory.setName(scenarioLessonCategory.getName());
          exerciseLessonCategory.setDescription(scenarioLessonCategory.getDescription());
          exerciseLessonCategory.setOrder(scenarioLessonCategory.getOrder());

          // Teams
          List<Team> teams = new ArrayList<>();
          scenarioLessonCategory
              .getTeams()
              .forEach(team -> teams.add(computeTeam(team, contextualTeams)));
          exerciseLessonCategory.setTeams(teams);

          LessonsCategory exerciseLessonCategorySaved =
              this.lessonsCategoryRepository.save(exerciseLessonCategory);

          // Lessons questions
          List<LessonsQuestion> exerciseLessonsQuestions =
              scenarioLessonCategory.getQuestions().stream()
                  .map(
                      scenarioLessonsQuestion -> {
                        LessonsQuestion exerciseLessonsQuestion = new LessonsQuestion();
                        exerciseLessonsQuestion.setContent(scenarioLessonsQuestion.getContent());
                        exerciseLessonsQuestion.setExplanation(
                            scenarioLessonsQuestion.getExplanation());
                        exerciseLessonsQuestion.setOrder(scenarioLessonsQuestion.getOrder());
                        exerciseLessonsQuestion.setCategory(exerciseLessonCategorySaved);
                        return exerciseLessonsQuestion;
                      })
                  .toList();
          this.lessonsQuestionRepository.saveAll(exerciseLessonsQuestions);
        });

    // Injects
    List<Inject> scenarioInjects = scenario.getInjects();
    Map<String, Inject> mapExerciseInjectsByScenarioInject = new HashMap<>();
    scenarioInjects.forEach(
        scenarioInject -> {
          Inject exerciseInject = new Inject();
          exerciseInject.setTitle(scenarioInject.getTitle());
          exerciseInject.setDescription(scenarioInject.getDescription());
          exerciseInject.setInjectorContract(scenarioInject.getInjectorContract().orElse(null));
          exerciseInject.setCountry(scenarioInject.getCountry());
          exerciseInject.setCity(scenarioInject.getCity());
          exerciseInject.setEnabled(scenarioInject.isEnabled());
          exerciseInject.setAllTeams(scenarioInject.isAllTeams());
          exerciseInject.setExercise(exerciseSaved);
          exerciseInject.setDependsDuration(scenarioInject.getDependsDuration());
          exerciseInject.setUser(scenarioInject.getUser());
          exerciseInject.setStatus(scenarioInject.getStatus().orElse(null));
          exerciseInject.setTags(CopyObjectListUtils.copy(scenarioInject.getTags(), Tag.class));
          exerciseInject.setContent(scenarioInject.getContent());

          // Content
          scenarioInject
              .getInjectorContract()
              .ifPresentOrElse(
                  injectorContract -> {
                    if (ChannelContract.TYPE.equals(injectorContract.getInjector().getType())) {
                      try {
                        ChannelContent content =
                            mapper.treeToValue(scenarioInject.getContent(), ChannelContent.class);
                        content.setArticles(
                            content.getArticles().stream()
                                .map(articleId -> articles.get(articleId).getId())
                                .toList());
                        exerciseInject.setContent(mapper.valueToTree(content));
                      } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                      }
                    }
                  },
                  () -> exerciseInject.setContent(scenarioInject.getContent()));

          // Teams
          List<Team> teams = new ArrayList<>();
          scenarioInject.getTeams().forEach(team -> teams.add(computeTeam(team, contextualTeams)));
          exerciseInject.setTeams(teams);

          // Assets & Asset Groups
          exerciseInject.setAssets(
              CopyObjectListUtils.copy(scenarioInject.getAssets(), Asset.class));
          exerciseInject.setAssetGroups(
              CopyObjectListUtils.copy(scenarioInject.getAssetGroups(), AssetGroup.class));
          Inject injectSaved = this.injectRepository.save(exerciseInject);

          mapExerciseInjectsByScenarioInject.put(scenarioInject.getId(), injectSaved);

          // Documents
          List<InjectDocument> exerciseInjectDocuments = new ArrayList<>();
          scenarioInject
              .getDocuments()
              .forEach(
                  injectDocument -> {
                    InjectDocument exerciseInjectDocument = new InjectDocument();
                    exerciseInjectDocument.setInject(injectSaved);
                    exerciseInjectDocument.setDocument(injectDocument.getDocument());
                    exerciseInjectDocument.setAttached(injectDocument.isAttached());
                    exerciseInjectDocuments.add(exerciseInjectDocument);
                  });
          this.injectDocumentRepository.saveAll(exerciseInjectDocuments);
        });

    // Second pass to add the correct links
    scenarioInjects.forEach(
        scenarioInject -> {
          if (scenarioInject.getDependsOn() != null) {
            Inject injectToUpdate = mapExerciseInjectsByScenarioInject.get(scenarioInject.getId());
            injectToUpdate.getDependsOn().clear();
            injectToUpdate
                .getDependsOn()
                .addAll(
                    scenarioInject.getDependsOn().stream()
                        .map(
                            (injectDependency -> {
                              InjectDependency dep = new InjectDependency();
                              dep.setCompositeId(injectDependency.getCompositeId());
                              dep.setInjectDependencyCondition(
                                  injectDependency.getInjectDependencyCondition());
                              dep.getCompositeId()
                                  .setInjectParent(
                                      mapExerciseInjectsByScenarioInject.get(
                                          dep.getCompositeId().getInjectParent().getId()));
                              dep.getCompositeId().setInjectChildren(injectToUpdate);
                              return dep;
                            }))
                        .toList());
            this.injectRepository.save(injectToUpdate);
          }
        });

    // Variables
    this.variableService.copyVariableFromScenarioForSimulation(
        scenario.getId(), exerciseSaved.getId());

    return exerciseSaved;
  }

  private List<Document> addExerciseToDocuments(
      @NotNull final List<Document> origDocuments, @NotNull final Exercise exercise) {
    List<Document> destDocuments = new ArrayList<>();
    origDocuments.forEach(
        origDocument -> {
          try {
            Document destDocument = new Document();
            BeanUtils.copyProperties(destDocument, origDocument);
            Set<Exercise> exercises = destDocument.getExercises();
            exercises.add(exercise);
            destDocument.setExercises(exercises);
            destDocuments.add(destDocument);
          } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        });
    return destDocuments;
  }

  private Team computeTeam(
      @NotNull final Team origTeam, @NotNull final Map<String, Team> contextualTeams) {
    if (origTeam.getContextual()) {
      return contextualTeams.get(origTeam.getId());
    } else {
      return origTeam;
    }
  }
}
