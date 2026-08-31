package io.openaev.rest;

import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.TeamFixture.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseTeamUserRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.ScenarioTeamUserRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.team.form.TeamBulkProcessingInput;
import io.openaev.rest.team.form.TeamCreateInput;
import io.openaev.rest.team.form.UpdateUsersTeamInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ExerciseTeamUserFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class TeamApiTest extends IntegrationTest {

  private static final String SEARCH_INPUT = "search input";

  @Autowired private MockMvc mvc;

  @Autowired private ExerciseService exerciseService;
  @Autowired private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @DisplayName("Given valid team input, should create a team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validTeamInput_should_createTeamSuccessfully() throws Exception {
    // --PREPARE--
    TeamCreateInput teamInput = createTeam();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI)
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(teamInput.getName(), JsonPath.read(response, "$.team_name"));
    assertEquals(teamInput.getDescription(), JsonPath.read(response, "$.team_description"));
  }

  @DisplayName("Given existing team name input, should throw an exception")
  @Test
  @WithMockUser(isAdmin = true)
  void given_existingTeamNameInput_should_throwAnException() throws Exception {
    // --PREPARE--
    Team team = new Team();
    team.setName(TEAM_NAME);
    this.teamRepository.save(team);

    TeamCreateInput teamInput = createTeam();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI)
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(
        "Global teams (non contextual) cannot have the same name (already exists)",
        JsonPath.read(response, "$.message"));
  }

  @DisplayName("Given valid contextual team input, should create a contextual team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validContextualTeamInput_should_createContextualTeamSuccessfully() throws Exception {
    // -- PREPARE --
    Exercise exercise = ExerciseFixture.getExercise();
    exercise = this.exerciseService.createExercise(exercise);

    TeamCreateInput teamInput = createContextualExerciseTeam(List.of(exercise.getId()));

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI)
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(CONTEXTUAL_TEAM_NAME, JsonPath.read(response, "$.team_name"));
  }

  @DisplayName("Given existing contextual team name input, should throw an exception")
  @Test
  @WithMockUser(isAdmin = true)
  void given_existingContextualTeamNameInput_should_throwAnException() throws Exception {
    // -- PREPARE --
    Exercise exercise = ExerciseFixture.getExercise();
    exercise = this.exerciseService.createExercise(exercise);
    Team team = new Team();
    team.setName(CONTEXTUAL_TEAM_NAME);
    team.setContextual(true);
    team.setExercises(List.of(exercise));
    this.teamRepository.save(team);

    TeamCreateInput teamInput = createContextualExerciseTeam(List.of(exercise.getId()));

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI)
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(
        "A contextual team with the same name already exists on this simulation",
        JsonPath.read(response, "$.message"));
  }

  @DisplayName("Given valid team ID and input, should update team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validTeamIdAndInput_should_updateTeamSuccessfully() throws Exception {
    // --PREPARE--
    TeamCreateInput teamInput = createTeam();

    Team team = new Team();
    team.setUpdateAttributes(teamInput);
    team = teamRepository.save(team);
    String newName = "updatedName";
    teamInput.setName(newName);

    // --EXECUTE--
    String response =
        mvc.perform(
                put(TEAM_URI + "/" + team.getId())
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName, JsonPath.read(response, "$.team_name"));
  }

  @DisplayName(
      "Given team users update, should remove users from simulation and scenario enabled audience")
  @Test
  @WithMockUser(
      withCapabilities = {Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS})
  void given_teamUsersUpdate_should_removeUsersFromSimulationAndScenarioEnabledAudience()
      throws Exception {
    // -- PREPARE --
    User keptUser = this.userRepository.save(UserFixture.getUserWithDefaultEmail());
    User removedUser = this.userRepository.save(UserFixture.getUserWithDefaultEmail());
    Team team = createTeamWithName("team-audience-sync");
    team.setUsers(new ArrayList<>(List.of(keptUser, removedUser)));
    team = this.teamRepository.save(team);
    this.tenantIsolationHelper.grantCapabilitiesInTenant(
        team.getTenant().getId(),
        Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));

    Exercise exercise = ExerciseFixture.getExercise(List.of(team));
    exercise = this.exerciseService.createExercise(exercise);
    Scenario scenario = ScenarioFixture.getScenario(List.of(team), null);
    scenario = this.scenarioRepository.save(scenario);

    ExerciseTeamUser exerciseTeamUserKept =
        ExerciseTeamUserFixture.createExerciseTeamUser(exercise, team, keptUser);
    exerciseTeamUserKept.getCompositeId().setExerciseId(exercise.getId());
    exerciseTeamUserKept.getCompositeId().setTeamId(team.getId());
    exerciseTeamUserKept.getCompositeId().setUserId(keptUser.getId());
    this.exerciseTeamUserRepository.save(exerciseTeamUserKept);
    ExerciseTeamUser exerciseTeamUserRemoved =
        ExerciseTeamUserFixture.createExerciseTeamUser(exercise, team, removedUser);
    exerciseTeamUserRemoved.getCompositeId().setExerciseId(exercise.getId());
    exerciseTeamUserRemoved.getCompositeId().setTeamId(team.getId());
    exerciseTeamUserRemoved.getCompositeId().setUserId(removedUser.getId());
    this.exerciseTeamUserRepository.save(exerciseTeamUserRemoved);

    ScenarioTeamUser scenarioTeamUserKept = new ScenarioTeamUser();
    scenarioTeamUserKept.setScenario(scenario);
    scenarioTeamUserKept.setTeam(team);
    scenarioTeamUserKept.setUser(keptUser);
    scenarioTeamUserKept.getCompositeId().setScenarioId(scenario.getId());
    scenarioTeamUserKept.getCompositeId().setTeamId(team.getId());
    scenarioTeamUserKept.getCompositeId().setUserId(keptUser.getId());
    this.scenarioTeamUserRepository.save(scenarioTeamUserKept);
    ScenarioTeamUser scenarioTeamUserRemoved = new ScenarioTeamUser();
    scenarioTeamUserRemoved.setScenario(scenario);
    scenarioTeamUserRemoved.setTeam(team);
    scenarioTeamUserRemoved.setUser(removedUser);
    scenarioTeamUserRemoved.getCompositeId().setScenarioId(scenario.getId());
    scenarioTeamUserRemoved.getCompositeId().setTeamId(team.getId());
    scenarioTeamUserRemoved.getCompositeId().setUserId(removedUser.getId());
    this.scenarioTeamUserRepository.save(scenarioTeamUserRemoved);

    UpdateUsersTeamInput input = new UpdateUsersTeamInput();
    input.setUserIds(List.of(keptUser.getId()));

    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();

    // -- EXECUTE --
    mvc.perform(
            put("/api/tenants/" + team.getTenant().getId() + "/teams/" + team.getId() + "/players")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    assertTrue(
        this.exerciseTeamUserRepository.existsByExerciseIdAndTeamIdAndUserId(
            exercise.getId(), team.getId(), keptUser.getId()));
    assertFalse(
        this.exerciseTeamUserRepository.existsByExerciseIdAndTeamIdAndUserId(
            exercise.getId(), team.getId(), removedUser.getId()));
    assertTrue(
        this.scenarioTeamUserRepository.existsByScenarioIdAndTeamIdAndUserId(
            scenario.getId(), team.getId(), keptUser.getId()));
    assertFalse(
        this.scenarioTeamUserRepository.existsByScenarioIdAndTeamIdAndUserId(
            scenario.getId(), team.getId(), removedUser.getId()));
  }

  @DisplayName("Given valid team ID and input, should upsert team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validTeamIdAndInput_should_upsertTeamSuccessfully() throws Exception {
    // --PREPARE--
    TeamCreateInput teamInput = createTeam();

    Team team = new Team();
    team.setUpdateAttributes(teamInput);
    teamRepository.save(team);
    String newName = "updatedName";
    teamInput.setName(newName);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI + "/upsert")
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName, JsonPath.read(response, "$.team_name"));
    // --THEN--
    teamRepository.deleteById(JsonPath.read(response, "$.team_id"));
  }

  @DisplayName("Given non existing and team input, should upsert team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_nonExistingTeamInput_should_upsertTeamSuccessfully() throws Exception {
    // --PREPARE--
    TeamCreateInput teamInput = createTeam();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(TEAM_URI + "/upsert")
                    .content(asJsonString(teamInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(TEAM_NAME, JsonPath.read(response, "$.team_name"));
  }

  @DisplayName("Given contextual team input with multiple exercise, should throw an exception")
  @Test
  @WithMockUser(isAdmin = true)
  void given_contextualTeamWithMultipleExercise_should_throwAnException() {
    // -- PREPARE --
    Exercise exercise1 = ExerciseFixture.getExercise();
    exercise1.setName("exercise 1");
    exercise1 = this.exerciseService.createExercise(exercise1);
    Exercise exercise2 = ExerciseFixture.getExercise();
    exercise2.setName("exercise 2");
    exercise2 = this.exerciseService.createExercise(exercise2);

    TeamCreateInput teamInput =
        createContextualExerciseTeam(List.of(exercise1.getId(), exercise2.getId()));

    // --EXECUTE--
    Exception exception =
        assertThrows(
            ServletException.class,
            () ->
                mvc.perform(
                    post(TEAM_URI + "/upsert")
                        .content(asJsonString(teamInput))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf())));

    String expectedMessage = "Contextual team can only be associated to one exercise";
    String actualMessage = exception.getMessage();

    // --ASSERT--
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @DisplayName("Given a team linked to injects, should delete the team and keep the injects")
  @Test
  @WithMockUser(isAdmin = true)
  void given_teamLinkedToInjects_should_deleteTeamSuccessfully() throws Exception {
    // --PREPARE--
    Team team = this.teamRepository.save(createTeamWithName(TEAM_NAME + "-linked"));
    Inject inject = saveInjectWithTeams(List.of(team));

    // --EXECUTE--
    mvc.perform(delete(TEAM_URI + "/" + team.getId()).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    entityManager.flush();

    // --ASSERT--
    assertFalse(this.teamRepository.existsById(team.getId()));
    assertTrue(this.injectRepository.findById(inject.getId()).isPresent());
  }

  @DisplayName("Given teams linked to injects, should bulk delete the teams and keep the injects")
  @Test
  @WithMockUser(isAdmin = true)
  void given_teamsLinkedToInjects_should_bulkDeleteTeamsSuccessfully() throws Exception {
    // --PREPARE--
    Team firstTeam = this.teamRepository.save(createTeamWithName(TEAM_NAME + "-bulk-1"));
    Team secondTeam = this.teamRepository.save(createTeamWithName(TEAM_NAME + "-bulk-2"));
    Inject inject = saveInjectWithTeams(List.of(firstTeam, secondTeam));

    TeamBulkProcessingInput input = new TeamBulkProcessingInput();
    input.setTeamIdsToProcess(List.of(firstTeam.getId(), secondTeam.getId()));

    // --EXECUTE--
    mvc.perform(
            delete(TEAM_URI)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
    entityManager.flush();

    // --ASSERT--
    assertFalse(this.teamRepository.existsById(firstTeam.getId()));
    assertFalse(this.teamRepository.existsById(secondTeam.getId()));
    assertTrue(this.injectRepository.findById(inject.getId()).isPresent());
  }

  private Inject saveInjectWithTeams(List<Team> teams) {
    Exercise exercise = this.exerciseService.createExercise(ExerciseFixture.getExercise());
    // Linking the teams to the simulation too, as the import does: this is what makes the delete
    // event load the injects back into the session
    exercise.getTeams().addAll(teams);
    Inject inject =
        getInjectForEmailContract(injectorContractFixture.getWellKnownSingleEmailContract());
    inject.setExercise(exercise);
    inject.setTeams(new ArrayList<>(teams));
    Inject savedInject = this.injectRepository.save(inject);
    // The deletion must start from a cold session, as in production
    entityManager.flush();
    entityManager.clear();
    return savedInject;
  }

  // Options endpoint tests

  private Inject prepareOptionsEndpointTestData() {
    // Teams
    Team team1input = new Team();
    team1input.setName(TEAM_NAME + "1");
    Team team1 = this.teamRepository.save(team1input);
    Team team2input = new Team();
    team2input.setName(TEAM_NAME + "2");
    Team team2 = this.teamRepository.save(team2input);
    Team team3input = new Team();
    team3input.setName(TEAM_NAME + "3");
    Team team3 = this.teamRepository.save(team3input);
    Team team4input = new Team();
    team4input.setName(TEAM_NAME + "4");
    Team team4 = this.teamRepository.save(team4input);
    Exercise exInput = ExerciseFixture.getExercise();
    Exercise exercise = this.exerciseService.createExercise(exInput);
    // Inject
    Inject inject =
        getInjectForEmailContract(injectorContractFixture.getWellKnownSingleEmailContract());
    inject.setExercise(exercise);
    inject.setTeams(
        new ArrayList<>() {
          {
            add(team1);
            add(team2);
            add(team3);
            add(team4);
          }
        });
    return this.injectRepository.save(inject);
  }

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of(
            null, false, 0), // Case 1: searchText is null and simulationOrScenarioId is null
        Arguments.of(
            TEAM_NAME, false, 0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            TEAM_NAME + "2",
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            null, true, 4), // Case 3: searchText is null and simulationOrScenarioId is valid
        Arguments.of(
            TEAM_NAME, true, 4), // Case 4: searchText is valid and simulationOrScenarioId is valid
        Arguments.of(
            TEAM_NAME + "2",
            true,
            1) // Case 5: searchText is valid and simulationOrScenarioId is valid
        );
  }

  @DisplayName("Test optionsByName")
  @ParameterizedTest
  @MethodSource("optionsByNameTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByNameTest(
      String searchText, Boolean simulationOrScenarioId, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject i = prepareOptionsEndpointTestData();
    Exercise exercise = i.getExercise();

    // --EXECUTE--;
    String response =
        mvc.perform(
                get(TEAM_URI + "/options")
                    .queryParam("searchText", searchText)
                    .queryParam("sourceId", simulationOrScenarioId ? exercise.getId() : null)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  Stream<Arguments> optionsByIdTestParameters() {
    return Stream.of(
        Arguments.of(0, 0), // Case 1: 0 ID given
        Arguments.of(1, 1), // Case 1: 1 ID given
        Arguments.of(2, 2) // Case 2: 2 IDs given
        );
  }

  @DisplayName("Test optionsById")
  @ParameterizedTest
  @MethodSource("optionsByIdTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByIdTest(Integer numberOfTeamToProvide, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject inject = prepareOptionsEndpointTestData();
    List<Team> teams = inject.getTeams();

    List<String> teamIdsToSearch = new ArrayList<>();
    for (int i = 0; i < numberOfTeamToProvide; i++) {
      teamIdsToSearch.add(teams.get(i).getId());
    }

    // --EXECUTE--;
    String response =
        mvc.perform(
                post(TEAM_URI + "/options")
                    .content(asJsonString(teamIdsToSearch))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  // -- TENANT ISOLATION TESTS --

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Team created in tenant X should NOT be readable from tenant Y")
    void given_teamInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("Isolation Test Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // Act — read from tenant Y (expect 404)
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/teams/" + teamId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Team created in tenant X should be readable from tenant X")
    void given_teamInTenantX_should_beReadableFromTenantX() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("Same Tenant Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      // Act & Assert — read from same tenant should succeed
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/teams/" + teamId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.team_name").value("Same Tenant Team"));
    }

    @Test
    @DisplayName("Team search in tenant Y should NOT return teams from tenant X")
    void given_teamInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("CrossTenantSearchTeam");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/teams")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

      // Act — search from tenant Y
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantSearchTeam");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/teams/search")
                      .content(asJsonString(searchInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — no results from tenant X
      assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
    }

    @Test
    @DisplayName("Team created in tenant X should NOT be updatable from tenant Y")
    void given_teamInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("Update Isolation Test Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // Act — update from tenant Y
      TeamCreateInput updateInput = createTeam();
      updateInput.setName("Hijacked Team Name");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/teams/" + teamId)
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Team created in tenant X should NOT be deletable from tenant Y")
    void given_teamInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(Capability.DELETE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("Delete Isolation Test Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // Act — delete from tenant Y
      int responseStatus =
          mvc.perform(delete("/api/tenants/" + tenantY.getId() + "/teams/" + teamId).with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Team players in tenant X should NOT be readable from tenant Y")
    void given_teamInTenantX_should_notHavePlayersReadableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("Players Isolation Test Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // Act — read players from tenant Y (expect 404)
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/teams/" + teamId + "/players")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Team players in tenant X should NOT be updatable from tenant Y")
    void given_teamInTenantX_should_notHavePlayersUpdatableFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("UpdatePlayers Isolation Test Team");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      entityManager.flush();
      entityManager.clear();

      // Act — update players from tenant Y (expect 404)
      UpdateUsersTeamInput updateUsersInput = new UpdateUsersTeamInput();
      updateUsersInput.setUserIds(List.of());

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/teams/" + teamId + "/players")
                      .content(asJsonString(updateUsersInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Teams options with ALL_INJECTS should NOT leak teams from another tenant")
    void given_injectTeamInTenantX_should_notAppearInTenantYAllInjectsOptions() throws Exception {
      // Arrange — regression test for the OR-precedence bug in
      // findAllTeamsForAtomicTestingsSimulationsAndScenarios: the EXISTS(injects_teams)
      // clause used to escape the tenant predicate and leak teams across tenants.
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TEAMS_AND_PLAYERS, Capability.ACCESS_TEAMS_AND_PLAYERS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TEAMS_AND_PLAYERS));

      TeamCreateInput input = createTeam();
      input.setName("CrossTenantOptionsTeam");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/teams")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String teamId = JsonPath.read(createResponse, "$.team_id");

      // Reference the team from an inject (in tenant X) so it matches the EXISTS clause
      tenantIsolationHelper.switchToTenant(tenantX.getId(), entityManager);
      Team team = teamRepository.findById(teamId).orElseThrow();
      Inject inject =
          getInjectForEmailContract(injectorContractFixture.getWellKnownSingleEmailContract());
      inject.setTeams(new ArrayList<>(List.of(team)));
      injectRepository.save(inject);

      entityManager.flush();
      entityManager.clear();

      // Act — fetch options with ALL_INJECTS from tenant Y
      String crossTenantResponse =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/teams/options")
                      .queryParam("inputFilterOption", "ALL_INJECTS")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — the tenant X team must not leak into tenant Y options
      List<String> crossTenantIds = JsonPath.read(crossTenantResponse, "$[*].id");
      assertFalse(crossTenantIds.contains(teamId));

      // Positive control — the team is returned for its own tenant
      String sameTenantResponse =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/teams/options")
                      .queryParam("inputFilterOption", "ALL_INJECTS")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> sameTenantIds = JsonPath.read(sameTenantResponse, "$[*].id");
      assertTrue(sameTenantIds.contains(teamId));
    }
  }
}
