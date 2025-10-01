package io.openaev.rest;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.TeamFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Team;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.team.form.TeamCreateInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class TeamApiTest extends IntegrationTest {

  private static final String SEARCH_INPUT = "search input";

  @Autowired private MockMvc mvc;

  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private TeamRepository teamRepository;

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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName, JsonPath.read(response, "$.team_name"));
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
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
                        .accept(MediaType.APPLICATION_JSON)));

    String expectedMessage = "Contextual team can only be associated to one exercise";
    String actualMessage = exception.getMessage();

    // --ASSERT--
    assertTrue(actualMessage.contains(expectedMessage));
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
        getInjectForEmailContract(
            this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow());
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
                    .queryParam(
                        "simulationOrScenarioId", simulationOrScenarioId ? exercise.getId() : null)
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }
}
