package io.openbas.killChainPhase;

import static io.openbas.database.model.Filters.FilterOperator.contains;
import static io.openbas.database.model.Filters.FilterOperator.eq;
import static io.openbas.database.specification.KillChainPhaseSpecification.byName;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.KillChainPhaseFixture.getKillChainPhase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.database.model.KillChainPhase;
import io.openbas.database.repository.KillChainPhaseRepository;
import io.openbas.database.specification.KillChainPhaseSpecification;
import io.openbas.rest.kill_chain_phase.KillChainPhaseApi;
import io.openbas.utils.FilterUtilsJpa;
import io.openbas.utils.fixtures.PaginationFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import io.openbas.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
public class KillChainPhaseApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private KillChainPhaseRepository killChainPhaseRepository;

  @Mock private KillChainPhaseRepository mockKillChainPhaseRepository;

  @InjectMocks private KillChainPhaseApi killChainPhaseApi;

  private static final KillChainPhase KILL_CHAIN_PHASE_1 = getKillChainPhase("name1", 1L);
  private static final KillChainPhase KILL_CHAIN_PHASE_2 = getKillChainPhase("name2", 2L);
  private static final KillChainPhase KILL_CHAIN_PHASE_3 = getKillChainPhase("name3", 3L);

  private static final String SEARCH_INPUT = "search input";
  private static final Specification<KillChainPhase> spec = byName(SEARCH_INPUT);

  private static String KILL_CHAIN_PHASE_ID_1;
  private static String KILL_CHAIN_PHASE_ID_2;
  private static String KILL_CHAIN_PHASE_ID_3;

  private static List<KillChainPhase> killChainPhaseList = new ArrayList<>();

  @BeforeAll
  public void beforeAll() {

    KILL_CHAIN_PHASE_ID_1 = this.killChainPhaseRepository.save(KILL_CHAIN_PHASE_1).getId();
    KILL_CHAIN_PHASE_ID_2 = this.killChainPhaseRepository.save(KILL_CHAIN_PHASE_2).getId();
    KILL_CHAIN_PHASE_ID_3 = this.killChainPhaseRepository.save(KILL_CHAIN_PHASE_3).getId();

    killChainPhaseList = Arrays.asList(KILL_CHAIN_PHASE_1, KILL_CHAIN_PHASE_2, KILL_CHAIN_PHASE_3);

    when(mockKillChainPhaseRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "order")))
        .thenReturn(killChainPhaseList);
  }

  @AfterAll
  public void afterAll() {
    this.killChainPhaseRepository.deleteAllById(
        List.of(KILL_CHAIN_PHASE_ID_1, KILL_CHAIN_PHASE_ID_2, KILL_CHAIN_PHASE_ID_3));
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Fetching a page of kill chain phases")
  class FetchingPageOfKillChainPhases {

    @Test
    @DisplayName("Fetching first page of kill chain phases succeed")
    void given_search_input_should_return_a_page_of_kill_chain_phases() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().size(3).build())))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(3));
    }

    @Test
    @DisplayName("Fetching first page of kill chain phases failed with bad request")
    void given_a_bad_search_input_should_throw_bad_request() throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().size(1110).build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Searching page of kill chain phases")
  class SearchingPageOfKillChainPhases {

    @DisplayName("Fetching first page of kill chain phases by textsearch")
    @Test
    void given_search_input_with_textsearch_should_return_a_page_of_kill_chain_phases()
        throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().textSearch("name2").build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @DisplayName("Fetching first page of kill chain phases by textsearch ignoring case")
    @Test
    void
        given_search_input_with_textsearch_should_return_a_page_of_kill_chain_phases_ignoring_case()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().textSearch("NAME2").build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @DisplayName("Fetching first page of kill chain phases by textsearch with spaces")
    @Test
    void given_search_input_with_textsearch_with_spaces_should_return_a_page_of_kill_chain_phases()
        throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().textSearch("name 2").build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(0));
    }
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Filtering page of kill chain phases")
  class FilteringPageOfKillChainPhases {

    @DisplayName("Fetching first page of kill chain phases by equals name")
    @Test
    void
        given_search_input_with_name_and_equals_operator_should_return_a_page_of_kill_chain_phases()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.simpleFilter("phase_name", "NAME2", eq);

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @DisplayName("Fetching first page of kill chain phases by contains name")
    @Test
    void
        given_search_input_with_name_and_contains_operator_should_return_a_page_of_kill_chain_phases()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.simpleFilter("phase_name", "2", contains);

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("Sorting page of kill chain phases")
  class SortingPageOfKillCHainPhases {

    @DisplayName("Sorting by default")
    @Test
    void
        given_search_input_without_sort_should_return_a_page_of_kill_chain_phases_with_default_sort()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().textSearch("name").build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.[0].phase_name").value("name1"))
          .andExpect(jsonPath("$.content.[1].phase_name").value("name2"))
          .andExpect(jsonPath("$.content.[2].phase_name").value("name3"));
    }

    @DisplayName("Sorting by name desc")
    @Test
    void given_sort_input_should_return_a_page_of_kill_chain_phases_sort_by_name_desc()
        throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault()
              .textSearch("name")
              .sorts(List.of(SortField.builder().property("phase_name").direction("desc").build()))
              .build();

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.[0].phase_name").value("name3"))
          .andExpect(jsonPath("$.content.[1].phase_name").value("name2"))
          .andExpect(jsonPath("$.content.[2].phase_name").value("name1"));
    }
  }

  @DisplayName("Test optionsByName")
  @Test
  void optionsByNameTest() throws Exception {

    try (MockedStatic<KillChainPhaseSpecification> mocked =
        Mockito.mockStatic(KillChainPhaseSpecification.class)) {
      when(KillChainPhaseSpecification.byName(SEARCH_INPUT)).thenReturn(spec);
      List<FilterUtilsJpa.Option> result = killChainPhaseApi.optionsByName(SEARCH_INPUT);

      verify(mockKillChainPhaseRepository).findAll(spec, Sort.by(Sort.Direction.ASC, "order"));
      assertEquals(
          killChainPhaseList.stream()
              .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
              .toList(),
          result);
    }
  }
}
