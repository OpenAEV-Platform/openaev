package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("performance")
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Exercise Team Search - Performance")
public class ExerciseTeamApiPerformanceTest extends IntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(ExerciseTeamApiPerformanceTest.class);

  private static final long MAX_RESPONSE_TIME_MS = 3000;

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;

  @Nested
  @DisplayName("Search teams with contextualOnly=false")
  @WithMockUser(isAdmin = true)
  class SearchAllTeams {

    @ParameterizedTest(name = "given {0} teams, response time should be under threshold")
    @ValueSource(ints = {100, 1000, 3000, 10000})
    void given_N_teams_should_respond_within_threshold(int teamCount) throws Exception {
      // -- Arrange --
      List<Team> teams = createTeams(teamCount);
      List<Team> savedTeams = teamRepository.saveAll(teams);

      // Associate half the teams with the exercise (simulates realistic mix)
      List<Team> exerciseTeams = savedTeams.subList(0, teamCount / 2);
      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTeams(exerciseTeams);
      Exercise savedExercise = exerciseRepository.save(exercise);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

      // -- Act --
      long startNanos = System.nanoTime();

      mvc.perform(
              post(EXERCISE_URI
                      + "/"
                      + savedExercise.getId()
                      + "/teams/search?contextualOnly=false")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

      // -- Assert --
      log.info("PERF | teams={} | contextualOnly=false | responseTime={}ms", teamCount, elapsedMs);

      Assertions.assertTrue(
          elapsedMs < MAX_RESPONSE_TIME_MS,
          "Response time %dms exceeded threshold %dms for %d teams"
              .formatted(elapsedMs, MAX_RESPONSE_TIME_MS, teamCount));
    }
  }

  @Nested
  @DisplayName("Search teams with contextualOnly=true")
  @WithMockUser(isAdmin = true)
  class SearchContextualTeams {

    @ParameterizedTest(
        name = "given {0} teams in exercise, response time should be under threshold")
    @ValueSource(ints = {100, 1000, 3000, 10000})
    void given_N_exercise_teams_should_respond_within_threshold(int teamCount) throws Exception {
      // -- Arrange --
      List<Team> teams = createTeams(teamCount);
      teams.forEach(t -> t.setContextual(true));
      List<Team> savedTeams = teamRepository.saveAll(teams);

      Exercise exercise = ExerciseFixture.createDefaultExercise();
      exercise.setTeams(savedTeams);
      Exercise savedExercise = exerciseRepository.save(exercise);

      SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

      // -- Act --
      long startNanos = System.nanoTime();

      mvc.perform(
              post(EXERCISE_URI + "/" + savedExercise.getId() + "/teams/search?contextualOnly=true")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

      // -- Assert --
      log.info("PERF | teams={} | contextualOnly=true | responseTime={}ms", teamCount, elapsedMs);

      Assertions.assertTrue(
          elapsedMs < MAX_RESPONSE_TIME_MS,
          "Response time %dms exceeded threshold %dms for %d teams"
              .formatted(elapsedMs, MAX_RESPONSE_TIME_MS, teamCount));
    }
  }

  private List<Team> createTeams(int count) {
    List<Team> teams = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Team team = new Team();
      team.setName("perf-team-%d".formatted(i));
      teams.add(team);
    }
    return teams;
  }
}
