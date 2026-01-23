package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectTestStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectTestStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class ExerciseInjectTestApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectTestStatusComposer injectTestStatusComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;

  private Exercise simulation;
  private Inject inject1, inject2;
  private InjectTestStatus injectTestStatus1, injectTestStatus2;

  @BeforeAll
  void setupData() {
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();

    InjectTestStatusComposer.Composer injectTestStatusComposer1 =
        injectTestStatusComposer.forInjectTestStatus(
            InjectTestStatusFixture.createSuccessInjectStatus());

    InjectTestStatusComposer.Composer injectTestStatusComposer2 =
        injectTestStatusComposer.forInjectTestStatus(
            InjectTestStatusFixture.createSuccessInjectStatus());

    InjectComposer.Composer injectComposer1 =
        injectComposer
            .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
            .withInjectTestStatus(injectTestStatusComposer1);

    InjectComposer.Composer injectComposer2 =
        injectComposer
            .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
            .withInjectTestStatus(injectTestStatusComposer2);

    inject1 = injectComposer1.persist().get();
    inject2 = injectComposer2.persist().get();

    injectTestStatus1 = injectTestStatusComposer1.persist().get();
    injectTestStatus2 = injectTestStatusComposer2.persist().get();

    simulation =
        simulationComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInjects(List.of(injectComposer1, injectComposer2))
            .persist()
            .get();
  }

  @Nested
  @DisplayName("As SimulationPlanner")
  class SimulationPlannerAccess {

    @Test
    @DisplayName("Should return paginated inject test results when inject tests exist")
    @WithMockUser
    void should_return_paginated_results_when_inject_tests_exist() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());

      SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/{simulationId}/injects/test/search", simulation.getId())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response)
          .inPath("$.content[*].status_id")
          .isArray()
          .contains(injectTestStatus1.getId());
    }

    @Test
    @DisplayName("Should return test status using test id")
    @WithMockUser
    void should_return_test_status_by_testId() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());
      mvc.perform(get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return test status when testing a specific inject")
    @WithMockUser(isAdmin = true)
    void should_return_test_status_when_testing_specific_inject() throws Exception {
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulation.getId(),
                  inject1.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_id").value(inject1.getId()));
    }

    @Test
    @DisplayName("Should return test statuses when performing bulk test with inject IDs")
    @WithMockUser
    void should_return_test_statuses_when_bulk_testing_with_inject_ids() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER, simulation.getId());

      InjectBulkProcessingInput input = new InjectBulkProcessingInput();
      input.setInjectIDsToProcess(List.of(inject1.getId()));
      input.setSimulationOrScenarioId(simulation.getId());

      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return 200 when deleting an inject test status")
    @WithMockUser(isAdmin = true)
    void should_return_200_when_fetching_deleting_an_inject_test_status() throws Exception {
      mvc.perform(
              delete(
                  EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                  simulation.getId(),
                  injectTestStatus2.getId()))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("As Unauthorized User")
  class UnauthorizedUserAccess {

    @Test
    @DisplayName("Should return 200 when search a paginated inject test results")
    @WithMockUser
    void should_return_200_when_search_paginated_results() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test/search", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 200 when search by id")
    @WithMockUser
    void should_return_200_when_search_by_testId() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      mvc.perform(get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 403 when testing a specific inject")
    @WithMockUser
    void should_return_403_when_testing_specific_inject() throws Exception {
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulation.getId(),
                  inject1.getId()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when performing bulk test with inject IDs")
    @WithMockUser
    void should_return_403_when_bulk_testing_with_inject_ids() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());

      InjectBulkProcessingInput input = new InjectBulkProcessingInput();
      input.setInjectIDsToProcess(List.of(inject1.getId(), inject2.getId()));
      input.setSimulationOrScenarioId(simulation.getId());

      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 403 when fetching a deleted inject test status")
    @WithMockUser
    void should_return_403_when_fetching_deleted_inject_test_status() throws Exception {
      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, simulation.getId());
      mvc.perform(
              delete(
                  EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                  simulation.getId(),
                  injectTestStatus1.getId()))
          .andExpect(status().isForbidden());
    }
  }
}
