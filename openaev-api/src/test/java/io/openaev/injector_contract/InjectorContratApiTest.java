package io.openaev.injector_contract;

import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.database.model.Filters.FilterOperator.eq;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.channel.ChannelInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class InjectorContratApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private ChallengeInjectorIntegrationFactory challengeInjectorIntegrationFactory;
  @Autowired private ChannelInjectorIntegrationFactory channelInjectorIntegrationFactory;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  @BeforeEach
  public void before() throws Exception {
    new Manager(
            List.of(
                emailInjectorIntegrationFactory,
                challengeInjectorIntegrationFactory,
                channelInjectorIntegrationFactory,
                manualInjectorIntegrationFactory))
        .monitorIntegrations();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Fetching contracts")
  class FetchingContracts {

    @Nested
    @DisplayName("Fetching a page of contracts")
    class FetchingPageOfContracts {

      @Test
      @DisplayName("Fetching first page of contracts succeed")
      void given_search_input_should_return_a_page_of_contrats() throws Exception {
        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().build())))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(5));
      }

      @Test
      @DisplayName("Fetching first page of contracts failed with bad request")
      void given_a_bad_search_input_should_throw_bad_request() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().size(1110).build();

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().isBadRequest());
      }
    }

    @Nested
    @DisplayName("Searching page of contracts")
    class SearchingPageOfContracts {

      @DisplayName("Fetching first page of contracts by textsearch ignoring case")
      @Test
      void given_search_input_with_textsearch_should_return_a_page_of_contrats_ignoring_case()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("PubLish Chal").build();

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @DisplayName("Fetching first page of contracts by textsearch with spaces")
      @Test
      void given_search_input_with_textsearch_with_spaces_should_return_a_page_of_contracts()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Pu bLish Ch al").build();

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Filtering page of contracts")
    class FilteringPageOfContracts {

      @DisplayName(
          "Fetching first page of contracts by label type ignoring case and contains operator")
      @Test
      void given_search_input_with_label_type_should_return_a_page_of_contrats_ignoring_case()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "injector_contract_labels", "multi-recipients", contains);

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @DisplayName("Fetching first page of contracts by label and equals operator")
      @Test
      void given_search_input_with_label_should_return_a_page_of_contrats() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "injector_contract_labels", "Send multi-recipients mail", eq);

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }

      @DisplayName("Fetching first page of contracts by label email ignoring case")
      @Test
      void given_search_input_with_label_should_return_a_page_of_contrats_ignoring_case()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "injector_contract_labels", "send multi-recipients mail", eq);

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(1));
      }
    }

    @Nested
    @DisplayName("Sorting page of contracts")
    class SortingPageOfContracts {

      @DisplayName("Sorting by label desc")
      @Test
      void given_sort_input_should_return_a_page_of_contrats_sort_by_label_desc() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .textSearch("mail")
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("injector_contract_labels")
                            .direction("desc")
                            .build()))
                .build();

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                jsonPath("$.content.[0].injector_contract_labels.en")
                    .value("Send multi-recipients mail"))
            .andExpect(
                jsonPath("$.content.[1].injector_contract_labels.en")
                    .value("Send individual mails"));
      }

      @DisplayName("Sorting by label asc")
      @Test
      void given_sort_input_should_return_a_page_of_contrats_sort_by_label_asc() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .textSearch("mail")
                .sorts(
                    List.of(
                        SortField.builder()
                            .property("injector_contract_labels")
                            .direction("asc")
                            .build()))
                .build();

        mvc.perform(
                post("/api/injector_contracts/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(
                jsonPath("$.content.[0].injector_contract_labels.en")
                    .value("Send individual mails"))
            .andExpect(
                jsonPath("$.content.[1].injector_contract_labels.en")
                    .value("Send multi-recipients mail"));
      }
    }
  }
}
