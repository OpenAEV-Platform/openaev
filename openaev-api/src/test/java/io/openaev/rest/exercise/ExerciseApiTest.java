package io.openaev.rest.exercise;

import static io.openaev.database.model.SettingKeys.DEFAULT_SIMULATION_DASHBOARD;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.exercise.form.*;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(PER_CLASS)
public class ExerciseApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
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

  List<ExerciseComposer.Composer> exerciseWrapperComposers = new ArrayList<>();
  private static final List<String> EXERCISE_IDS = new ArrayList<>();
  private static final List<String> USER_IDS = new ArrayList<>();
  private static final List<String> TEAM_IDS = new ArrayList<>();

  @AfterAll
  void afterAll() {
    exerciseWrapperComposers.forEach(ExerciseComposer.Composer::delete);
    this.exerciseRepository.deleteAllById(EXERCISE_IDS);
    this.userRepository.deleteAllById(USER_IDS);
    this.teamRepository.deleteAllById(TEAM_IDS);
    this.tagRuleRepository.deleteAll();
    this.assetGroupRepository.deleteAll();
    this.tagRepository.deleteAll();
  }

  @DisplayName("Create simulation succeed with default dashboard")
  @Test
  @WithMockUser(isAdmin = true)
  @Transactional
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
    String response =
        this.mvc
            .perform(
                post(EXERCISE_URI)
                    .content(asJsonString(exerciseInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
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
                  .accept(MediaType.APPLICATION_JSON))
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
                      .content(asJsonString(input)))
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
                    .accept(MediaType.APPLICATION_JSON))
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
    io.openaev.database.model.Tag tag2 = TagFixture.getTag();
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
                    .accept(MediaType.APPLICATION_JSON))
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
    Tag tag2 = TagFixture.getTag();
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
                    .accept(MediaType.APPLICATION_JSON))
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
      exerciseWrapperComposers.add(newExerciseComposer);
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
                  .accept(MediaType.APPLICATION_JSON))
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
                  .accept(MediaType.APPLICATION_JSON))
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
                      AgentFixture.createDefaultAgentSession(
                          executorFixture.getCrowdstrikeExecutor())))
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
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }
  }

  @Test
  @Transactional
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
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<ExerciseTeamUser> links = exerciseTeamUserRepository.findAll();

    ExerciseTeamUser link = links.getFirst();
    assertEquals(exerciseSaved.getId(), link.getExercise().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }
}
