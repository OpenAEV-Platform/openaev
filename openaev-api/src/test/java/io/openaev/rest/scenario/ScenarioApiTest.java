package io.openaev.rest.scenario;

import static io.openaev.database.model.TenantSettingKeys.TENANT_SCENARIO_DASHBOARD;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.*;
import io.openaev.injector_contract.fields.ContractExpectations;
import io.openaev.model.inject.form.Expectation;
import io.openaev.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.scenario.form.CheckScenarioRulesInput;
import io.openaev.rest.scenario.form.ScenarioAndInjectorContractsInputs;
import io.openaev.rest.scenario.form.ScenarioIdsAndInjectorContractsInputs;
import io.openaev.rest.scenario.form.ScenarioInput;
import io.openaev.rest.scenario.form.ScenarioRecurrenceInput;
import io.openaev.rest.scenario.form.ScenarioUpdateTeamsInput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;

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
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @AfterEach
  void afterEach() {
    agentComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    scenarioComposer.reset();
    userComposer.reset();
    grantComposer.reset();
    tenantGroupComposer.reset();
    tenantRoleComposer.reset();
    injectorContractComposer.reset();
    payloadComposer.reset();
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

  @DisplayName("Create chained scenario fails without enterprise edition")
  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_ASSESSMENT})
  void given_chainedScenarioCreationWithoutEE_should_fail() throws Exception {
    // Arrange
    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("My chained scenario");
    scenarioInput.setIsChaining(true);

    // Act & Assert
    this.mvc
        .perform(
            post(SCENARIO_URI)
                .content(asJsonString(scenarioInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
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

  @Nested
  @DisplayName("Scenario email configuration")
  class ScenarioEmails {

    private static final String CUSTOM_REPLY_TO = "custom-reply@openaev.io";
    private static final String OTHER_REPLY_TO = "other-reply@openaev.io";

    private String createScenarioWithReplyTos(List<String> replyTos) throws Exception {
      ScenarioInput input = new ScenarioInput();
      input.setName("Scenario with emails");
      input.setFromName("Custom sender");
      input.setReplyTos(replyTos);
      String response =
          mvc.perform(
                  post(SCENARIO_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(response, "$.scenario_id");
    }

    private String getScenarioAsJson(String scenarioId) throws Exception {
      return mvc.perform(
              get(SCENARIO_URI + "/" + scenarioId).accept(MediaType.APPLICATION_JSON).with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @DisplayName("Creation keeps the reply-to and sender name typed by the user")
    @Test
    @WithMockUser(isAdmin = true)
    void given_customEmails_should_notBeOverriddenByPlatformDefaults() throws Exception {
      // Arrange & Act
      String scenarioId = createScenarioWithReplyTos(List.of(CUSTOM_REPLY_TO));

      // Assert
      Scenario created = scenarioRepository.findById(scenarioId).orElseThrow();
      assertEquals(List.of(CUSTOM_REPLY_TO), created.getReplyTos());
      assertEquals("Custom sender", created.getFromName());
    }

    @DisplayName("Read exposes the reply-to addresses")
    @Test
    @WithMockUser(isAdmin = true)
    void given_aScenarioWithReplyTos_should_returnThemOnRead() throws Exception {
      // Arrange
      String scenarioId = createScenarioWithReplyTos(List.of(CUSTOM_REPLY_TO));

      // Act
      String response = getScenarioAsJson(scenarioId);

      // Assert
      assertEquals(List.of(CUSTOM_REPLY_TO), JsonPath.read(response, "$.scenario_mails_reply_to"));
    }

    @DisplayName("Replaying the read payload as an update keeps the reply-to addresses")
    @Test
    @WithMockUser(isAdmin = true)
    void given_theReadPayloadReplayedAsUpdate_should_keepReplyTos() throws Exception {
      // Arrange
      String scenarioId = createScenarioWithReplyTos(List.of(CUSTOM_REPLY_TO));
      String read = getScenarioAsJson(scenarioId);
      ScenarioInput replayed = new ScenarioInput();
      replayed.setName(JsonPath.read(read, "$.scenario_name"));
      replayed.setFromName(JsonPath.read(read, "$.scenario_mail_from_name"));
      replayed.setReplyTos(JsonPath.read(read, "$.scenario_mails_reply_to"));

      // Act
      mvc.perform(
              put(SCENARIO_URI + "/" + scenarioId)
                  .content(asJsonString(replayed))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertEquals(
          List.of(CUSTOM_REPLY_TO),
          scenarioRepository.findById(scenarioId).orElseThrow().getReplyTos());
    }

    @DisplayName("An update omitting the reply-to field keeps the stored addresses")
    @Test
    @WithMockUser(isAdmin = true)
    void given_anUpdateWithoutReplyTos_should_keepThem() throws Exception {
      // Arrange
      String scenarioId = createScenarioWithReplyTos(List.of(CUSTOM_REPLY_TO));
      ScenarioInput update = new ScenarioInput();
      update.setName("Renamed scenario");

      // Act
      mvc.perform(
              put(SCENARIO_URI + "/" + scenarioId)
                  .content(asJsonString(update))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertEquals(
          List.of(CUSTOM_REPLY_TO),
          scenarioRepository.findById(scenarioId).orElseThrow().getReplyTos());
    }

    @DisplayName("An update with an explicit empty array clears the reply-to addresses")
    @Test
    @WithMockUser(isAdmin = true)
    void given_anUpdateWithEmptyReplyTos_should_clearThem() throws Exception {
      // Arrange
      String scenarioId = createScenarioWithReplyTos(List.of(CUSTOM_REPLY_TO, OTHER_REPLY_TO));
      ScenarioInput update = new ScenarioInput();
      update.setName("Renamed scenario");
      update.setReplyTos(new ArrayList<>());

      // Act
      mvc.perform(
              put(SCENARIO_URI + "/" + scenarioId)
                  .content(asJsonString(update))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertTrue(scenarioRepository.findById(scenarioId).orElseThrow().getReplyTos().isEmpty());
    }
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
  void given_validInput_should_createScenarioWithInjectorContracts() throws Exception {
    // -- PREPARE --
    User testUser = createUserWithManageAssessmentRoleAndGrantOnEmailInjectorContract();
    Authentication auth = buildAuthenticationToken(testUser);

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
                    .with(authentication(auth))
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

  @DisplayName("Create scenario with injector contracts carries default expectations")
  @Test
  void given_payloadContractWithDefaultExpectations_should_carryExpectationsOnCreatedInjects()
      throws Exception {
    // -- PREPARE --
    Expectation detection = new Expectation();
    detection.setType(BaseInjectExpectation.EXPECTATION_TYPE.DETECTION);
    detection.setName("Detection");
    detection.setScore(100.0);
    detection.setPredefined(true);
    Expectation prevention = new Expectation();
    prevention.setType(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);
    prevention.setName("Prevention");
    prevention.setScore(100.0);
    prevention.setPredefined(false);
    InjectorContract contract =
        InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
            List.of(ContractExpectations.expectationsField(List.of(detection, prevention))));
    contract.setLabels(Map.of("en", "Payload action with default expectations"));
    injectorContractComposer
        .forInjectorContract(contract)
        .withInjector(InjectorFixture.createDefaultPayloadInjector())
        .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
        .persist();

    User testUser = createUserWithManageAssessmentRoleAndGrantOnContract(contract.getId());
    Authentication auth = buildAuthenticationToken(testUser);

    ScenarioInput scenarioInput = new ScenarioInput();
    scenarioInput.setName("Scenario with default expectations");
    scenarioInput.setFromName("no-reply@openaev.io");

    InjectorContractSearchPaginationInput paginationInput =
        new InjectorContractSearchPaginationInput();
    paginationInput.setIncludeFullDetails(true);
    paginationInput.setInjectorContractIdsToProcess(List.of(contract.getId()));

    ScenarioAndInjectorContractsInputs input = new ScenarioAndInjectorContractsInputs();
    input.setLocale("en");
    input.setScenarioInput(scenarioInput);
    input.setInjectorContractSearchPaginationInput(paginationInput);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                post(SCENARIO_URI + "/with-injector-contracts")
                    .with(authentication(auth))
                    .with(csrf())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    String scenarioId = JsonPath.read(response, "$.scenario_id");
    Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
    assertEquals(1, injects.size());
    ObjectNode content = injects.iterator().next().getContent();
    assertNotNull(content, "Inject content should be derived from the contract defaults");
    // The raw contract definition must not leak into the inject content
    assertFalse(content.has("fields"));
    JsonNode expectations = content.get("expectations");
    assertNotNull(expectations, "Default (predefined) expectations should be carried over");
    assertTrue(expectations.isArray());
    assertEquals(1, expectations.size());
    assertEquals("DETECTION", expectations.get(0).get("expectation_type").asText());
    assertTrue(expectations.get(0).get("expectation_is_predefined").asBoolean());
  }

  @DisplayName("Update scenarios with injector contracts")
  @Test
  void given_existingScenarios_should_updateScenariosWithInjectorContracts() throws Exception {
    // -- PREPARE --
    // Create test user with assessment role and granted on email injectorContract
    User testUser = createUserWithManageAssessmentRoleAndGrantOnEmailInjectorContract();
    Authentication auth = buildAuthenticationToken(testUser);

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
                    .with(authentication(auth))
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

  private User createUserWithManageAssessmentRoleAndGrantOnEmailInjectorContract() {
    return createUserWithManageAssessmentRoleAndGrantOnContract(
        injectorContractFixture.getWellKnownSingleEmailContract().getId());
  }

  private User createUserWithManageAssessmentRoleAndGrantOnContract(String injectorContractId) {
    Grant grant = new Grant();
    grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL);
    grant.setName(Grant.GRANT_TYPE.OBSERVER);
    grant.setResourceId(injectorContractId);

    TenantGroupComposer.Composer threatArsenalGroup =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withRole(
                tenantRoleComposer.forRole(
                    TenantRoleFixture.getRole(new HashSet<>(Set.of(Capability.MANAGE_ASSESSMENT)))))
            .withGrant(grantComposer.forGrant(grant));

    return userComposer
        .forUser(
            UserFixture.getUser(
                "AccessThreatArsenals", "User", UUID.randomUUID() + "@unittests.invalid"))
        .withGroup(threatArsenalGroup)
        .persist()
        .get();
  }

  private InjectorContractSearchPaginationInput createInjectorContractSearchPaginationInput() {
    InjectorContractSearchPaginationInput paginationInput =
        new InjectorContractSearchPaginationInput();
    paginationInput.setIncludeFullDetails(true);
    paginationInput.setInjectorContractIdsToProcess(
        List.of(injectorContractFixture.getWellKnownSingleEmailContract().getId()));
    return paginationInput;
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Scenario created in tenant X should NOT be readable from tenant Y")
    void given_scenarioInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("Isolation Test Scenario");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — read from tenant Y (expect 404) --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/scenarios/" + scenarioId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Scenario created in tenant X should be readable from tenant X")
    void given_scenarioInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("Same Tenant Scenario");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

      // -------- Act & Assert — read from same tenant should succeed --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/scenarios/" + scenarioId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_name").value("Same Tenant Scenario"));
    }

    @Test
    @DisplayName("Scenario search in tenant Y should NOT return scenarios from tenant X")
    void given_scenarioInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("CrossTenantSearchScenario");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/scenarios")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

      // -------- Act — search from tenant Y --------
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantSearchScenario");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/scenarios/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Scenario created in tenant X should NOT be updatable from tenant Y")
    void given_scenarioInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("Update Isolation Test Scenario");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — update from tenant Y (expect 404) --------
      ScenarioInput updateInput = new ScenarioInput();
      updateInput.setName("Hijacked Name");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/scenarios/" + scenarioId)
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
    @DisplayName("Scenario created in tenant X should NOT be deletable from tenant Y")
    void given_scenarioInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("Delete Isolation Test Scenario");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — delete from tenant Y (expect 404) --------
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/scenarios/" + scenarioId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Fetching scenarios by IDs should NOT return scenarios from another tenant")
    void given_scenarioInTenantX_should_notBeReturnedByIdFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSESSMENT, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSESSMENT));

      ScenarioInput input = new ScenarioInput();
      input.setName("SearchById Isolation Scenario");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(createResponse, "$.scenario_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — fetch by IDs from tenant Y --------
      String searchByIdResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/scenarios/search-by-id")
                      .content(asJsonString(Map.of("scenario_ids", List.of(scenarioId))))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert — result should be empty --------
      List<Object> results = JsonPath.read(searchByIdResponse, "$");
      assertThat(results.size()).isEqualTo(0);
    }

    @Test
    @DisplayName(
        "Enabling team players in scenario from tenant X should fail with team from tenant Y")
    void given_teamInTenantY_should_notEnablePlayersInScenarioFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));

      // Create scenario in tenant X
      ScenarioInput scenarioInput = new ScenarioInput();
      scenarioInput.setName("TeamPlayer Isolation Scenario");

      String scenarioResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(scenarioInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(scenarioResponse, "$.scenario_id");

      // Create team in tenant Y
      io.openaev.rest.team.form.TeamCreateInput teamInput =
          new io.openaev.rest.team.form.TeamCreateInput();
      teamInput.setName("CrossTenant Scenario Team");

      String teamResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/teams")
                      .content(asJsonString(teamInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(teamResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — enable team players from tenant X using team from tenant Y --------
      ScenarioTeamPlayersEnableInput playersInput = new ScenarioTeamPlayersEnableInput();
      playersInput.setPlayersIds(List.of());

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantX.getId()
                          + "/scenarios/"
                          + scenarioId
                          + "/teams/"
                          + teamId
                          + "/players/enable")
                      .content(asJsonString(playersInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert — team from another tenant should not be found --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName(
        "Adding team players in scenario from tenant X should fail with team from tenant Y")
    void given_teamInTenantY_should_notAddPlayersInScenarioFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));

      // Create scenario in tenant X
      ScenarioInput scenarioInput = new ScenarioInput();
      scenarioInput.setName("AddPlayer Isolation Scenario");

      String scenarioResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(scenarioInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(scenarioResponse, "$.scenario_id");

      // Create team in tenant Y
      io.openaev.rest.team.form.TeamCreateInput teamInput =
          new io.openaev.rest.team.form.TeamCreateInput();
      teamInput.setName("CrossTenant Add Scenario Team");

      String teamResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/teams")
                      .content(asJsonString(teamInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(teamResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — add players from tenant X using team from tenant Y --------
      ScenarioTeamPlayersEnableInput playersInput = new ScenarioTeamPlayersEnableInput();
      playersInput.setPlayersIds(List.of());

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantX.getId()
                          + "/scenarios/"
                          + scenarioId
                          + "/teams/"
                          + teamId
                          + "/players/add")
                      .content(asJsonString(playersInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert — team from another tenant should not be found --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName(
        "Removing team players in scenario from tenant X should fail with team from tenant Y")
    void given_teamInTenantY_should_notRemovePlayersInScenarioFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(
                  Capability.MANAGE_ASSESSMENT,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.MANAGE_TEAMS_AND_PLAYERS,
                  Capability.ACCESS_TEAMS_AND_PLAYERS));

      // Create scenario in tenant X
      ScenarioInput scenarioInput = new ScenarioInput();
      scenarioInput.setName("RemovePlayer Isolation Scenario");

      String scenarioResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/scenarios")
                      .content(asJsonString(scenarioInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String scenarioId = JsonPath.read(scenarioResponse, "$.scenario_id");

      // Create team in tenant Y
      io.openaev.rest.team.form.TeamCreateInput teamInput =
          new io.openaev.rest.team.form.TeamCreateInput();
      teamInput.setName("CrossTenant Remove Scenario Team");

      String teamResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/teams")
                      .content(asJsonString(teamInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(teamResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act — remove players from tenant X using team from tenant Y --------
      ScenarioTeamPlayersEnableInput playersInput = new ScenarioTeamPlayersEnableInput();
      playersInput.setPlayersIds(List.of());

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/"
                          + tenantX.getId()
                          + "/scenarios/"
                          + scenarioId
                          + "/teams/"
                          + teamId
                          + "/players/remove")
                      .content(asJsonString(playersInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert — team from another tenant should not be found --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
