package io.openbas.service;

import static io.openbas.database.specification.TeamSpecification.fromScenario;
import static io.openbas.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openbas.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openbas.utils.fixtures.TeamFixture.getTeam;
import static io.openbas.utils.fixtures.UserFixture.getUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

import io.openbas.IntegrationTest;
import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.*;
import io.openbas.database.model.Tag;
import io.openbas.database.repository.*;
import io.openbas.ee.Ee;
import io.openbas.rest.dashboard.DashboardService;
import io.openbas.rest.inject.service.InjectDuplicateService;
import io.openbas.rest.inject.service.InjectService;
import io.openbas.telemetry.metric_collectors.ActionMetricCollector;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.AssetGroupFixture;
import io.openbas.utils.fixtures.ScenarioFixture;
import io.openbas.utils.fixtures.TagFixture;
import io.openbas.utils.mapper.ExerciseMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScenarioServiceTest extends IntegrationTest {

  @Autowired ScenarioRepository scenarioRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private ArticleRepository articleRepository;
  @Mock ScenarioRepository mockScenarioRepository;
  @Autowired InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;

  @Mock Ee eeService;
  @Mock GrantService grantService;
  @Mock VariableService variableService;
  @Mock ChallengeService challengeService;
  @Autowired private TeamService teamService;
  @Mock FileService fileService;
  @Autowired private InjectDuplicateService injectDuplicateService;
  @Mock private InjectService injectService;
  @Mock private TagRuleService tagRuleService;
  @Mock private UserService userService;
  @Mock private DashboardService dashboardService;
  @InjectMocks private ScenarioService scenarioService;

  @Mock private LicenseCacheManager licenseCacheManager;
  @Autowired private ExerciseMapper exerciseMapper;
  @Mock private ActionMetricCollector actionMetricCollector;

  private static String USER_ID;
  private static String TEAM_ID;
  private static String INJECT_ID;

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
            eeService,
            variableService,
            challengeService,
            teamService,
            fileService,
            injectDuplicateService,
            tagRuleService,
            injectService,
            userService,
            dashboardService,
            injectRepository,
            lessonsCategoryRepository);
  }

  void setUpWithMockRepository() {
    scenarioService =
        new ScenarioService(
            mockScenarioRepository,
            teamRepository,
            userRepository,
            documentRepository,
            scenarioTeamUserRepository,
            articleRepository,
            exerciseMapper,
            actionMetricCollector,
            licenseCacheManager,
            eeService,
            variableService,
            challengeService,
            teamService,
            fileService,
            injectDuplicateService,
            tagRuleService,
            injectService,
            userService,
            dashboardService,
            injectRepository,
            lessonsCategoryRepository);
  }

  @AfterAll
  public void teardown() {
    this.userRepository.deleteById(USER_ID);
    this.teamRepository.deleteById(TEAM_ID);
    this.injectRepository.deleteById(INJECT_ID);
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

    InjectorContract injectorContract =
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
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
  public void testUpdateScenario_WITH_applyRule_true() {
    setUpWithMockRepository();
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(mockScenarioRepository.save(scenario)).thenReturn(scenario);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(true);

    scenarioService.updateScenario(scenario, currentTags, true);

    scenario
        .getInjects()
        .forEach(
            inject ->
                verify(injectService)
                    .applyDefaultAssetGroupsToInject(inject.getId(), assetGroupsToAdd));
    verify(mockScenarioRepository).save(scenario);
  }

  @Test
  public void testUpdateScenario_WITH_applyRule_true_and_manual_inject() {
    setUpWithMockRepository();
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(mockScenarioRepository.save(scenario)).thenReturn(scenario);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(false);

    scenarioService.updateScenario(scenario, currentTags, true);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
    verify(mockScenarioRepository).save(scenario);
  }

  @Test
  public void testUpdateScenario_WITH_applyRule_false() {
    setUpWithMockRepository();
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Scenario scenario = ScenarioFixture.getScenario(null, Set.of(inject1, inject2));
    scenario.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(mockScenarioRepository.save(scenario)).thenReturn(scenario);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(true);

    scenarioService.updateScenario(scenario, currentTags, false);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }
}
