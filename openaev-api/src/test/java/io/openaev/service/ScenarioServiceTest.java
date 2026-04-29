package io.openaev.service;

import static io.openaev.database.specification.TeamSpecification.fromScenario;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.TeamFixture.getTeam;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static java.time.Instant.now;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.SecurityCoverageComposer;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.ScenarioMapper;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScenarioServiceTest extends IntegrationTest {

  @Autowired ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private ArticleRepository articleRepository;
  @Autowired InjectRepository injectRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private HealthCheckUtils healthCheckUtils;

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;

  @Mock EnterpriseEditionService enterpriseEditionService;
  @Mock VariableService variableService;
  @Mock ChallengeService challengeService;
  @Autowired private TeamService teamService;
  @Mock FileService fileService;
  @Autowired private InjectDuplicateService injectDuplicateService;
  @Mock private InjectService injectService;
  @Mock private TagRuleService tagRuleService;
  @Mock private UserService userService;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private CustomDashboardService customDashboardService;
  @Mock private InjectorContractService injectorContractService;
  @InjectMocks private ScenarioService scenarioService;
  @Autowired private ScenarioMapper scenarioMapper;

  @Mock private WorkflowService workflowService;
  @Autowired private PreviewFeatureService previewFeatureService;

  @Mock private LicenseCacheManager licenseCacheManager;
  @Autowired private ExerciseMapper exerciseMapper;
  @Mock private ActionMetricCollector actionMetricCollector;

  private static String USER_ID;
  private static String TEAM_ID;
  private static String INJECT_ID;
  @Autowired private InjectorContractFixture injectorContractFixture;

  @BeforeEach
  void setUp() {
    scenarioService =
        new ScenarioService(
            scenarioRepository,
            teamRepository,
            userRepository,
            documentRepository,
            scenarioTeamUserRepository,
            articleRepository,
            exerciseMapper,
            actionMetricCollector,
            licenseCacheManager,
            enterpriseEditionService,
            variableService,
            challengeService,
            teamService,
            fileService,
            injectDuplicateService,
            tagRuleService,
            injectService,
            userService,
            platformSettingsService,
            customDashboardService,
            injectorContractService,
            injectRepository,
            lessonsCategoryRepository,
            tagRepository,
            healthCheckUtils,
            scenarioMapper,
            workflowService);
  }

  @AfterAll
  public void teardown() {
    this.userRepository.deleteById(USER_ID);
    this.teamRepository.deleteById(TEAM_ID);
    this.injectRepository.deleteById(INJECT_ID);
  }

  @DisplayName("Should delete injects at the same time as the scenario itself")
  @Test
  @Transactional
  public void shouldDeleteInjectsAtTheSameTImeAsTheScenarioItself() {
    InjectComposer.Composer injectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject());
    Scenario scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(injectWrapper)
            .persist()
            .get();
    entityManager.flush();
    entityManager.clear();

    String scenarioId = scenario.getId();

    scenarioService.deleteScenario(scenarioId);
    entityManager.flush();
    entityManager.clear();

    assertThat(injectRepository.findById(injectWrapper.get().getId())).isEmpty();
    assertThatThrownBy(() -> scenarioService.getScenarioById(scenarioId))
        .isInstanceOf(ElementNotFoundException.class);
  }

  @DisplayName(
      "Should null references from Security Coverage and Simulations when scenario deleted")
  @Test
  @Transactional
  public void shouldNullReferencesFromSecurityCoverageAndSimulationsWhenScenarioDeleted() {
    ExerciseComposer.Composer simulationWrapper =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
    ScenarioComposer.Composer scenarioWrapper =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
            .withSimulation(simulationWrapper);
    SecurityCoverageComposer.Composer securityCoverageWrapper =
        securityCoverageComposer
            .forSecurityCoverage(SecurityCoverageFixture.createDefaultSecurityCoverage())
            .withScenario(scenarioWrapper)
            .persist();
    entityManager.flush();
    entityManager.clear();

    String scenarioId = scenarioWrapper.get().getId();

    scenarioService.deleteScenario(scenarioId);
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(() -> scenarioService.getScenarioById(scenarioId))
        .isInstanceOf(ElementNotFoundException.class);

    assertThat(exerciseRepository.findById(simulationWrapper.get().getId()))
        .isPresent()
        .get()
        .satisfies(securityCoverage -> assertThat(securityCoverage.getScenario()).isNull());
    assertThat(securityCoverageRepository.findById(securityCoverageWrapper.get().getId()))
        .isPresent()
        .get()
        .satisfies(securityCoverage -> assertThat(securityCoverage.getScenario()).isNull());
  }

  @DisplayName("Should create new contextual teams during scenario duplication")
  @Test
  @Transactional(rollbackFor = Exception.class)
  void createNewContextualTeamsDuringScenarioDuplication() {
    // -- PREPARE --
    List<Team> scenarioTeams = new ArrayList<>();
    Team contextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName1", true));
    scenarioTeams.add(contextualTeam);
    Team noContextualTeam = this.teamRepository.save(getTeam(null, "fakeTeamName2", false));
    scenarioTeams.add(noContextualTeam);

    Inject inject = new Inject();
    inject.setTeams(scenarioTeams);
    Set<Inject> scenarioInjects = new HashSet<>();
    scenarioInjects.add(this.injectRepository.save(inject));
    Scenario scenario =
        this.scenarioRepository.save(ScenarioFixture.getScenario(scenarioTeams, scenarioInjects));

    // -- EXECUTE --
    Scenario scenarioDuplicated = scenarioService.getDuplicateScenario(scenario.getId());

    // -- ASSERT --
    assertNotEquals(scenario.getId(), scenarioDuplicated.getId());
    assertEquals(scenario.getFrom(), scenarioDuplicated.getFrom());
    assertEquals(2, scenarioDuplicated.getTeams().size());
    scenarioDuplicated
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
    assertEquals(1, scenarioDuplicated.getInjects().size());
    assertEquals(2, scenario.getInjects().getFirst().getTeams().size());
    scenarioDuplicated
        .getInjects()
        .getFirst()
        .getTeams()
        .forEach(
            injectTeam -> {
              if (injectTeam.getContextual()) {
                assertNotEquals(contextualTeam.getId(), injectTeam.getId());
                assertEquals(
                    scenarioDuplicated.getTeams().stream()
                        .filter(team -> team.getContextual().equals(true))
                        .findFirst()
                        .orElse(new Team())
                        .getId(),
                    injectTeam.getId());
              } else {
                assertEquals(noContextualTeam.getId(), injectTeam.getId());
              }
            });
  }

  @DisplayName("Should remove team from scenario")
  @Test
  void testRemoveTeams() {
    // -- PREPARE --
    User user = getUser();
    User userSaved = this.userRepository.saveAndFlush(user);
    USER_ID = userSaved.getId();
    Team team = getTeam(userSaved);
    Team teamSaved = this.teamRepository.saveAndFlush(team);
    TEAM_ID = teamSaved.getId();
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTeams(List.of(teamSaved));
    Scenario scenarioSaved = this.scenarioRepository.saveAndFlush(scenario);

    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject injectDefaultEmail = getInjectForEmailContract(injectorContract);
    injectDefaultEmail.setScenario(scenarioSaved);
    injectDefaultEmail.setTeams(List.of(teamSaved));
    Inject injectDefaultEmailSaved = this.injectRepository.saveAndFlush(injectDefaultEmail);
    INJECT_ID = injectDefaultEmailSaved.getId();

    // -- EXECUTE --
    this.scenarioService.removeTeams(scenarioSaved.getId(), List.of(teamSaved.getId()));

    // -- ASSERT --
    List<Team> teams = this.teamRepository.findAll(fromScenario(scenarioSaved.getId()));
    assertEquals(0, teams.size());
    Inject injectAssert = this.injectRepository.findById(INJECT_ID).orElseThrow();
    assertEquals(0, injectAssert.getTeams().size());
  }

  @Test
  public void testRunChecksWhenScenarioIsNull() {
    List<HealthCheck> healthchecks = scenarioService.runChecks(null);

    assertNull(healthchecks);
  }

  @Test
  @Transactional
  public void testRunChecksForSmtpIssue() {
    // PREPARE
    Inject inject = new Inject();
    Scenario scenario = new Scenario();
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    HealthCheck healthCheck =
        new HealthCheck(
            HealthCheck.Type.SMTP,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.ERROR,
            now());

    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of(healthCheck));

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.SMTP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.SMTP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  @Transactional
  public void testRunChecksForImapIssue() {
    // PREPARE
    Inject inject = new Inject();
    Scenario scenario = new Scenario();
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    HealthCheck healthCheck =
        new HealthCheck(
            HealthCheck.Type.IMAP,
            HealthCheck.Detail.SERVICE_UNAVAILABLE,
            HealthCheck.Status.WARNING,
            now());

    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of(healthCheck));

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.IMAP.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.IMAP, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.SERVICE_UNAVAILABLE, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.WARNING, healthCheckToVerify.getStatus());
  }

  @Test
  @Transactional
  public void testRunChecksForExecutorIssue() {
    // PREPARE
    Inject inject = new Inject();
    Scenario scenario = new Scenario();
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    HealthCheck healthCheck =
        new HealthCheck(
            HealthCheck.Type.AGENT_OR_EXECUTOR,
            HealthCheck.Detail.EMPTY,
            HealthCheck.Status.ERROR,
            now());
    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of(healthCheck));

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.AGENT_OR_EXECUTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.AGENT_OR_EXECUTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  @Transactional
  public void testRunChecksForCollectorIssue() {
    // PREPARE
    Inject inject = new Inject();
    Scenario scenario = new Scenario();
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    HealthCheck healthCheck =
        new HealthCheck(
            HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR,
            HealthCheck.Detail.EMPTY,
            HealthCheck.Status.ERROR,
            now());
    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of(healthCheck));

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.ERROR, healthCheckToVerify.getStatus());
  }

  @Test
  @Transactional
  public void testRunChecksForMissingContentIssue() {
    // PREPARE
    Inject inject = new Inject();
    Scenario scenario = new Scenario();
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    HealthCheck healthCheck =
        new HealthCheck(
            HealthCheck.Type.INJECT,
            HealthCheck.Detail.NOT_READY,
            HealthCheck.Status.WARNING,
            now());
    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of(healthCheck));

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.INJECT.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.INJECT, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.NOT_READY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.WARNING, healthCheckToVerify.getStatus());
  }

  @Test
  @Transactional
  public void testRunChecksForTeamsIssue() {
    // PREPARE
    Scenario scenario = new Scenario();

    Injector injector = new Injector();
    injector.setDependencies(
        new ExternalServiceDependency[] {
          ExternalServiceDependency.SMTP, ExternalServiceDependency.IMAP
        });
    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    injectorContract.getInjectors().clear();
    injectorContract.addInjector(injector);

    Inject inject = InjectFixture.createInject(injectorContract, "test");
    inject.setInjector(injector);
    scenario.setInjects(new HashSet<>(List.of(inject)));
    this.scenarioRepository.save(scenario);

    // MOCK
    when(this.injectService.runChecks(any())).thenReturn(List.of());

    // RUN
    List<HealthCheck> healthchecks = scenarioService.runChecks(scenario.getId());

    // VERIFY
    assertFalse(healthchecks.isEmpty());

    HealthCheck healthCheckToVerify =
        healthchecks.stream()
            .filter(hc -> HealthCheck.Type.TEAMS.equals(hc.getType()))
            .findFirst()
            .orElse(new HealthCheck(null, null, null, now()));
    assertEquals(HealthCheck.Type.TEAMS, healthCheckToVerify.getType());
    assertEquals(HealthCheck.Detail.EMPTY, healthCheckToVerify.getDetail());
    assertEquals(HealthCheck.Status.WARNING, healthCheckToVerify.getStatus());
  }
}
