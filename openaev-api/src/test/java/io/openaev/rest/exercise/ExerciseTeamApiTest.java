package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Team;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ExerciseTeamApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;

  @DisplayName("Exercise team search")
  @Nested
  @WithMockUser(isAdmin = true)
  class SearchExerciseTeams {
    @Test
    @DisplayName("Returns global and exercise teams when searching teams")
    void givenContextualOnlyFalse_whenSearchingTeams_shouldReturnGlobalAndExerciseTeams()
        throws Exception {
      Team team = new Team();
      String teamName = "Team test";
      team.setName(teamName);

      Team contextualTeam = new Team();
      contextualTeam.setName(teamName + " 2");
      contextualTeam.setContextual(true);
      List<Team> savedTeams = teamRepository.saveAll(List.of(team, contextualTeam));

      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTeams(savedTeams.stream().filter(Team::getContextual).toList());
      Exercise exerciseCreated = exerciseRepository.save(exercise);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(EXERCISE_URI
                      + "/"
                      + exerciseCreated.getId()
                      + "/teams/search?contextualOnly=false")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.size()").value(2))
          .andExpect(
              jsonPath(
                  "$.content[*].team_id",
                  containsInAnyOrder(savedTeams.stream().map(Team::getId).toArray(String[]::new))));
    }

    @Test
    @DisplayName("Returns only exercise teams")
    void givenContextualOnlyTrue_whenSearchingTeams_shouldReturnOnlyExerciseTeams()
        throws Exception {
      Team team = new Team();
      String teamName = "Team test";
      team.setName(teamName);

      Team team1 = new Team();
      team1.setName(teamName + "1");

      Team contextualTeam = new Team();
      contextualTeam.setName(teamName + "3");
      contextualTeam.setContextual(true);

      Team contextualTeam1 = new Team();
      contextualTeam1.setName(teamName + "4");
      contextualTeam1.setContextual(true);

      teamRepository.saveAll(List.of(team, contextualTeam));
      List<Team> exerciseTeams = teamRepository.saveAll(List.of(team1, contextualTeam1));

      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTeams(exerciseTeams);
      Exercise exerciseCreated = exerciseRepository.save(exercise);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getOptioned();
      searchPaginationInput.setTextSearch(teamName);

      mvc.perform(
              post(EXERCISE_URI
                      + "/"
                      + exerciseCreated.getId()
                      + "/teams/search?contextualOnly=true")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.size()").value(2))
          .andExpect(
              jsonPath(
                  "$.content[*].team_id",
                  containsInAnyOrder(
                      exerciseTeams.stream().map(Team::getId).toArray(String[]::new))));
    }
  }
}
