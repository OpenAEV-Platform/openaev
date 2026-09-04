package io.openaev.service;

import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.utils.fixtures.ExerciseFixture.getExercise;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.TeamFixture.getTeam;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.PauseExerciseService;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioRecurrenceService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.ObjectiveFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExerciseServiceIntegrationTest extends IntegrationTest {

  @Mock EnterpriseEditionService enterpriseEditionService;
  @Mock InjectDuplicateService injectDuplicateService;
  @Mock VariableService variableService;
  @Autowired private TeamService teamService;
  @Autowired private TagRuleService tagRuleService;
  @Autowired private DocumentService documentService;
  @Autowired private InjectService injectService;
  @Autowired private UserService userService;
  @Autowired private GrantService grantService;
  @Autowired private ExerciseTeamUserService exerciseTeamUserService;

  @Autowired private ExerciseMapper exerciseMapper;
  @Autowired private InjectMapper injectMapper;
  @Autowired private ResultUtils resultUtils;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ObjectiveRepository objectiveRepository;

  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PauseRepository pauseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private LessonsQuestionRepository lessonsQuestionRepository;
  @Autowired private LessonsAnswerRepository lessonsAnswerRepository;
  @Autowired private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Autowired private LicenseCacheManager licenseCacheManager;
  @Autowired private InjectExpectationMapper injectExpectationMapper;
  @Autowired private ScenarioRecurrenceService scenarioRecurrenceService;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private LessonsService lessonsService;
  @Autowired private FileService fileService;
  @Autowired private PauseExerciseService pauseExerciseService;
  @Autowired private UrlAccessTokenService urlAccessTokenService;

  @Autowired private WorkflowService workflowService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private io.openaev.healthcheck.utils.HealthCheckUtils healthCheckUtils;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private AttackPathExecutionIngestionService attackPathExecutionService;
  @Autowired private AutonomousRunRepository autonomousRunRepository;
  @Autowired private BulkDeleteExecutor bulkDeleteExecutor;

  private static String USER_ID;
  private static String TEAM_ID;
  private static String INJECT_ID;

  @InjectMocks private ExerciseService exerciseService;
  @Autowired private StepService stepService;

  @BeforeEach
  void setUp() {
    exerciseService =
        new ExerciseService(
            enterpriseEditionService,
            injectDuplicateService,
            teamService,
            variableService,
            tagRuleService,
            documentService,
            injectService,
            userService,
            grantService,
            exerciseTeamUserService,
            exerciseMapper,
            injectMapper,
            resultUtils,
            actionMetricCollector,
            licenseCacheManager,
            assetRepository,
            assetGroupRepository,
            injectExpectationRepository,
            articleRepository,
            exerciseRepository,
            bulkDeleteExecutor,
            injectStatusRepository,
            pauseRepository,
            lessonsQuestionRepository,
            teamRepository,
            userRepository,
            exerciseTeamUserRepository,
            injectRepository,
            lessonsAnswerRepository,
            lessonsCategoryRepository,
            lessonsService,
            urlAccessTokenService,
            injectExpectationMapper,
            scenarioRecurrenceService,
            workflowService,
            pauseExerciseService,
            fileService,
            stepService,
            healthCheckUtils,
            eventPublisher,
            attackPathExecutionService,
            autonomousRunRepository);
  }

  @AfterAll
  public void teardown() {
    if (USER_ID != null) this.userRepository.deleteById(USER_ID);
    if (TEAM_ID != null) this.teamRepository.deleteById(TEAM_ID);
    if (INJECT_ID != null) this.injectRepository.deleteById(INJECT_ID);
  }

  @DisplayName("Should create new contextual teams while exercise duplication")
  @Test
  @Transactional(rollbackFor = Exception.class)
  void createNewContextualTeamsWhileExerciseDuplication() {
    // -- PREPARE --
    List<Team> exerciseTeams = new ArrayList<>();
    Team contextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName1", true));
    exerciseTeams.add(contextualTeam);
    Team noContextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName2", false));
    exerciseTeams.add(noContextualTeam);
    Exercise exercise = getExercise(exerciseTeams);
    exercise.setFrom("test@test.com");
    exercise.setLessonsEnabled(true);
    this.exerciseRepository.save(exercise);
    entityManager.flush();

    // -- EXECUTE --
    Exercise exerciseDuplicated = exerciseService.getDuplicateExercise(exercise.getId());

    // -- ASSERT --
    assertNotEquals(exercise.getId(), exerciseDuplicated.getId());
    assertTrue(exerciseDuplicated.isLessonsEnabled());
    assertEquals(2, exerciseDuplicated.getTeams().size());
    exerciseDuplicated
        .getTeams()
        .forEach(
            team -> {
              if (team.getContextual()) {
                assertNotEquals(contextualTeam.getId(), team.getId());
                assertEquals(contextualTeam.getName(), team.getName());
              } else {
                assertEquals(noContextualTeam.getId(), team.getId());
              }
            });
  }

  @DisplayName("Should skip lesson data during exercise duplication when lessons are disabled")
  @Test
  @Transactional(rollbackFor = Exception.class)
  void shouldSkipLessonDataDuringExerciseDuplicationWhenLessonsDisabled() {
    Exercise exercise = getExercise();
    exercise.setFrom("test@test.com");
    exercise.setLessonsEnabled(false);
    this.exerciseRepository.save(exercise);

    Objective objective = ObjectiveFixture.getObjective();
    objective.setExercise(exercise);
    this.objectiveRepository.save(objective);

    LessonsCategory lessonsCategory = LessonsCategoryFixture.createLessonCategory();
    lessonsCategory.setExercise(exercise);
    this.lessonsCategoryRepository.save(lessonsCategory);

    entityManager.flush();

    Exercise exerciseDuplicated = exerciseService.getDuplicateExercise(exercise.getId());

    assertNotEquals(exercise.getId(), exerciseDuplicated.getId());
    assertFalse(exerciseDuplicated.isLessonsEnabled());
    assertTrue(exerciseDuplicated.getObjectives().isEmpty());
    assertTrue(exerciseDuplicated.getLessonsCategories().isEmpty());
  }

  @DisplayName("Stopping a chained simulation keeps its injects")
  @Test
  @Transactional(rollbackFor = Exception.class)
  void given_runningChainedSimulation_should_keepInjectsOnStop() throws ChainingException {
    // -- PREPARE --
    // Stopping used to delete every inject of a manual chained simulation, which emptied the
    // Execution screen while the attack path (cleared only on reset) still showed the same run.
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setFrom("test@test.com");
    exercise.setStatus(ExerciseStatus.RUNNING);
    Exercise exerciseSaved = this.exerciseRepository.save(exercise);

    // The cancel path only touches injects when the simulation has a RUN workflow.
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowRun.setSimulation(exerciseSaved);
    this.workflowRepository.save(workflowRun);

    // The inject has no status yet (pending): stop must keep it too - only Reset clears the
    // record, and the autonomous-only carve-out is exactly about pending injects like this one.
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject pendingInject = getInjectForEmailContract(injectorContract);
    pendingInject.setExercise(exerciseSaved);
    Inject pendingInjectSaved = this.injectRepository.save(pendingInject);
    entityManager.flush();

    // -- EXECUTE --
    this.exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, exerciseSaved.getId());
    entityManager.flush();

    // -- ASSERT --
    assertEquals(
        List.of(pendingInjectSaved.getId()),
        this.injectRepository.findByExerciseId(exerciseSaved.getId()).stream()
            .map(Inject::getId)
            .toList());
  }

  @DisplayName("Should remove team from exercise")
  @Test
  void testRemoveTeams() {
    // -- PREPARE --
    User user = getUser();
    User userSaved = this.userRepository.saveAndFlush(user);
    USER_ID = userSaved.getId();
    Team team = getTeam(userSaved);
    Team teamSaved = this.teamRepository.saveAndFlush(team);
    TEAM_ID = teamSaved.getId();
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setTeams(List.of(teamSaved));
    exercise.setFrom(user.getEmail());
    Exercise exerciseSaved = this.exerciseRepository.saveAndFlush(exercise);
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject injectDefaultEmail = getInjectForEmailContract(injectorContract);
    injectDefaultEmail.setExercise(exerciseSaved);
    injectDefaultEmail.setTeams(List.of(teamSaved));
    Inject injectDefaultEmailSaved = this.injectRepository.saveAndFlush(injectDefaultEmail);
    INJECT_ID = injectDefaultEmailSaved.getId();

    // -- EXECUTE --
    this.exerciseService.removeTeams(exerciseSaved.getId(), List.of(teamSaved.getId()));

    // -- ASSERT --
    List<Team> teams = this.teamRepository.findAll(fromExercise(exerciseSaved.getId()));
    assertEquals(0, teams.size());
    Inject injectAssert = this.injectRepository.findById(INJECT_ID).orElseThrow();
    assertEquals(0, injectAssert.getTeams().size());
  }
}
