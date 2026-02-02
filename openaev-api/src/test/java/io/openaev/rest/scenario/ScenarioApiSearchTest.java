package io.openaev.rest.scenario;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.database.model.Scenario.SEVERITY.critical;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.scenario.form.GetScenariosInput;
import io.openaev.utils.fixtures.*;
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
public class ScenarioApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private GrantRepository grantRepository;
  @Autowired private UserRepository userRepository;

  private static final List<String> SCENARIO_IDS = new ArrayList<>();

  @BeforeAll
  void beforeAll() {
    Scenario scenario1 = ScenarioFixture.createDefaultCrisisScenario();
    Scenario scenario1Saved = this.scenarioRepository.save(scenario1);
    SCENARIO_IDS.add(scenario1Saved.getId());

    Scenario scenario2 = ScenarioFixture.createDefaultIncidentResponseScenario();
    Scenario scenario2Saved = this.scenarioRepository.save(scenario2);
    SCENARIO_IDS.add(scenario2Saved.getId());
  }

  @AfterAll
  void afterAll() {
    this.scenarioRepository.deleteAllById(SCENARIO_IDS);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving scenarios")
  class RetrievingScenarios {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of scenarios")
    class SearchingPageOfScenarios {

      @Test
      @DisplayName("Retrieving first page of scenarios by textsearch")
      void given_working_search_input_should_return_a_page_of_scenarios() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Crisis").build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Not retrieving first page of scenario by textsearch")
      void given_not_working_search_input_should_return_a_page_of_scenarios() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of scenarios")
    class SortingPageOfScenarios {

      @Test
      @DisplayName("Sorting page of scenarios by name")
      void given_sorting_input_by_name_should_return_a_page_of_scenarios_sort_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("scenario_name").build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].scenario_name").value("Crisis scenario"))
            .andExpect(jsonPath("$.content.[1].scenario_name").value("Incident response scenario"));
      }

      @Test
      @DisplayName("Sorting page of scenarios by category")
      void given_sorting_input_by_category_should_return_a_page_of_scenarios_sort_by_category()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("scenario_category")
                            .direction("desc")
                            .build()))
                .build();

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].scenario_name").value("Incident response scenario"))
            .andExpect(jsonPath("$.content.[1].scenario_name").value("Crisis scenario"));
      }
    }

    @Nested
    @DisplayName("Filtering page of scenarios")
    class FilteringPageOfScenarios {

      @Test
      @DisplayName("Filtering page of scenarios by name")
      void given_filter_input_by_name_should_return_a_page_of_scenarios_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("scenario_name", "Crisis", contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of scenarios by category")
      void given_filter_input_by_category_should_return_a_page_of_scenarios_filter_by_category()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "scenario_category", Scenario.MAIN_FOCUS_INCIDENT_RESPONSE, contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Filtering page of scenarios by severity")
      void given_filter_input_by_severity_should_return_a_page_of_scenarios_filter_by_severity()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "scenario_severity", valueOf(critical), contains);

        mvc.perform(
                post(SCENARIO_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @Test
      @DisplayName("Search scenarios by ids as admin")
      void given_list_of_ids_select_scenarios() throws Exception {
        GetScenariosInput getScenariosInput = new GetScenariosInput();
        getScenariosInput.setScenarioIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getScenariosInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search scenarios by ids as granted user")
      @WithMockUser(isAdmin = false, withCapabilities = Capability.ACCESS_ASSESSMENT)
      void given_list_of_ids_select_scenarios_with_capabilities() throws Exception {
        GetScenariosInput getScenariosInput = new GetScenariosInput();
        getScenariosInput.setScenarioIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getScenariosInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
      }

      @Test
      @DisplayName("Search scenarios by ids as user not granted")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_scenarios_without_capabilities_no_grants() throws Exception {
        GetScenariosInput getScenariosInput = new GetScenariosInput();
        getScenariosInput.setScenarioIds(SCENARIO_IDS);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getScenariosInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
      }

      @Test
      @DisplayName("Search scenarios by ids as user granted on some scenario")
      @WithMockUser(isAdmin = false)
      void given_list_of_ids_select_scenarios_without_capabilities_with_grants() throws Exception {
        Scenario scenarioGranted = ScenarioFixture.createDefaultIncidentResponseScenario();
        User user = userRepository.findById(currentUser().getId()).get();
        Group group = new Group();
        group.setName("test");
        group = groupRepository.save(group);
        scenarioGranted.getGrants().addAll(group.getGrants());
        scenarioGranted = scenarioRepository.save(scenarioGranted);

        Grant grantObserver = new Grant();
        grantObserver.setResourceId(scenarioGranted.getId());
        grantObserver.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
        grantObserver.setGroup(group);
        grantObserver.setName(Grant.GRANT_TYPE.OBSERVER);
        Grant grantPlanner = new Grant();
        grantPlanner.setResourceId(scenarioGranted.getId());
        grantPlanner.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
        grantPlanner.setGroup(group);
        grantPlanner.setName(Grant.GRANT_TYPE.PLANNER);
        grantRepository.saveAll(List.of(grantObserver, grantPlanner));
        group.setGrants(List.of(grantObserver, grantPlanner));
        group.setUsers(List.of(user));
        groupRepository.save(group);
        SCENARIO_IDS.add(scenarioGranted.getId());

        GetScenariosInput getScenariosInput = new GetScenariosInput();
        List<String> scenarioIds = new ArrayList<>();
        scenarioIds.add(scenarioGranted.getId());
        scenarioIds.addAll(SCENARIO_IDS);
        getScenariosInput.setScenarioIds(scenarioIds);

        mvc.perform(
                post(SCENARIO_URI + "/search-by-id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(getScenariosInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1));

        scenarioRepository.delete(scenarioGranted);
      }
    }
  }
}
