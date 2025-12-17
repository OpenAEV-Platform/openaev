package io.openaev.rest.exercise;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.model.ExerciseStatus.SCHEDULED;
import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exercise.form.GetExercisesInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ExerciseApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private UserRepository userRepository;

  private static final List<String> EXERCISE_IDS = new ArrayList<>();

  @BeforeEach
  void beforeAll() {
    Exercise exercise1 = ExerciseFixture.createDefaultCrisisExercise();
    Exercise exercise1Saved = this.exerciseRepository.save(exercise1);
    EXERCISE_IDS.add(exercise1Saved.getId());

    Exercise exercise2 = ExerciseFixture.createDefaultIncidentResponseExercise();
    Exercise exercise2Saved = this.exerciseRepository.save(exercise2);
    EXERCISE_IDS.add(exercise2Saved.getId());
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
      void given_filter_input_by_status_should_return_a_page_of_exercises_filter_by_status()
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

      @Test
      @DisplayName("Search exercises by ids as admin")
      void given_list_of_ids_select_exercises() throws Exception {
        GetExercisesInput getExercisesInput = new GetExercisesInput();
        getExercisesInput.setExerciseIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getExercisesInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search exercises by ids as user granted")
      @WithMockUser(isAdmin = false, withCapabilities = Capability.ACCESS_ASSESSMENT)
      void given_list_of_ids_select_exercises_with_capabilities() throws Exception {
        GetExercisesInput getExercisesInput = new GetExercisesInput();
        getExercisesInput.setExerciseIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getExercisesInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search exercises by ids as user non granted")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_exercises_without_capabilities_no_grants() throws Exception {
        GetExercisesInput getExercisesInput = new GetExercisesInput();
        getExercisesInput.setExerciseIds(EXERCISE_IDS);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getExercisesInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
      }

      @Test
      @DisplayName("Search exercises by ids as user granted on some exercises")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_exercises_without_capabilities_with_grants() throws Exception {
        Exercise exerciseGranted = ExerciseFixture.createDefaultExercise();
        User user = userRepository.findById(currentUser().getId()).get();
        Group group = new Group();
        group.setName("test");
        group = groupRepository.save(group);
        exerciseGranted.getGrants().addAll(group.getGrants());
        exerciseGranted = exerciseRepository.save(exerciseGranted);

        Grant grantObserver = new Grant();
        grantObserver.setResourceId(exerciseGranted.getId());
        grantObserver.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
        grantObserver.setGroup(group);
        grantObserver.setName(Grant.GRANT_TYPE.OBSERVER);
        Grant grantPlanner = new Grant();
        grantPlanner.setResourceId(exerciseGranted.getId());
        grantPlanner.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
        grantPlanner.setGroup(group);
        grantPlanner.setName(Grant.GRANT_TYPE.PLANNER);
        grantRepository.saveAll(List.of(grantObserver, grantPlanner));
        group.setGrants(new ArrayList<>(List.of(grantObserver, grantPlanner)));
        group.setUsers(new ArrayList<>(List.of(user)));
        groupRepository.save(group);
        EXERCISE_IDS.add(exerciseGranted.getId());

        GetExercisesInput getExercisesInput = new GetExercisesInput();
        List<String> exerciseIds = new ArrayList<>();
        exerciseIds.add(exerciseGranted.getId());
        exerciseIds.addAll(EXERCISE_IDS);
        getExercisesInput.setExerciseIds(exerciseIds);

        mvc.perform(
                post(EXERCISE_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getExercisesInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));

        exerciseRepository.delete(exerciseGranted);
      }
    }
  }
}
