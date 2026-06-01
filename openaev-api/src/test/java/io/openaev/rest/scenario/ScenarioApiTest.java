package io.openaev.rest.scenario;

import static io.openaev.database.model.TenantSettingKeys.TENANT_SCENARIO_DASHBOARD;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.scenario.form.CheckScenarioRulesInput;
import io.openaev.rest.scenario.form.ScenarioAndInjectorContractsInputs;
import io.openaev.rest.scenario.form.ScenarioIdsAndInjectorContractsInputs;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.rest.scenario.form.ScenarioRecurrenceInput;
import io.openaev.rest.scenario.form.ScenarioUpdateTeamsInput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
@Transactional
public class ScenarioApiTest extends IntegrationTest {

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private InjectorContractFixture injectorContractFixture;

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TagRuleRepository tagRuleRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private SettingRepository settingRepository;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private AssetGroupService assetGroupService;

  @AfterEach
  void afterEach() {
    agentComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    scenarioComposer.reset();
  }

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

  @DisplayName("Create scenario succeed")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void createScenarioTest() throws Exception {
    // -- PREPARE --
    ScenarioInput scenarioInput = new ScenarioInput();

    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI)
                .content(asJsonString(scenarioInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());

    // -- PREPARE --
    String name = "My scenario";
    scenarioInput.setName(name);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    String scenarioId = JsonPath.read(response, "$.scenario_id");
    assertFalse(scenarioId.isEmpty());
  }

  @DisplayName("Create scenario succeed with default dashboard")
  @Test
  @WithMockUser(isAdmin = true)
  void given_scenario_creation_should_set_default_custom_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboard defaultDashboard = new CustomDashboard();
    defaultDashboard.setName("Default scenario dashboard");
    CustomDashboard customDashboardSaved = customDashboardRepository.save(defaultDashboard);

    ScenarioInput scenarioInput = new ScenarioInput();
    String name = "My scenario";
    scenarioInput.setName(name);

    settingRepository.save(
        settingRepository
            .findByKeyAndTenantId(TENANT_SCENARIO_DASHBOARD.key(), TenantContext.getCurrentTenant())
            .map(
                s -> {
                  s.setValue(customDashboardSaved.getId());
                  return s;
                })
            .orElseGet(
                () -> {
                  Setting s =
                      new Setting(TENANT_SCENARIO_DASHBOARD.key(), customDashboardSaved.getId());
                  s.setTenant(new Tenant(TenantContext.getCurrentTenant()));
                  return s;
                }));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI)
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void retrieveScenariosTest() throws Exception {
    // -- PREPARE --
    scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(get(SCENARIO_URI).accept(MediaType.APPLICATION_JSON).with(csrf()))
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
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void retrieveScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario testScenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + testScenario.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
  }

  @DisplayName("Requesting non existing scenario by ID fails gracefully")
  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void failsafeNonExistScenarioId() throws Exception {
    // -- EXECUTE --
    this.mvc
        .perform(
            get(SCENARIO_URI + "/DOESNOTEXIST").accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @DisplayName("Update scenario")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void updateScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario testScenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();

    ScenarioInput scenarioInput = new ScenarioInput();
    String subtitle = "A subtitle";
    scenarioInput.setName(testScenario.getName());
    scenarioInput.setSubtitle(subtitle);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + testScenario.getId())
                    .content(asJsonString(scenarioInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
  @WithMockUser(withCapabilities = {Capability.DELETE_ASSESSMENT})
  void deleteScenarioTest() throws Exception {
    // -- PREPARE --
    Scenario testScenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();

    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + testScenario.getId()).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @DisplayName("Delete scenario with inject")
  @Test
  @WithMockUser(withCapabilities = {Capability.DELETE_ASSESSMENT})
  void deleteScenarioWithInjectTest() throws Exception {
    Scenario testScenario = getScenario(ScenarioFixture.getScheduledScenario(), null);
    // -- EXECUTE 1 ASSERT --
    this.mvc
        .perform(delete(SCENARIO_URI + "/" + testScenario.getId()).with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @DisplayName("Check if a rule applies when a rule is found")
  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTagNoId();
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
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  void checkIfRuleAppliesTest_WHEN_no_rule_found() throws Exception {
    this.tagRuleRepository.deleteAll();
    this.tagRepository.deleteAll();
    Tag tag2 = TagFixture.getTagNoId();
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
  @DisplayName("Lock Scenario EE feature")
  @WithMockUser(isAdmin = true)
  class LockScenarioEEFeature {

    @Test
    @DisplayName("Throw license restricted error when launch scenario with crowdstrike")
    void given_crowdstrikeAsset_should_not_startScenario() throws Exception {
      Scenario scenario = getScenario(null, executorFixture.getCrowdstrikeExecutor());

      mvc.perform(post(SCENARIO_URI + "/" + scenario.getId() + "/exercise/running").with(csrf()))
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
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when scheduled scenario with Sentinel One")
    void given_sentineloneAsset_should_not_scheduleScenario() throws Exception {
      Scenario scenario = getScenario(null, executorFixture.getSentineloneExecutor());
      ScenarioRecurrenceInput input = new ScenarioRecurrenceInput();
      input.setRecurrenceStart(Instant.now());

      mvc.perform(
              put(SCENARIO_URI + "/" + scenario.getId() + "/recurrence")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
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
      entityManager.flush();
      endpointRepository.deleteAll();
      entityManager.clear();
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(
              agentComposer.forAgent(
                  AgentFixture.createDefaultAgentSession(executorFixture.getCrowdstrikeExecutor())))
          .persist();

      InjectInput input = new InjectInput();
      input.setTitle(scenario.getInjects().getFirst().getTitle());
      input.setAssetGroups(List.of(dynamicAssetGroupSaved.getId()));
      // necessary to avoid detach exception in test context since we removed the test order and
      // added the transactional.
      assetGroupService.computeDynamicAssets(dynamicAssetGroupSaved);

      mvc.perform(
              put(SCENARIO_URI
                      + "/"
                      + scenario.getId()
                      + "/injects/"
                      + scenario.getInjects().getFirst().getId())
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }
  }

  @Test
  @Transactional
  @DisplayName("Should enable all users of newly added teams when replacing scenario teams")
  @WithMockUser(isAdmin = true)
  void replacingTeamsShouldEnableNewTeamUsers() throws Exception {
    // -- PREPARE --
    User userTom = userRepository.save(UserFixture.getUser("Tom", "TEST", "tom-test@fake.email"));
    User userBen = userRepository.save(UserFixture.getUser("Ben", "TEST", "ben-test@fake.email"));

    Team teamA = TeamFixture.getTeam(userTom, "TeamA", false);
    teamA.setUsers(List.of(userTom));
    teamRepository.save(teamA);
    Team teamB = TeamFixture.getTeam(userBen, "TeamB", false);
    teamB.setUsers(List.of(userBen));
    teamRepository.save(teamB);

    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTeams(Collections.singletonList(teamA));
    Scenario scenarioSaved = scenarioRepository.save(scenario);

    // -- ACT --
    List<String> newTeamIds = Arrays.asList(teamA.getId(), teamB.getId());
    ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
    input.setTeamIds(newTeamIds);

    mvc.perform(
            put(SCENARIO_URI + "/" + scenarioSaved.getId() + "/teams/replace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(input))
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());

    // -- ASSERT --
    List<ScenarioTeamUser> links = scenarioTeamUserRepository.findAll();

    ScenarioTeamUser link = links.getFirst();
    assertEquals(scenarioSaved.getId(), link.getScenario().getId());
    assertEquals(teamB.getId(), link.getTeam().getId());
    assertEquals(userBen.getId(), link.getUser().getId());
  }

  @DisplayName("Create scenario with injector contracts")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void given_nullInput_should_returnBadRequest_onCreateScenarioWithInjectorContracts()
      throws Exception {
    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            post(SCENARIO_URI + "/with-injector-contracts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("Update scenarios with injector contracts returns bad request on null input")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void given_nullInput_should_returnBadRequest_onUpdateScenariosWithInjectorContracts()
      throws Exception {
    // -- EXECUTE & ASSERT --
    this.mvc
        .perform(
            put(SCENARIO_URI + "/with-injector-contracts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @DisplayName("Create scenario with injector contracts")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void given_validInput_should_createScenarioWithInjectorContracts() throws Exception {
    // -- PREPARE --
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Scenario with injector contracts");
    scenarioInput.setFromName("no-reply@openaev.io");

    InjectorContractSearchPaginationInput paginationInput =
        createInjectorContractSearchPaginationInput();

    ScenarioAndInjectorContractsInputs input = new ScenarioAndInjectorContractsInputs();
    input.setLocale("en");
    input.setScenarioInput(scenarioInput);
    input.setInjectorContractSearchPaginationInput(paginationInput);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/with-injector-contracts")
                    .with(csrf())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.scenario_name").value("Scenario with injector contracts"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String scenarioId = JsonPath.read(response, "$.scenario_id");
    assertFalse(scenarioId.isEmpty());
    assertFalse(injectRepository.findByScenarioId(scenarioId).isEmpty());
  }

  @DisplayName("Update scenarios with injector contracts")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void given_existingScenarios_should_updateScenariosWithInjectorContracts() throws Exception {
    // -- PREPARE --
    Scenario scenarioA =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario()).persist().get();
    Scenario scenarioB =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .persist()
            .get();

    InjectorContractSearchPaginationInput paginationInput =
        createInjectorContractSearchPaginationInput();

    ScenarioIdsAndInjectorContractsInputs input = new ScenarioIdsAndInjectorContractsInputs();
    input.setLocale("en");
    input.setScenarioIds(List.of(scenarioA.getId(), scenarioB.getId()));
    input.setInjectorContractSearchPaginationInput(paginationInput);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/with-injector-contracts")
                    .with(csrf())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    List<String> returnedScenarioIds = JsonPath.read(response, "$..scenario_id");
    assertTrue(returnedScenarioIds.contains(scenarioA.getId()));
    assertTrue(returnedScenarioIds.contains(scenarioB.getId()));
    assertFalse(injectRepository.findByScenarioId(scenarioA.getId()).isEmpty());
    assertFalse(injectRepository.findByScenarioId(scenarioB.getId()).isEmpty());
  }

  private InjectorContractSearchPaginationInput createInjectorContractSearchPaginationInput() {
    InjectorContractSearchPaginationInput paginationInput =
        new InjectorContractSearchPaginationInput();
    paginationInput.setIncludeFullDetails(true);
    paginationInput.setInjectorContractIdsToProcess(
        List.of(injectorContractFixture.getWellKnownSingleEmailContract().getId()));
    return paginationInput;
  }
}
