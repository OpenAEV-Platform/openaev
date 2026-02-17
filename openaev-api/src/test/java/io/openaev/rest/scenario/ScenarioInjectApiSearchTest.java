package io.openaev.rest.scenario;

import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.fixtures.InjectorContractFixture;
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

@TestInstance(PER_CLASS)
public class ScenarioInjectApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private InjectRepository injectRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;

  private static final List<String> INJECT_IDS = new ArrayList<>();
  private static String SCENARIO_ID;
  private static String EMAIL_INJECTOR_CONTRACT_ID;

  @BeforeAll
  void beforeAll() {
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    EMAIL_INJECTOR_CONTRACT_ID = injectorContract.getInjector().getId();

    Scenario scenario = createDefaultCrisisScenario();
    Scenario scenarioSaved = this.scenarioRepository.save(scenario);
    SCENARIO_ID = scenarioSaved.getId();

    Inject injectDefaultEmail = getInjectForEmailContract(injectorContract);
    injectDefaultEmail.setScenario(scenarioSaved);
    injectDefaultEmail.setTitle("Inject default email");
    injectDefaultEmail.setDependsDuration(1L);
    Inject injectDefaultEmailSaved = this.injectRepository.save(injectDefaultEmail);
    INJECT_IDS.add(injectDefaultEmailSaved.getId());

    Inject injectDefaultGlobal = getInjectForEmailContract(injectorContract);
    injectDefaultGlobal.setScenario(scenarioSaved);
    injectDefaultGlobal.setTitle("Inject global email");
    Inject injectDefaultGlobalSaved = this.injectRepository.save(injectDefaultGlobal);
    INJECT_IDS.add(injectDefaultGlobalSaved.getId());
  }

  @AfterAll
  void afterAll() {
    this.injectRepository.deleteAllById(INJECT_IDS);
    this.scenarioRepository.deleteById(SCENARIO_ID);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving injects")
  class RetrievingInjects {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of injects")
    class SearchingPageOfInjects {

      @Test
      @DisplayName("Retrieving first page of injects by textsearch")
      void given_working_search_input_should_return_a_page_of_injects() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("default").build();

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Not retrieving first page of injects by textsearch")
      void given_not_working_search_input_should_return_a_page_of_injects() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of injects")
    class SortingPageOfInjects {

      @Test
      @DisplayName("Sorting page of injects by name")
      void given_sorting_input_by_name_should_return_a_page_of_injects_sort_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("inject_title").build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].inject_title").value("Inject default email"))
            .andExpect(jsonPath("$.content.[1].inject_title").value("Inject global email"));
      }

      @Test
      @DisplayName("Sorting page of injects by updated at")
      void given_sorting_input_by_updated_at_should_return_a_page_of_injects_sort_by_updated_at()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("inject_depends_duration")
                            .direction("asc")
                            .build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].inject_title").value("Inject global email"))
            .andExpect(jsonPath("$.content.[1].inject_title").value("Inject default email"));
      }
    }

    @Nested
    @DisplayName("Filtering page of injects")
    class FilteringPageOfInjects {

      @Test
      @DisplayName("Filtering page of injects by name")
      void given_filter_input_by_name_should_return_a_page_of_injects_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("inject_title", "email", contains);

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }

      @Test
      @DisplayName("Filtering page of injects by injector contract")
      void
          given_filter_input_by_injector_contract_should_return_a_page_of_injects_filter_by_injector_contract()
              throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "inject_injector_contract", EMAIL_INJECTOR_CONTRACT_ID, contains);

        mvc.perform(
                post(SCENARIO_URI + "/" + SCENARIO_ID + "/injects/simple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }
    }
  }
}
