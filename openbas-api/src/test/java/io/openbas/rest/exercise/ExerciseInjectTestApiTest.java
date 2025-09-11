package io.openbas.rest.exercise;

import static io.openbas.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openbas.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.database.model.Exercise;
import io.openbas.database.model.Inject;
import io.openbas.database.model.InjectTestStatus;
import io.openbas.database.model.InjectorContract;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.rest.inject.form.InjectBulkProcessingInput;
import io.openbas.utils.fixtures.ExerciseFixture;
import io.openbas.utils.fixtures.InjectFixture;
import io.openbas.utils.fixtures.InjectTestStatusFixture;
import io.openbas.utils.fixtures.composers.ExerciseComposer;
import io.openbas.utils.fixtures.composers.InjectComposer;
import io.openbas.utils.fixtures.composers.InjectTestStatusComposer;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
public class ExerciseInjectTestApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectTestStatusComposer injectTestStatusComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private Exercise simulation;
  private Inject inject1, inject2;
  private InjectTestStatus injectTestStatus1, injectTestStatus2;

  @BeforeAll
  void setupData() {
    InjectorContract injectorContract =
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();

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
    @WithMockAdminUser // FIXME: Temporary workaround for grant issue
    void should_return_paginated_results_when_inject_tests_exist() throws Exception {
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
    @WithMockAdminUser // FIXME: Temporary workaround for grant issue
    void should_return_test_status_by_testId() throws Exception {
      mvc.perform(get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return test status when testing a specific inject")
    @WithMockAdminUser
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
    @WithMockAdminUser // FIXME: Temporary workaround for grant issue
    void should_return_test_statuses_when_bulk_testing_with_inject_ids() throws Exception {
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
    @WithMockAdminUser
    void should_return_200_when_fetching_deleting_an_inject_test_status() throws Exception {
      mvc.perform(
              delete(
                  EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                  simulation.getId(),
                  injectTestStatus2.getId()))
          .andExpect(status().isOk());
    }
  }

  // FIXME these tests are not applicable anymore and needs to be refactored
  /*@Nested
  @DisplayName("As Unauthorized User")
  class UnauthorizedUserAccess {

    @Test
    @DisplayName("Should return 200 when search a paginated inject test results")
    @WithMockAdminUser // FIXME: Temporary workaround for grant issue
    void should_return_200_when_search_paginated_results() throws Exception {
      SearchPaginationInput searchPaginationInput = new SearchPaginationInput();
      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test/search", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 200 when search by id")
    @WithMockAdminUser // FIXME: Temporary workaround for grant issue
    void should_return_200_when_search_by_testId() throws Exception {
      mvc.perform(get(EXERCISE_URI + "/injects/test/{testId}", injectTestStatus1.getId()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 404 when testing a specific inject")
    @WithMockObserverUser
    void should_return_404_when_testing_specific_inject() throws Exception {
      mvc.perform(
              get(
                  EXERCISE_URI + "/{simulationId}/injects/{injectId}/test",
                  simulation.getId(),
                  inject1.getId()))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when performing bulk test with inject IDs")
    @WithMockObserverUser
    void should_return_404_when_bulk_testing_with_inject_ids() throws Exception {
      InjectBulkProcessingInput input = new InjectBulkProcessingInput();
      input.setInjectIDsToProcess(List.of(inject1.getId(), inject2.getId()));
      input.setSimulationOrScenarioId(simulation.getId());

      mvc.perform(
              post(EXERCISE_URI + "/{simulationId}/injects/test", simulation.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when fetching a deleted inject test status")
    @WithMockObserverUser
    void should_return_404_when_fetching_deleted_inject_test_status() throws Exception {
      mvc.perform(
              delete(
                  EXERCISE_URI + "/{simulationId}/injects/test/{testId}",
                  simulation.getId(),
                  injectTestStatus1.getId()))
          .andExpect(status().isNotFound());
    }
  }*/
}
