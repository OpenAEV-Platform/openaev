package io.openbas.rest.exercise;

import static io.openbas.database.model.ExerciseStatus.SCHEDULED;
import static io.openbas.database.model.Filters.FilterOperator.contains;
import static io.openbas.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.database.model.Exercise;
import io.openbas.database.repository.ExerciseRepository;
import io.openbas.utils.fixtures.ExerciseFixture;
import io.openbas.utils.fixtures.PaginationFixture;
import io.openbas.utils.mockUser.WithMockUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import io.openbas.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
public class ExerciseApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ExerciseRepository exerciseRepository;

  private static final List<String> EXERCISE_IDS = new ArrayList<>();

  @BeforeAll
  void beforeAll() {
    Exercise exercise1 = ExerciseFixture.createDefaultCrisisExercise();
    Exercise exercise1Saved = this.exerciseRepository.save(exercise1);
    EXERCISE_IDS.add(exercise1Saved.getId());

    Exercise exercise2 = ExerciseFixture.createDefaultIncidentResponseExercise();
    Exercise exercise2Saved = this.exerciseRepository.save(exercise2);
    EXERCISE_IDS.add(exercise2Saved.getId());
  }

  @AfterAll
  void afterAll() {
    this.exerciseRepository.deleteAllById(EXERCISE_IDS);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving exercises")
  class RetrievingExercises {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of exercises")
    class SearchingPageOfExercises {

      @Test
      @DisplayName("Retrieving first page of exercises by text search")
      void given_working_search_input_should_return_a_page_of_exercises() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Not retrieving first page of exercises by text search")
      void given_not_working_search_input_should_return_a_page_of_exercises() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
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
      @DisplayName("Sorting page of exercises by name")
      void given_sorting_input_by_name_should_return_a_page_of_exercises_sort_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("exercise_name").build()))
                .build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].exercise_name").value("Crisis exercise"))
            .andExpect(jsonPath("$.content.[1].exercise_name").value("Incident response exercise"));
      }

      @Test
      @DisplayName("Sorting page of exercises by start date")
      void given_sorting_input_by_start_date_should_return_a_page_of_exercises_sort_by_start_date()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("exercise_start_date")
                            .direction("desc")
                            .build()))
                .build();

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[1].exercise_name").value("Incident response exercise"))
            .andExpect(jsonPath("$.content.[0].exercise_name").value("Crisis exercise"));
      }
    }

    @Nested
    @DisplayName("Filtering page of exercises")
    class FilteringPageOfExercises {

      @Test
      @DisplayName("Filtering page of exercises by name")
      void given_filter_input_by_name_should_return_a_page_of_exercises_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("exercise_name", "Crisis", contains);

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of exercises by status")
      void given_filter_input_by_sttaus_should_return_a_page_of_exercises_filter_by_status()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "exercise_status", valueOf(SCHEDULED), contains);

        mvc.perform(
                post(EXERCISE_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }
    }
  }
}
