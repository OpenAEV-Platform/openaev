package io.openaev.rest.scenario;

import static io.openaev.database.model.SettingKeys.DEFAULT_SIMULATION_DASHBOARD;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.utils.fixtures.ArticleFixture.ARTICLE_NAME;
import static io.openaev.utils.fixtures.ArticleFixture.getArticle;
import static io.openaev.utils.fixtures.DocumentFixture.getDocumentJpeg;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.ObjectiveFixture.OBJECTIVE_NAME;
import static io.openaev.utils.fixtures.ObjectiveFixture.getObjective;
import static io.openaev.utils.fixtures.TagFixture.getTag;
import static io.openaev.utils.fixtures.TeamFixture.getTeam;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.service.LoadService;
import io.openaev.service.ScenarioService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScenarioToExerciseServiceTest extends IntegrationTest {

  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private LoadService loadService;

  @Autowired private ScenarioService scenarioService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ChannelRepository channelRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private VariableRepository variableRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;

  private static String SCENARIO_ID;
  private static String EXERCISE_ID;
  private static String USER_ID;
  private static String TEAM_ID;
  private static String TEAM_CONTEXTUAL_ID;
  private static String TAG_ID;
  private static String DOCUMENT_ARTICLE_ID;
  private static String CHANNEL_ID;
  private static String LESSON_CATEGORY_ID;
  private static String LESSON_QUESTION_ID;
  private static String INJECT_ID;
  private static String DOCUMENT_ID;
  private static String VARIABLE_ID;

  @AfterAll
  public void teardown() {
    this.scenarioService.deleteScenario(SCENARIO_ID);
    this.exerciseRepository.deleteById(EXERCISE_ID);
    this.userRepository.deleteById(USER_ID);
    this.teamRepository.deleteById(TEAM_ID);
    this.teamRepository.deleteById(TEAM_CONTEXTUAL_ID);
    this.tagRepository.deleteById(TAG_ID);
    this.documentRepository.deleteById(DOCUMENT_ARTICLE_ID);
    this.channelRepository.deleteById(CHANNEL_ID);
    this.lessonsCategoryRepository.deleteById(LESSON_CATEGORY_ID);
    this.lessonsQuestionRepository.deleteById(LESSON_QUESTION_ID);
    this.injectRepository.deleteById(INJECT_ID);
    this.documentRepository.deleteById(DOCUMENT_ID);
    this.variableRepository.deleteById(VARIABLE_ID);
  }

  @DisplayName("Scenario to Exercise test")
  @Test
  void scenarioToExerciseTest() {
    // -- PREPARE --
    // Base
    Scenario scenario = ScenarioFixture.getScenario();
    String name = scenario.getName();
    // User & Teams
    User user = getUser();
    User userSaved = this.userRepository.save(user);
    USER_ID = userSaved.getId();
    Team team = getTeam(user);
    Team teamSaved = this.teamRepository.save(team);
    TEAM_ID = teamSaved.getId();
    Team contextualTeam = getTeam(user);
    contextualTeam.setName("Contextual team");
    contextualTeam.setContextual(true);
    Team contextualTeamSaved = this.teamRepository.save(contextualTeam);
    TEAM_CONTEXTUAL_ID = contextualTeamSaved.getId();
    scenario.setTeams(
        new ArrayList<>() {
          {
            add(teamSaved);
            add(contextualTeamSaved);
          }
        });

    // Tag
    Tag tag = getTag();
    Tag tagSaved = this.tagRepository.save(tag);
    TAG_ID = tagSaved.getId();
    scenario.setTags(
        new HashSet<>() {
          {
            add(tagSaved);
          }
        });

    Scenario scenarioSaved = this.scenarioService.createScenario(scenario);
    SCENARIO_ID = scenarioSaved.getId();

    // Team Users
    ScenarioTeamUser scenarioTeamUser = new ScenarioTeamUser();
    scenarioTeamUser.setScenario(scenarioSaved);
    scenarioTeamUser.setTeam(teamSaved);
    scenarioTeamUser.setUser(userSaved);
    ScenarioTeamUser scenarioTeamUserContextual = new ScenarioTeamUser();
    scenarioTeamUserContextual.setScenario(scenarioSaved);
    scenarioTeamUserContextual.setTeam(contextualTeamSaved);
    scenarioTeamUserContextual.setUser(userSaved);
    scenario.setTeamUsers(List.of(scenarioTeamUser, scenarioTeamUserContextual));

    // Objective
    Objective objective = getObjective();
    objective.setScenario(scenarioSaved);
    scenario.setObjectives(
        new ArrayList<>() {
          {
            add(objective);
          }
        });

    // Document
    Document document = getDocumentJpeg();
    Document documentSaved = this.documentRepository.save(document);
    DOCUMENT_ID = documentSaved.getId();
    scenario.setDocuments(
        new ArrayList<>() {
          {
            add(documentSaved);
          }
        });

    // Article
    Document documentArticle = new Document();
    String documentArticleName = "A document for my article";
    documentArticle.setName(documentArticleName);
    documentArticle.setType("image/jpeg");
    Document documentArticleSaved = this.documentRepository.save(documentArticle);
    DOCUMENT_ARTICLE_ID = documentArticleSaved.getId();
    Channel channel = new Channel();
    channel.setName("A channel");
    Channel channelSaved = this.channelRepository.save(channel);
    CHANNEL_ID = channelSaved.getId();
    Article article = getArticle(channelSaved);
    article.setDocuments(
        new ArrayList<>() {
          {
            add(documentArticleSaved);
          }
        });
    scenarioSaved.setArticles(
        new ArrayList<>() {
          {
            add(article);
          }
        });

    // Lessons questions
    LessonsCategory lessonsCategory = new LessonsCategory();
    lessonsCategory.setName("Category");
    lessonsCategory.setScenario(scenarioSaved);
    lessonsCategory.setTeams(
        new ArrayList<>() {
          {
            add(teamSaved);
            add(contextualTeamSaved);
          }
        });
    LessonsCategory lessonsCategorySaved = this.lessonsCategoryRepository.save(lessonsCategory);
    LESSON_CATEGORY_ID = lessonsCategorySaved.getId();
    LessonsQuestion lessonsQuestion = new LessonsQuestion();
    lessonsQuestion.setContent("Content of my question");
    lessonsQuestion.setCategory(lessonsCategory);
    LessonsQuestion lessonsQuestionSaved = this.lessonsQuestionRepository.save(lessonsQuestion);
    LESSON_QUESTION_ID = lessonsQuestionSaved.getId();

    lessonsCategory.setQuestions(
        new ArrayList<>() {
          {
            add(lessonsQuestionSaved);
          }
        });
    scenario.setLessonsCategories(
        new ArrayList<>() {
          {
            add(lessonsCategorySaved);
          }
        });

    // Inject
    Inject inject =
        getInjectForEmailContract(
            this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow());
    inject.setTeams(
        new ArrayList<>() {
          {
            add(teamSaved);
            add(contextualTeamSaved);
          }
        });
    inject.setScenario(scenarioSaved);
    Inject injectSaved = this.injectRepository.save(inject);
    INJECT_ID = injectSaved.getId();
    scenario.setInjects(
        new HashSet<>() {
          {
            add(inject);
          }
        });

    // Variables
    Variable variable = new Variable();
    variable.setKey("keyvariable");
    variable.setValue("keyvalue");
    variable.setScenario(scenarioSaved);
    Variable variableSaved = this.variableRepository.save(variable);
    VARIABLE_ID = variableSaved.getId();

    // Default Simulation dashboard
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);
    settingRepository.save(
        settingRepository
            .findByKey(DEFAULT_SIMULATION_DASHBOARD.key())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () ->
                    new Setting(DEFAULT_SIMULATION_DASHBOARD.key(), customDashboardSaved.getId())));
    // -- EXECUTE --
    Exercise exercise = this.scenarioToExerciseService.toExercise(scenario, null, false);
    EXERCISE_ID = exercise.getId();
    Exercise exerciseSaved = this.loadService.exercise(EXERCISE_ID);

    // -- ASSERT --
    assertNotNull(exerciseSaved);
    assertEquals(name, exerciseSaved.getName());
    // User & Teams
    assertEquals(2, (long) exerciseSaved.getTeams().size());
    assertTrue(
        exerciseSaved.getTeams().stream().anyMatch(t -> teamSaved.getId().equals(t.getId())));
    assertTrue(
        exerciseSaved.getTeams().stream().anyMatch(t -> Boolean.TRUE.equals(t.getContextual())));
    // Team Users
    assertEquals(2, exerciseSaved.getTeamUsers().size());
    // Tags
    assertEquals(1, exerciseSaved.getTags().size());
    // Objectives
    assertEquals(1, exerciseSaved.getObjectives().size());
    assertEquals(OBJECTIVE_NAME, exerciseSaved.getObjectives().getFirst().getTitle());
    // Documents
    assertEquals(1, exerciseSaved.getDocuments().size());
    // Articles
    assertEquals(1, exerciseSaved.getArticles().size());
    assertEquals(ARTICLE_NAME, exerciseSaved.getArticles().getFirst().getName());
    assertEquals(
        documentArticleName,
        exerciseSaved.getArticles().getFirst().getDocuments().getFirst().getName());
    // Lessons questions
    assertEquals(1, exerciseSaved.getLessonsCategories().size());
    LessonsCategory exerciseLessonsCategory = exerciseSaved.getLessonsCategories().getFirst();
    assertEquals(1, exerciseLessonsCategory.getQuestions().size());
    assertTrue(
        exerciseLessonsCategory.getTeams().stream()
            .anyMatch(t -> teamSaved.getId().equals(t.getId())));
    assertTrue(
        exerciseLessonsCategory.getTeams().stream()
            .anyMatch(t -> Boolean.TRUE.equals(t.getContextual())));
    // Injects
    assertEquals(1, exerciseSaved.getInjects().size());
    // Dashboard
    assertEquals(customDashboardSaved.getId(), exerciseSaved.getCustomDashboard().getId());
  }
}
