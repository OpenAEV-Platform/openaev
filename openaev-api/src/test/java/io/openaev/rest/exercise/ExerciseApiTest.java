package io.openaev.rest.exercise;

import static io.openaev.database.model.TenantSettingKeys.TENANT_SIMULATION_DASHBOARD;
import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.util.AssertionErrors.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injector_contract.ContractCardinality;
import io.openaev.injector_contract.fields.ContractSelect;
import io.openaev.rest.exercise.form.*;
import io.openaev.rest.inject.SimulationInjectApi;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@AutoConfigureMockMvc
@TestInstance(PER_CLASS)
@Transactional
public class ExerciseApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  private static final List<String> COMPOSER_EXERCISE_IDS = new ArrayList<>();
  private static final List<String> EXERCISE_IDS = new ArrayList<>();
  private static final List<String> USER_IDS = new ArrayList<>();
  private static final List<String> TEAM_IDS = new ArrayList<>();

  @DisplayName("Create simulation succeed with default dashboard")
  @Test
  @WithMockUser(isAdmin = true)
  void given_exercise_creation_should_set_default_custom_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);

    ExerciseInput exerciseInput = new ExerciseInput();
    String name = "My scenario";
    exerciseInput.setName(name);

    settingRepository.save(
        settingRepository
            .findByKeyAndTenantId(
                TENANT_SIMULATION_DASHBOARD.key(), TenantContext.getCurrentTenant())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () -> {
                  Setting s =
                      new Setting(TENANT_SIMULATION_DASHBOARD.key(), customDashboardSaved.getId());
                  s.setTenant(new Tenant(TenantContext.getCurrentTenant()));
                  return s;
                }));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI)
                    .content(asJsonString(exerciseInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.exercise_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String newExerciseId = JsonPath.read(response, "$.exercise_id");
    Exercise newExercise = this.exerciseRepository.findById(newExerciseId).orElseThrow();
    assertEquals(customDashboardSaved.getId(), newExercise.getCustomDashboard().getId());
  }

  @Nested
  @DisplayName("Retrieving exercise informations")
  class RetrievingExercises {
    @Test
    @DisplayName("Retrieving players by exercise")
    @WithMockUser(isAdmin = true)
    void retrievingPlayersByExercise() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
      User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));
      USER_IDS.addAll(Arrays.asList(userTom.getId(), userBen.getId()));
      Team teamA = teamRepository.save(TeamFixture.getTeam(userTom, "TeamA", false));
      Team teamB = teamRepository.save(TeamFixture.getTeam(userBen, "TeamB", false));
      TEAM_IDS.addAll(Arrays.asList(teamA.getId(), teamB.getId()));

      Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
      exercise.setTeams(Arrays.asList(teamA, teamB));
      Exercise exerciseSaved = exerciseRepository.save(exercise);
      EXERCISE_IDS.add(exerciseSaved.getId());

      ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
      exerciseTeamUser.setExercise(exerciseSaved);
      exerciseTeamUser.setTeam(teamA);
      exerciseTeamUser.setUser(userTom);
      ExerciseTeamUser exerciseTeamUser2 = new ExerciseTeamUser();
      exerciseTeamUser2.setExercise(exerciseSaved);
      exerciseTeamUser2.setTeam(teamB);
      exerciseTeamUser2.setUser(userBen);
      exerciseTeamUserRepository.saveAll(Arrays.asList(exerciseTeamUser, exerciseTeamUser2));

      mvc.perform(
              get(EXERCISE_URI + "/" + exerciseSaved.getId() + "/players")
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(
              jsonPath("$[*].user_id")
                  .value(
                      org.hamcrest.Matchers.containsInAnyOrder(userTom.getId(), userBen.getId())));
    }

    @Test
    @DisplayName("Get global score for exercises")
    @WithMockUser(isAdmin = true)
    void getGlobalScoreForExercises() throws Exception {
      Exercise exercise1 = ExerciseFixture.createDefaultCrisisExercise();
      Exercise exercise1Saved = exerciseRepository.save(exercise1);
      EXERCISE_IDS.add(exercise1Saved.getId());

      Exercise exercise2 = ExerciseFixture.createDefaultIncidentResponseExercise();
      Exercise exercise2Saved = exerciseRepository.save(exercise2);
      EXERCISE_IDS.add(exercise2Saved.getId());

      ExercisesGlobalScoresInput input =
          new ExercisesGlobalScoresInput(List.of(exercise1Saved.getId(), exercise2Saved.getId()));

      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/global-scores")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertEquals(
          "[]",
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + exercise1Saved.getId())
              .toString());
      assertEquals(
          "[]",
          JsonPath.read(response, "$.global_scores_by_exercise_ids." + exercise2Saved.getId())
              .toString());
    }
  }

  @Test
  @DisplayName("Get scenario from exercise id")
  @WithMockUser(isAdmin = true)
  void givenExerciseId_whenGettingScenarioFromExercise_thenReturnScenario() throws Exception {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    Scenario scenarioSaved = scenarioRepository.save(scenario);

    Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
    exercise.setScenario(scenarioSaved);

    Exercise exerciseSaved = exerciseRepository.save(exercise);

    String response =
        mvc.perform(
                get(EXERCISE_URI + "/" + exerciseSaved.getId() + "/scenario")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(scenarioSaved.getId(), JsonPath.read(response, "$.scenario_id"));
  }

  @DisplayName("Check if a rule applies when a rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    io.openaev.database.model.Tag tag2 = TagFixture.getTagNoId();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);

    AssetGroup assetGroup =
        assetGroupRepository.save(AssetGroupFixture.createDefaultAssetGroup("assetGroup"));
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag2);
    tagRule.setAssetGroups(List.of(assetGroup));
    this.tagRuleRepository.save(tagRule);

    Exercise exercise = this.exerciseRepository.save(ExerciseFixture.createDefaultCrisisExercise());

    CheckExerciseRulesInput input = new CheckExerciseRulesInput();
    input.setNewTags(List.of(tag2.getId()));
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + exercise.getId() + "/check-rules")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    assertEquals(true, JsonPath.read(response, "$.rules_found"));
  }

  @DisplayName("Check if a rule applies when no rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_no_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTagNoId();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);
    CheckExerciseRulesInput input = new CheckExerciseRulesInput();
    input.setNewTags(List.of(tag2.getId()));

    Exercise exercise = this.exerciseRepository.save(ExerciseFixture.createDefaultCrisisExercise());

    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI + "/" + exercise.getId() + "/check-rules")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response);
    assertEquals(false, JsonPath.read(response, "$.rules_found"));
  }

  @Nested
  @DisplayName("Lock Exercise EE feature")
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  class LockExerciseEEFeature {

    private Exercise getExercise(@Nullable Executor executor) {
      Executor executorToRun = (executor == null) ? executorFixture.getDefaultExecutor() : executor;
      ExerciseComposer.Composer newExerciseComposer =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultAttackExercise(Instant.now()))
              .withInject(
                  injectComposer
                      .forInject(InjectFixture.getDefaultInject())
                      .withEndpoint(
                          endpointComposer
                              .forEndpoint(EndpointFixture.createEndpoint())
                              .withAgent(
                                  agentComposer.forAgent(
                                      AgentFixture.createDefaultAgentSession(executorToRun))))
                      .withInjectStatus(
                          injectStatusComposer.forInjectStatus(
                              InjectStatusFixture.createDraftInjectStatus())))
              .persist();
      COMPOSER_EXERCISE_IDS.add(newExerciseComposer.get().getId());
      return newExerciseComposer.get();
    }

    @Test
    @DisplayName("Throw license restricted error when launch exercise with Crowdstrike")
    @WithMockUser(withCapabilities = {Capability.LAUNCH_ASSESSMENT})
    void given_crowdstrike_should_not_launchExercise() throws Exception {
      Exercise exercise = getExercise(executorFixture.getTaniumExecutor());
      ExerciseUpdateStatusInput input = new ExerciseUpdateStatusInput();
      input.setStatus(ExerciseStatus.RUNNING);

      mvc.perform(
              put(EXERCISE_URI + "/" + exercise.getId() + "/status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when schedule exercise with Tanium")
    void given_tanium_should_not_scheduleExercise() throws Exception {
      Exercise exercise = getExercise(executorFixture.getTaniumExecutor());
      ExerciseUpdateStartDateInput input = new ExerciseUpdateStartDateInput();

      mvc.perform(
              put(EXERCISE_URI + "/" + exercise.getId() + "/start-date")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when schedule exercise with Sentinel One")
    void given_sentinelone_should_not_scheduleExercise() throws Exception {
      Exercise exercise = getExercise(executorFixture.getSentineloneExecutor());
      ExerciseUpdateStartDateInput input = new ExerciseUpdateStartDateInput();

      mvc.perform(
              put(EXERCISE_URI + "/" + exercise.getId() + "/start-date")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when add Tanium on scheduled scenario")
    void given_taniumAsset_should_not_beAddedToScheduledExercise() throws Exception {
      Exercise exercise = getExercise(null);

      // Create endpoint with tanium agent
      Asset assetToAdd =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withAgent(
                  agentComposer.forAgent(
                      AgentFixture.createDefaultAgentSession(executorFixture.getTaniumExecutor())))
              .persist()
              .get();

      InjectInput input = new InjectInput();
      input.setTitle(exercise.getInjects().getFirst().getTitle());
      input.setAssets(List.of(assetToAdd.getId()));

      mvc.perform(
              put("/api/injects/"
                      + exercise.getId()
                      + "/"
                      + exercise.getInjects().getFirst().getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }
  }

  @Test
  @DisplayName("Should enable all users of newly added teams when replacing exercise teams")
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void replacingTeamsShouldEnableNewTeamUsers() throws Exception {
    // -- PREPARE --
    User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
    User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));
    USER_IDS.addAll(Arrays.asList(userTom.getId(), userBen.getId()));

    Team teamA = TeamFixture.getTeam(userTom, "TeamA", false);
    teamA.setUsers(List.of(userTom));
    teamRepository.save(teamA);
    Team teamB = TeamFixture.getTeam(userBen, "TeamB", false);
    teamB.setUsers(List.of(userBen));
    teamRepository.save(teamB);

    TEAM_IDS.addAll(Arrays.asList(teamA.getId(), teamB.getId()));

    Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
    exercise.setTeams(Collections.singletonList(teamA));
    Exercise exerciseSaved = exerciseRepository.save(exercise);
    EXERCISE_IDS.add(exerciseSaved.getId());

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(EXERCISE_URI + "/" + exerciseSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<ExerciseTeamUser> links = exerciseTeamUserRepository.findAll();

    ExerciseTeamUser link = links.getFirst();
    assertEquals(exerciseSaved.getId(), link.getExercise().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }

  @Nested
  @Transactional
  @DisplayName("replaceTeams - scoped cleanup and no cross-exercise side effects")
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  class ReplaceTeamsIntegration {

    @Test
    @DisplayName(
        "Deselecting a team should remove its exercise-team-user links only for that exercise")
    void deselectedTeamShouldRemoveLinksOnlyForCurrentExercise() throws Exception {
      // -- PREPARE --
      User userTom = userRepository.save(UserFixture.getUser("Tom", "RT1", "tom-rt1@fake.email"));
      USER_IDS.add(userTom.getId());

      Team sharedTeam = new Team();
      sharedTeam.setName("SharedTeam-RT1");
      sharedTeam.setUsers(List.of(userTom));
      teamRepository.save(sharedTeam);
      TEAM_IDS.add(sharedTeam.getId());

      // exerciseA
      Exercise exerciseA = ExerciseFixture.createDefaultCrisisExercise();
      exerciseA.setTeams(List.of(sharedTeam));
      Exercise exerciseASaved = exerciseRepository.save(exerciseA);
      EXERCISE_IDS.add(exerciseASaved.getId());

      ExerciseTeamUser linkA = new ExerciseTeamUser();
      linkA.setExercise(exerciseASaved);
      linkA.setTeam(sharedTeam);
      linkA.setUser(userTom);
      exerciseTeamUserRepository.save(linkA);

      // exerciseB
      Exercise exerciseB = ExerciseFixture.createDefaultCrisisExercise();
      exerciseB.setTeams(List.of(sharedTeam));
      Exercise exerciseBSaved = exerciseRepository.save(exerciseB);
      EXERCISE_IDS.add(exerciseBSaved.getId());

      ExerciseTeamUser linkB = new ExerciseTeamUser();
      linkB.setExercise(exerciseBSaved);
      linkB.setTeam(sharedTeam);
      linkB.setUser(userTom);
      exerciseTeamUserRepository.save(linkB);

      // -- ACT : We remove sharedTeam from exerciseA --
      ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
      input.setTeamIds(List.of());

      mvc.perform(
              put(EXERCISE_URI + "/" + exerciseASaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      // The link of exerciseA must have been deleted
      assertTrue(
          exerciseTeamUserRepository.rawByExerciseIds(List.of(exerciseASaved.getId())).isEmpty(),
          "The link exercise-team-user of exerciseA should have been deleted");

      // The link of exerciseB must still exist
      var linksB = exerciseTeamUserRepository.rawByExerciseIds(List.of(exerciseBSaved.getId()));
      assertEquals(1, linksB.size(), "The link exercise-team-user of exerciseB should be intact");
      assertEquals(sharedTeam.getId(), linksB.getFirst().getTeam_id());

      // exerciseA should not have any team anymore
      List<Team> exerciseATeams = teamRepository.findAll(fromExercise(exerciseASaved.getId()));
      assertTrue(exerciseATeams.isEmpty());

      // exerciseB should still have sharedTeam in its team list
      List<Team> exerciseBTeams = teamRepository.findAll(fromExercise(exerciseBSaved.getId()));
      assertEquals(1, exerciseBTeams.size());
      assertEquals(sharedTeam.getId(), exerciseBTeams.getFirst().getId());
    }

    @Test
    @DisplayName("Replacing teams should update the exercise team list in database")
    void replacingTeamsShouldPersistNewTeamListInDatabase() throws Exception {
      // -- PREPARE --
      Team teamToRemove = new Team();
      teamToRemove.setName("TeamToRemove-RT3");
      teamRepository.save(teamToRemove);
      TEAM_IDS.add(teamToRemove.getId());

      Team teamToKeep = new Team();
      teamToKeep.setName("TeamToKeep-RT3");
      teamRepository.save(teamToKeep);
      TEAM_IDS.add(teamToKeep.getId());

      Team teamToAdd = new Team();
      teamToAdd.setName("TeamToAdd-RT3");
      teamRepository.save(teamToAdd);
      TEAM_IDS.add(teamToAdd.getId());

      Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
      exercise.setTeams(List.of(teamToRemove, teamToKeep));
      Exercise exerciseSaved = exerciseRepository.save(exercise);
      EXERCISE_IDS.add(exerciseSaved.getId());

      ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
      input.setTeamIds(List.of(teamToKeep.getId(), teamToAdd.getId()));

      // -- ACT --
      mvc.perform(
              put(EXERCISE_URI + "/" + exerciseSaved.getId() + "/teams/replace")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(input))
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- ASSERT --
      List<String> teamIds =
          teamRepository.findAll(fromExercise(exerciseSaved.getId())).stream()
              .map(Team::getId)
              .toList();

      assertEquals(2, teamIds.size());
      assertTrue(teamIds.contains(teamToKeep.getId()), "teamToKeep should still be present.");
      assertTrue(teamIds.contains(teamToAdd.getId()), "teamToAdd should be present");
      assertFalse(teamIds.contains(teamToRemove.getId()), "teamToRemove should have been removed");
    }
  }

  @Nested
  @DisplayName("Inject check")
  @WithMockUser(isAdmin = true)
  @Transactional
  class SimulationInjectCheck {

    @Autowired private SimulationInjectApi simulationInjectApi;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private InjectService injectService;
    @Autowired private InjectDuplicateService injectDuplicateService;
    @Autowired private InjectMapper injectMapper;
    @Autowired private InjectRepository injectRepository;
    @Autowired private InjectorContractRepository injectorContractRepository;

    private Exercise exercise;
    private String exerciseId;
    private String validInjectorContractId;

    @BeforeEach
    void setUp() throws JsonProcessingException {
      exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
      exerciseId = exercise.getId();

      ContractSelect obfuscatorSelect =
          new ContractSelect("obfuscator", "Obfuscators", ContractCardinality.One);
      obfuscatorSelect.setChoices(Map.of("plain-text", "plain-text", "base64", "base64"));
      Injector i = InjectorFixture.createDefaultPayloadInjector();
      i.setDependencies(new ExternalServiceDependency[] {ExternalServiceDependency.NUCLEI});
      InjectorContract icf =
          InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
              i, null, List.of(obfuscatorSelect));
      InjectorContract ic = injectorContractComposer.forInjectorContract(icf).persist().get();
      // On récupère un InjectorContract réellement présent en base (EMAIL_DEFAULT est enregistré au
      // démarrage)
      validInjectorContractId =
          injectorContractRepository
              .findById(ic.getId())
              .map(InjectorContract::getId)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          " injector contract not found in DB — check test data initialization"));
    }

    @Test
    @DisplayName(
        "createInjectForExercise: should return InjectOutput with inject_id and inject_checks")
    void createInjectForExercise_shouldReturnInjectOutputWithChecks() {
      // -- PREPARE --
      InjectInput input = new InjectInput();
      input.setTitle("Test inject");
      input.setInjectorContract(validInjectorContractId);
      input.setDependsDuration(0L);

      // -- EXECUTE --
      InjectOutput result = simulationInjectApi.createInjectForExercise(exerciseId, input);

      // -- ASSERT --
      assertNotNull(result, "La réponse ne doit pas être null");
      assertNotNull(result.getId(), "inject_id ne doit pas être null");
      // Sans teams/assets/contenu, l'inject ne peut pas être ready
      assertFalse(result.isReady(), "L'inject sans contenu complet ne doit pas être ready");
      assertTrue(injectRepository.existsById(result.getId()));
    }

    @Test
    @DisplayName("createInjectForExercise: inject_checks should reflect missing content")
    void createInjectForExercise_checksShouldReflectMissingContent() {
      // -- PREPARE --
      InjectInput input = new InjectInput();
      input.setTitle("Inject with missing content");
      input.setInjectorContract(validInjectorContractId);
      input.setDependsDuration(0L);

      // -- EXECUTE --
      InjectOutput result = simulationInjectApi.createInjectForExercise(exerciseId, input);

      // -- ASSERT --
      assertNotNull(result);
      assertFalse(result.isReady(), "Un inject sans teams/assets/contenu ne doit pas être ready");
    }

    @Test
    @DisplayName(
        "duplicateInjectForExercise: should return InjectOutput with new inject_id and inject_checks")
    void duplicateInjectForExercise_shouldReturnInjectOutputWithChecks() {
      // -- PREPARE --
      InjectInput input = new InjectInput();
      input.setTitle("Original inject");
      input.setInjectorContract(validInjectorContractId);
      input.setDependsDuration(0L);

      InjectOutput original = simulationInjectApi.createInjectForExercise(exerciseId, input);
      String originalInjectId = original.getId();

      // -- EXECUTE --
      InjectOutput result =
          simulationInjectApi.duplicateInjectForExercise(exerciseId, originalInjectId);

      // -- ASSERT --
      assertNotNull(result, "La réponse ne doit pas être null");
      assertNotNull(result.getId(), "inject_id ne doit pas être null");
      assertNotEquals("", originalInjectId, result.getId());
      assertFalse(result.isReady());
      assertTrue(
          result.getTitle().contains("duplicate"),
          "Le titre de l'inject dupliqué doit contenir 'Duplicate'");
      assertTrue(injectRepository.existsById(result.getId()));
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(
      withCapabilities = {
        Capability.MANAGE_ASSESSMENT,
        Capability.ACCESS_ASSESSMENT,
        Capability.DELETE_ASSESSMENT
      })
  class TenantIsolation {

    @Test
    @DisplayName("Exercise created in tenant X should NOT be readable from tenant Y")
    // @WithoutRls
    // uncomment to fail the test: native query caught by RLS
    void given_exerciseInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT));

      CreateExerciseInput input = new CreateExerciseInput();
      input.setName("RLS Isolation Test Exercise");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/exercises")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String exerciseId = JsonPath.read(createResponse, "$.exercise_id");

      // -------- Act — read from tenant Y (expect 404) --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/exercises/" + exerciseId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Exercise created in tenant X should be readable from tenant X")
    void given_exerciseInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      CreateExerciseInput input = new CreateExerciseInput();
      input.setName("Same Tenant Exercise");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/exercises")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String exerciseId = JsonPath.read(createResponse, "$.exercise_id");

      // -------- Act & Assert — read from same tenant should succeed --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/exercises/" + exerciseId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_name").value("Same Tenant Exercise"));
    }

    @Test
    @DisplayName("Exercise search in tenant Y should NOT return exercises from tenant X")
    void given_exerciseInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT));

      CreateExerciseInput input = new CreateExerciseInput();
      input.setName("CrossTenantSearchExercise");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/exercises")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Evict L1 cache so findById() hits the DB (where RLS filters)
      entityManager.flush();
      entityManager.clear();

      // -------- Act — search from tenant Y --------
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantSearchExercise");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/exercises/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — no results from tenant X --------
      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Exercise created in tenant X should NOT be updatable from tenant Y")
    // @WithoutRls // uncomment to fail the test: native query caught by RLS
    void given_exerciseInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      CreateExerciseInput input = new CreateExerciseInput();
      input.setName("Update Isolation Test Exercise");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/exercises")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String exerciseId = JsonPath.read(createResponse, "$.exercise_id");

      // Evict L1 cache so findById() hits the DB (where RLS filters)
      entityManager.flush();
      entityManager.clear();

      // -------- Act — update from tenant Y --------
      UpdateExerciseInput updateInput = new UpdateExerciseInput();
      updateInput.setName("Hijacked Name");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/exercises/" + exerciseId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Exercise created in tenant X should NOT be deletable from tenant Y")
    // @WithoutRls // uncomment to fail the test: native query caught by RLS
    void given_exerciseInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      CreateExerciseInput input = new CreateExerciseInput();
      input.setName("Delete Isolation Test Exercise");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/exercises")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String exerciseId = JsonPath.read(createResponse, "$.exercise_id");

      // Evict L1 cache so deleteById() hits the DB (where RLS filters)
      entityManager.flush();
      entityManager.clear();

      // -------- Act — delete from tenant Y --------
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/exercises/" + exerciseId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
