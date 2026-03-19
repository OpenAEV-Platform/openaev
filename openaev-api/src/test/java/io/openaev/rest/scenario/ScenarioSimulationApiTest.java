package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class ScenarioSimulationApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TagRepository tagRepository;

  private static Scenario scenario;
  private static Exercise exercise1FromScenario;
  private static Exercise exercise2FromScenario;
  private static Exercise exercise3NotFromScenario;

  @BeforeEach
  void beforeEach() {
    // Create scenarios
    Scenario defaultScenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario = this.scenarioRepository.save(defaultScenario);

    // Create exercises linked to the scenario
    this.exerciseRepository.deleteAll();
    Exercise exercise1 = ExerciseFixture.createDefaultCrisisExercise();
    exercise1.setScenario(scenario);
    exercise1FromScenario = this.exerciseRepository.save(exercise1);
    Exercise exercise2 = ExerciseFixture.createDefaultIncidentResponseExercise();
    exercise2.setScenario(scenario);
    exercise2FromScenario = this.exerciseRepository.save(exercise2);

    // Create an exercise not linked to the scenario
    Exercise exercise3 = ExerciseFixture.createDefaultCrisisExercise();
    exercise3NotFromScenario = this.exerciseRepository.save(exercise3);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving exercises of a scenario")
  class RetrievingExercisesOfScenario {

    @Nested
    @DisplayName("Filtering page of exercises")
    class FilteringPageOfExercises {
      @Test
      @DisplayName("Retrieve all exercises of a scenario")
      void given_scenarioId_should_return_all_exercises_of_scenario() throws Exception {
        SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();
        List<String> expectedIds =
            List.of(exercise1FromScenario.getId(), exercise2FromScenario.getId());

        String response =
            mvc.perform(
                    post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(searchPaginationInput)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.numberOfElements").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThatJson(response)
            .inPath("$.content[*].exercise_id")
            .isArray()
            .containsExactlyInAnyOrderElementsOf(expectedIds);
      }

      @Test
      @DisplayName("Filtering page of exercises by tag")
      void given_scenarioId_and_tag_should_return_filtered_exercises_of_scenario()
          throws Exception {
        Tag tagToAdd = TagFixture.getTagWithText("ScenarioSimulationApiTestTag");
        Tag tag = tagRepository.save(tagToAdd);
        Set<Tag> tags = new HashSet<>();
        tags.add(tag);
        exercise2FromScenario.setTags(tags);
        exerciseRepository.save(exercise2FromScenario);
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "exercise_tags", tag.getId(), Filters.FilterOperator.eq);
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1))
            .andExpect(jsonPath("$.content[0].exercise_id").value(exercise2FromScenario.getId()));
        exercise2FromScenario.setTags(null);
        exerciseRepository.save(exercise2FromScenario);
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of exercises")
    class SortingPageOfExercises {
      @Test
      @DisplayName("Sorting page of exercises by updated at date")
      void given_sorting_input_by_updateDate_should_return_a_page_of_exercises_sort_by_updateDate()
          throws Exception {
        exercise2FromScenario.setUpdatedAt(Instant.now());
        exercise1FromScenario.setUpdatedAt(Instant.now().minusSeconds(60));
        exerciseRepository.saveAll(Arrays.asList(exercise1FromScenario, exercise2FromScenario));
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_updated_at")
                            .direction("DESC")
                            .build()))
                .build();
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2))
            .andExpect(jsonPath("$.content[0].exercise_id").value(exercise2FromScenario.getId()))
            .andExpect(jsonPath("$.content[1].exercise_id").value(exercise1FromScenario.getId()));
      }

      @Test
      @DisplayName("Sorting page of exercises by last end date otherwise by update date")
      void
          given_sorting_input_by_lastEndDate_should_return_a_page_of_exercises_sort_by_lastEndDate()
              throws Exception {
        Instant now = Instant.now();
        // end date = yesterday
        exercise2FromScenario.setEnd(now.minusSeconds(24 * 60 * 60));
        exercise2FromScenario.setUpdatedAt(now.minusSeconds(24 * 60 * 60));

        exercise3NotFromScenario.setScenario(scenario);
        exercise3NotFromScenario.setEnd(now.minusSeconds(24 * 60 * 60 * 2));
        exercise3NotFromScenario.setUpdatedAt(now.minusSeconds(60 * 60));

        exercise1FromScenario.setEnd(null);
        exercise1FromScenario.setUpdatedAt(now);

        exerciseRepository.saveAll(
            Arrays.asList(exercise1FromScenario, exercise2FromScenario, exercise3NotFromScenario));
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_end_date")
                            .direction("DESC")
                            .nullHandling(Sort.NullHandling.NULLS_LAST)
                            .build(),
                        SortField.builder()
                            .property("exercise_updated_at")
                            .direction("DESC")
                            .build()))
                .build();
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(3))
            .andExpect(jsonPath("$.content[0].exercise_id").value(exercise2FromScenario.getId()))
            .andExpect(jsonPath("$.content[1].exercise_id").value(exercise3NotFromScenario.getId()))
            .andExpect(jsonPath("$.content[2].exercise_id").value(exercise1FromScenario.getId()));
      }
    }

    @Nested
    @DisplayName("Searching page of exercises of a scenario")
    class SearchingPageOfExercises {
      @Test
      @DisplayName("Retrieving first page of exercises by text search")
      void given_working_search_input_should_return_a_page_of_exercises() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1))
            .andExpect(jsonPath("$.content[0].exercise_id").value(exercise1FromScenario.getId()));
      }

      @Test
      @DisplayName("Not retrieving first page of exercises by text search")
      void given_notworking_search_input_should_notreturn_a_page_of_exercises() throws Exception {
        String searchText = "wrong";
        exercise3NotFromScenario.setName(searchText);
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch(searchText).build();
        mvc.perform(
                post(SCENARIO_URI + "/" + scenario.getId() + "/exercises/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }
  }
}
