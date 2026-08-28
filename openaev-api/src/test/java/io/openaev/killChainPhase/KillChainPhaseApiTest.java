package io.openaev.killChainPhase;

import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.database.model.Filters.FilterOperator.eq;
import static io.openaev.database.specification.KillChainPhaseSpecification.byNameOrKillChainName;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.KillChainPhaseFixture.getKillChainPhase;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.database.specification.KillChainPhaseSpecification;
import io.openaev.rest.kill_chain_phase.KillChainPhaseApi;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpsertInput;
import io.openaev.rest.kill_chain_phase.service.KillChainPhaseService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
  private static final Specification<KillChainPhase> spec = byNameOrKillChainName(SEARCH_INPUT);

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

    when(mockKillChainPhaseRepository.findAll(
            spec, Sort.by(Sort.Order.asc("killChainName"), Sort.Order.asc("order"))))
        .thenReturn(killChainPhaseList);
  }

  @AfterAll
  public void afterAll() {
    this.killChainPhaseRepository.deleteAllById(
        List.of(KILL_CHAIN_PHASE_ID_1, KILL_CHAIN_PHASE_ID_2, KILL_CHAIN_PHASE_ID_3));
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Fetching a page of kill chain phases")
  class FetchingPageOfKillChainPhases {

    @Test
    @DisplayName("Fetching first page of kill chain phases succeed")
    void given_search_input_should_return_a_page_of_kill_chain_phases() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(PaginationFixture.getDefault().size(3).build()))
                  .with(csrf()))
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(0));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Filtering page of kill chain phases")
  class FilteringPageOfKillChainPhases {

    @DisplayName("Fetching first page of kill chain phases by equals name")
    @Test
    void
        given_search_input_with_name_and_equals_operator_should_return_a_page_of_kill_chain_phases()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.simpleSearchWithAndOperator("phase_name", "NAME2", eq);

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }

    @DisplayName("Fetching first page of kill chain phases by contains name")
    @Test
    void
        given_search_input_with_name_and_contains_operator_should_return_a_page_of_kill_chain_phases()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.simpleSearchWithAndOperator("phase_name", "2", contains);

      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Sorting page of kill chain phases")
  class SortingPageOfKillCHainPhases {

    @DisplayName("Sorting by default")
    @Test
    void
        given_search_input_without_sort_should_return_a_page_of_kill_chain_phases_with_default_sort()
            throws Exception {
      SearchPaginationInput searchPaginationInput =
          PaginationFixture.getDefault().textSearch("name").build();

      // No sort is requested and SortUtilsJpa applies none, so the row order is
      // whatever Postgres returns; only membership can be asserted here.
      mvc.perform(
              post("/api/kill_chain_phases/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(3))
          .andExpect(
              jsonPath("$.content[*].phase_name")
                  .value(containsInAnyOrder("name1", "name2", "name3")));
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content.[0].phase_name").value("name3"))
          .andExpect(jsonPath("$.content.[1].phase_name").value("name2"))
          .andExpect(jsonPath("$.content.[2].phase_name").value("name1"));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Upserting kill chain phases")
  class UpsertingKillChainPhases {

    private static final String KILL_CHAIN = "upsert-test-chain";
    private static final String STIX_ID = "x-mitre-tactic--upsert-test-0001";

    @AfterEach
    void cleanUp() {
      for (String shortName : List.of("old-short", "new-short", "exec")) {
        killChainPhaseRepository
            .findByKillChainNameAndShortName(KILL_CHAIN, shortName)
            .ifPresent(killChainPhaseRepository::delete);
      }
    }

    private KillChainPhaseCreateInput createInput(String shortName, String name, String stixId) {
      KillChainPhaseCreateInput input = new KillChainPhaseCreateInput();
      input.setKillChainName(KILL_CHAIN);
      input.setShortName(shortName);
      input.setName(name);
      input.setStixId(stixId);
      input.setExternalId("TA-UPSERT-TEST");
      return input;
    }

    private KillChainPhaseUpsertInput upsertInput(KillChainPhaseCreateInput... inputs) {
      KillChainPhaseUpsertInput upsertInput = new KillChainPhaseUpsertInput();
      upsertInput.setKillChainPhases(List.of(inputs));
      return upsertInput;
    }

    @Test
    @DisplayName("Upsert matches an existing phase by STIX id even when the short name changed")
    void given_existing_stix_id_should_update_phase_instead_of_inserting() throws Exception {
      KillChainPhase existing = getKillChainPhase("Old name", 1L);
      existing.setKillChainName(KILL_CHAIN);
      existing.setShortName("old-short");
      existing.setStixId(STIX_ID);
      String existingId = killChainPhaseRepository.save(existing).getId();

      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(upsertInput(createInput("new-short", "New name", STIX_ID))))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.length()").value(1))
          .andExpect(jsonPath("$[0].phase_id").value(existingId))
          .andExpect(jsonPath("$[0].phase_shortname").value("new-short"))
          .andExpect(jsonPath("$[0].phase_name").value("New name"));

      assertTrue(
          killChainPhaseRepository
              .findByKillChainNameAndShortName(KILL_CHAIN, "old-short")
              .isEmpty(),
          "the old natural key must not survive as a separate row");
      assertEquals(
          existingId,
          killChainPhaseRepository
              .findByKillChainNameAndShortName(KILL_CHAIN, "new-short")
              .orElseThrow()
              .getId());
    }

    @Test
    @DisplayName("Upsert rejects entries with blank mandatory fields (cascaded validation)")
    void given_blank_mandatory_fields_should_return_bad_request() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(upsertInput(createInput("", "No short name", null))))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upsert rejects an explicit null kill_chain_phases list")
    void given_null_phase_list_should_return_bad_request() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"kill_chain_phases\": null}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upsert rejects null entries inside the kill_chain_phases list")
    void given_null_phase_entry_should_return_bad_request() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"kill_chain_phases\": [null]}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upsert rejects entries without an external id")
    void given_missing_external_id_should_return_bad_request() throws Exception {
      KillChainPhaseCreateInput input = createInput("exec", "Execution", STIX_ID);
      input.setExternalId(null);

      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(upsertInput(input)))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Duplicate entries in one request (with and without STIX id) persist a single row")
    void given_duplicate_inputs_in_batch_should_persist_single_row() throws Exception {
      mvc.perform(
              post("/api/kill_chain_phases/upsert")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          upsertInput(
                              createInput("exec", "Execution", STIX_ID),
                              createInput("exec", "Execution updated", null))))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.length()").value(1));

      KillChainPhase persisted =
          killChainPhaseRepository
              .findByKillChainNameAndShortName(KILL_CHAIN, "exec")
              .orElseThrow();
      assertEquals("Execution updated", persisted.getName());
      assertEquals(STIX_ID, persisted.getStixId(), "STIX id must survive the stix-less duplicate");
    }

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
      return new DataIntegrityViolationException(
          "duplicate key",
          new org.hibernate.exception.ConstraintViolationException(
              "duplicate key", new java.sql.SQLException("duplicate key"), constraintName));
    }

    @Test
    @DisplayName("Upsert retries once when the first attempt loses a concurrent-insert race")
    void given_concurrent_insert_race_should_retry_once() {
      KillChainPhaseService service = mock(KillChainPhaseService.class);
      KillChainPhaseApi api = new KillChainPhaseApi(mock(KillChainPhaseRepository.class), service);
      KillChainPhaseUpsertInput input = new KillChainPhaseUpsertInput();
      List<KillChainPhase> winner = List.of(new KillChainPhase());
      when(service.upsertKillChainPhases(input.getKillChainPhases()))
          .thenThrow(uniqueViolation("kill_chain_phases_stix_id_tenant_unique"))
          .thenReturn(winner);

      Iterable<KillChainPhase> result = api.upsertKillChainPhases(input);

      assertSame(winner, result);
      verify(service, times(2)).upsertKillChainPhases(input.getKillChainPhases());
    }

    @Test
    @DisplayName("Upsert does not retry integrity failures unrelated to the unique constraints")
    void given_unrelated_integrity_violation_should_not_retry() {
      KillChainPhaseService service = mock(KillChainPhaseService.class);
      KillChainPhaseApi api = new KillChainPhaseApi(mock(KillChainPhaseRepository.class), service);
      KillChainPhaseUpsertInput input = new KillChainPhaseUpsertInput();
      DataIntegrityViolationException notNullViolation =
          new DataIntegrityViolationException("null value in column phase_external_id");
      when(service.upsertKillChainPhases(input.getKillChainPhases())).thenThrow(notNullViolation);

      DataIntegrityViolationException thrown =
          assertThrows(
              DataIntegrityViolationException.class, () -> api.upsertKillChainPhases(input));

      assertSame(notNullViolation, thrown);
      verify(service, times(1)).upsertKillChainPhases(input.getKillChainPhases());
    }
  }

  @DisplayName("Test optionsByName")
  @Test
  void optionsByNameTest() throws Exception {

    try (MockedStatic<KillChainPhaseSpecification> mocked =
        Mockito.mockStatic(KillChainPhaseSpecification.class)) {
      when(KillChainPhaseSpecification.byNameOrKillChainName(SEARCH_INPUT)).thenReturn(spec);
      List<FilterUtilsJpa.Option> result = killChainPhaseApi.optionsByName(SEARCH_INPUT);

      // Multi kill chain platform: options are sorted by kill chain then phase order, and
      // labelled "[kill chain] phase" (see KillChainPhaseApi#toOption)
      verify(mockKillChainPhaseRepository)
          .findAll(spec, Sort.by(Sort.Order.asc("killChainName"), Sort.Order.asc("order")));
      assertEquals(
          killChainPhaseList.stream()
              .map(
                  i ->
                      new FilterUtilsJpa.Option(
                          i.getId(), "[" + i.getKillChainName() + "] " + i.getName()))
              .toList(),
          result);
    }
  }
}
