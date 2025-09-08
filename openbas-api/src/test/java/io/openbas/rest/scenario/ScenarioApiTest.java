package io.openbas.rest.scenario;

import static io.openbas.database.model.SettingKeys.DEFAULT_SCENARIO_DASHBOARD;
import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.Tag;
import io.openbas.database.repository.*;
import io.openbas.rest.inject.form.InjectInput;
import io.openbas.rest.scenario.form.CheckScenarioRulesInput;
import io.openbas.rest.scenario.form.ScenarioInput;
import io.openbas.rest.scenario.form.ScenarioRecurrenceInput;
import io.openbas.rest.scenario.form.ScenarioUpdateTeamsInput;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockAdminUser;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
public class ScenarioApiTest extends IntegrationTest {

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;

  static String SCENARIO_ID;

  private static final List<String> SCENARIO_IDS = new ArrayList<>();
  private static final List<String> USER_IDS = new ArrayList<>();
  private static final List<String> TEAM_IDS = new ArrayList<>();

  @AfterAll
  void afterAll() {
    if (SCENARIO_ID != null) {
      this.scenarioRepository.deleteById(SCENARIO_ID);
    }
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    this.assetGroupRepository.deleteAll();
  }

  @DisplayName("Create scenario succeed")
  @Test
  @Order(1)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void createScenarioTest() throws Exception {
    // -- PREPARE --
    ScenarioInput scenarioInput = new ScenarioInput();

    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI)
                .content(asJsonString(scenarioInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());

    // -- PREPARE --
    String name = "My scenario";
    scenarioInput.setName(name);
    String from = "no-reply@openbas.io";
    scenarioInput.setFrom(from);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    SCENARIO_ID = JsonPath.read(response, "$.scenario_id");
  }

  @DisplayName("Create scenario succeed with default dashboard")
  @Test
  @Order(1)
  @WithMockAdminUser
  @Transactional
  void given_scenario_creation_should_set_default_custom_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);

    ScenarioInput scenarioInput = new ScenarioInput();
    String name = "My scenario";
    scenarioInput.setName(name);
    String from = "no-reply@openbas.io";
    scenarioInput.setFrom(from);

    settingRepository.save(
        settingRepository
            .findByKey(DEFAULT_SCENARIO_DASHBOARD.key())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () -> new Setting(DEFAULT_SCENARIO_DASHBOARD.key(), customDashboardSaved.getId())));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String newScenarioId = JsonPath.read(response, "$.scenario_id");
    Scenario newScenario = this.scenarioRepository.findById(newScenarioId).orElseThrow();
    assertEquals(customDashboardSaved.getId(), newScenario.getCustomDashboard().getId());
  }

  @DisplayName("Retrieve scenarios")
  @Test
  @Order(2)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void retrieveScenariosTest() throws Exception {
    // -- EXECUTE --
    String response =
        this.mvc
            .perform(get(SCENARIO_URI).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Retrieve scenario")
  @Test
  @Order(3)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void retrieveScenarioTest() throws Exception {
    // -- EXECUTE --
    String response =
        this.mvc
            .perform(get(SCENARIO_URI + "/" + SCENARIO_ID).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Update scenario")
  @Test
  @Order(4)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void updateScenarioTest() throws Exception {
    // -- PREPARE --
    String response =
        this.mvc
            .perform(get(SCENARIO_URI + "/" + SCENARIO_ID).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ScenarioInput scenarioInput = new ScenarioInput();
    String subtitle = "A subtitle";
    scenarioInput.setName(JsonPath.read(response, "$.scenario_name"));
    scenarioInput.setFrom(JsonPath.read(response, "$.scenario_mail_from"));
    scenarioInput.setSubtitle(subtitle);

    // -- EXECUTE --
    response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + SCENARIO_ID)
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(subtitle, JsonPath.read(response, "$.scenario_subtitle"));
  }

  @DisplayName("Delete scenario")
  @Test
  @Order(5)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void deleteScenarioTest() throws Exception {
    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + SCENARIO_ID))
        .andExpect(status().is2xxSuccessful());
  }

  @DisplayName("Check if a rule applies when a rule is found")
  @Test
  @Order(7)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void checkIfRuleAppliesTest_WHEN_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);

    AssetGroup assetGroup =
        assetGroupRepository.save(AssetGroupFixture.createDefaultAssetGroup("assetGroup"));
    TagRule tagRule = new TagRule();
    tagRule.setTag(tag2);
    tagRule.setAssetGroups(List.of(assetGroup));
    this.tagRuleRepository.save(tagRule);

    Scenario scenario = this.scenarioRepository.save(ScenarioFixture.getScenario());

    CheckScenarioRulesInput input = new CheckScenarioRulesInput();
    input.setNewTags(List.of(tag2.getId()));
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/check-rules")
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
  @Order(8)
  @WithMockAdminUser // FIXME: Temporary workaround for grant issue
  void checkIfRuleAppliesTest_WHEN_no_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTag();
    tag2.setName("tag2");
    tag2 = this.tagRepository.save(tag2);
    CheckScenarioRulesInput input = new CheckScenarioRulesInput();
    input.setNewTags(List.of(tag2.getId()));

    Scenario scenario = this.scenarioRepository.save(ScenarioFixture.getScenario());

    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/check-rules")
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
  @DisplayName("Lock Scenario EE feature")
  @WithMockAdminUser
  class LockScenarioEEFeature {

    private Scenario getScenario(@Nullable Scenario scenario, @Nullable Executor executor) {
      Executor executorToRun = (executor == null) ? executorFixture.getDefaultExecutor() : executor;
      Scenario scenarioToSet = (scenario == null) ? ScenarioFixture.getScenario() : scenario;
      ScenarioComposer.Composer newScenarioComposer =
          scenarioComposer
              .forScenario(scenarioToSet)
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
      return newScenarioComposer.get();
    }

    @Test
    @DisplayName("Throw license restricted error when launch scenario with crowdstrike")
    void given_crowdstrikeAsset_should_not_startScenario() throws Exception {
      Scenario scenario = getScenario(null, executorFixture.getCrowdstrikeExecutor());

      mvc.perform(post(SCENARIO_URI + "/" + scenario.getId() + "/exercise/running"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when scheduled scenario with Tanium")
    void given_taniumAsset_should_not_scheduleScenario() throws Exception {
      Scenario scenario = getScenario(null, executorFixture.getTaniumExecutor());
      ScenarioRecurrenceInput input = new ScenarioRecurrenceInput();
      input.setRecurrenceStart(Instant.now());

      mvc.perform(
              put(SCENARIO_URI + "/" + scenario.getId() + "/recurrence")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when add Crowdstrike on scheduled scenario")
    void given_crowdstrikeInsdeDynamicGroup_should_not_beAddedToScheduledExercise()
        throws Exception {
      Scenario scenario = getScenario(ScenarioFixture.getScheduledScenario(), null);

      // Create dynamic windows asset group
      AssetGroup dynamicAssetGroup = AssetGroupFixture.createDefaultAssetGroup("windows group");
      Filters.Filter windowsFilter = new Filters.Filter();
      windowsFilter.setKey("endpoint_platform");
      windowsFilter.setMode(Filters.FilterMode.and);
      windowsFilter.setValues(List.of("Windows"));
      windowsFilter.setOperator(Filters.FilterOperator.eq);
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setFilters(List.of(windowsFilter));
      filterGroup.setMode(Filters.FilterMode.and);
      dynamicAssetGroup.setDynamicFilter(filterGroup);
      AssetGroup dynamicAssetGroupSaved = assetGroupRepository.save(dynamicAssetGroup);

      // Create windows endpoint with crowdstrike agent
      endpointRepository.deleteAll();
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(
              agentComposer.forAgent(
                  AgentFixture.createDefaultAgentSession(executorFixture.getCrowdstrikeExecutor())))
          .persist();

      InjectInput input = new InjectInput();
      input.setTitle(scenario.getInjects().getFirst().getTitle());
      input.setAssetGroups(List.of(dynamicAssetGroupSaved.getId()));

      mvc.perform(
              put(SCENARIO_URI
                      + "/"
                      + scenario.getId()
                      + "/injects/"
                      + scenario.getInjects().getFirst().getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }
  }

  @Test
  @Transactional
  @DisplayName("Should enable all users of newly added teams when replacing scenario teams")
  @WithMockAdminUser
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

    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTeams(Collections.singletonList(teamA));
    Scenario scenarioSaved = scenarioRepository.save(scenario);
    SCENARIO_IDS.add(scenarioSaved.getId());

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(SCENARIO_URI + "/" + scenarioSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<ScenarioTeamUser> links = scenarioTeamUserRepository.findAll();

    ScenarioTeamUser link = links.getFirst();
    assertEquals(scenarioSaved.getId(), link.getScenario().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }
}
