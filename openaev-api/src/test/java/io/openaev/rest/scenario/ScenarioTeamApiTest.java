package io.openaev.rest.scenario;

import static io.openaev.database.specification.TeamSpecification.fromScenario;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static io.openaev.utils.fixtures.ScenarioFixture.getScenario;
import static io.openaev.utils.fixtures.TeamFixture.TEAM_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScenarioTeamUser;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.ScenarioTeamUserRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exercise.form.ExerciseTeamPlayersEnableInput;
import io.openaev.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openaev.rest.scenario.form.ScenarioUpdateTeamsInput;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class ScenarioTeamApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioTeamUserRepository scenarioTeamUserRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private UserRepository userRepository;

  @DisplayName("Given a valid scenario and team input, should add team to scenario successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validScenarioAndTeamInput_should_replaceTeamToScenarioSuccessfully() throws Exception {
    // -- PREPARE --
    Scenario scenario = getScenario();
    Team teamToRemove = new Team();
    teamToRemove.setName("teamToRemove");
    Team teamToRemoveSaved = this.teamRepository.save(teamToRemove);
    scenario.setTeams(List.of(teamToRemoveSaved));
    Scenario scenarioCreated = this.scenarioRepository.save(scenario);

    Team teamToAdd = new Team();
    teamToAdd.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(teamToAdd);
    ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
    input.setTeamIds(List.of(teamCreated.getId()));

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                put(SCENARIO_URI + "/" + scenarioCreated.getId() + "/teams/replace")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> responseList =
        objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});
    assertEquals(2, responseList.size());
    Map<String, Map<String, Object>> responseMap =
        responseList.stream()
            .collect(
                Collectors.toMap(
                    obj -> (String) obj.get("team_name"),
                    obj -> obj,
                    (existing, duplicate) -> {
                      throw new IllegalStateException("Duplicate key found: " + existing);
                    }));

    assertEquals(List.of(), responseMap.get("teamToRemove").get("team_scenarios"));
    assertEquals(
        List.of(scenarioCreated.getId()), responseMap.get(TEAM_NAME).get("team_scenarios"));

    Optional<Scenario> scenarioSaved = this.scenarioRepository.findById(scenarioCreated.getId());
    assertTrue(scenarioSaved.isPresent());
    assertEquals(1, (long) scenarioSaved.get().getTeams().size());
    assertEquals(teamCreated.getId(), scenarioSaved.get().getTeams().getFirst().getId());
  }

  @DisplayName("Given a valid scenario with teams, should retrieve teams successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validScenarioWithTeams_should_retrieveTeamsSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    Scenario scenario = getScenario();
    scenario.setTeams(List.of(teamCreated));
    Scenario scenarioCreated = this.scenarioRepository.save(scenario);

    // -- EXECUTE --
    String response =
        this.mvc
            .perform(
                get(SCENARIO_URI + "/" + scenarioCreated.getId() + "/teams")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(teamCreated.getId(), JsonPath.read(response, "$[0].team_id"));
  }

  @DisplayName("Given a valid scenario and team, should add player to team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validScenarioAndTeam_should_addPlayerToTeamSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    Scenario scenario = getScenario();
    scenario.setTeams(List.of(teamCreated));
    Scenario scenarioCreated = this.scenarioRepository.save(scenario);

    User user = UserFixture.getUser();
    User userCreated = this.userRepository.save(user);

    ScenarioTeamPlayersEnableInput input = new ScenarioTeamPlayersEnableInput();
    input.setPlayersIds(List.of(userCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI
                    + "/"
                    + scenarioCreated.getId()
                    + "/teams/"
                    + teamCreated.getId()
                    + "/players/add")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<ScenarioTeamUser> scenarioTeamUsers =
        fromIterable(this.scenarioTeamUserRepository.findAll());
    assertTrue(!scenarioTeamUsers.isEmpty());
    assertTrue(
        scenarioTeamUsers.stream().anyMatch(s -> userCreated.getId().equals(s.getUser().getId())));
  }

  @DisplayName(
      "Given a valid scenario and team with a player, should remove player from team successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validScenarioAndTeamWithPlayer_should_removePlayerFromTeamSuccessfully()
      throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    Scenario scenario = getScenario();
    scenario.setTeams(List.of(teamCreated));
    Scenario scenarioCreated = this.scenarioRepository.save(scenario);

    User user = UserFixture.getUser();
    User userCreated = this.userRepository.save(user);

    ScenarioTeamUser scenarioTeamUser = new ScenarioTeamUser();
    scenarioTeamUser.setScenario(scenarioCreated);
    scenarioTeamUser.setTeam(teamCreated);
    scenarioTeamUser.setUser(userCreated);
    this.scenarioTeamUserRepository.save(scenarioTeamUser);

    ExerciseTeamPlayersEnableInput input = new ExerciseTeamPlayersEnableInput();
    input.setPlayersIds(List.of(userCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI
                    + "/"
                    + scenarioCreated.getId()
                    + "/teams/"
                    + teamCreated.getId()
                    + "/players/remove")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<ScenarioTeamUser> scenarioTeamUsers =
        fromIterable(this.scenarioTeamUserRepository.findAll());
    assertTrue(!scenarioTeamUsers.isEmpty());
    assertFalse(scenarioTeamUsers.stream().anyMatch(s -> s.getScenario() == null));
  }

  @DisplayName("Given a valid scenario with a team, should remove team from scenario successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validScenarioWithTeam_should_removeTeamFromScenarioSuccessfully() throws Exception {
    // -- PREPARE --
    Team team = new Team();
    team.setName(TEAM_NAME);
    Team teamCreated = this.teamRepository.save(team);

    Scenario scenario = getScenario();
    scenario.setTeams(List.of(teamCreated));
    Scenario scenarioCreated = this.scenarioRepository.save(scenario);

    ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
    input.setTeamIds(List.of(teamCreated.getId()));

    // -- EXECUTE --
    this.mvc
        .perform(
            put(SCENARIO_URI + "/" + scenarioCreated.getId() + "/teams/remove")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // -- ASSERT --
    List<Team> teams = this.teamRepository.findAll(fromScenario(scenarioCreated.getId()));
    assertTrue(teams.isEmpty());
  }
}
