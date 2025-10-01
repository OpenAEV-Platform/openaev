package io.openaev.rest.asset_group;

import static io.openaev.database.model.Filters.FilterOperator.contains;
import static io.openaev.database.model.Filters.FilterOperator.not_contains;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.utils.fixtures.AssetGroupFixture;
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
public class AssetGroupApiSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private AssetGroupRepository assetGroupRepository;

  private static final List<String> ASSET_GROUP_IDS = new ArrayList<>();

  @BeforeAll
  void beforeAll() {
    AssetGroup assetGroup1 = AssetGroupFixture.createDefaultAssetGroup("Asset Group 1");
    AssetGroup assetGroup1Saved = this.assetGroupRepository.save(assetGroup1);
    ASSET_GROUP_IDS.add(assetGroup1Saved.getId());

    AssetGroup assetGroup2 = AssetGroupFixture.createDefaultAssetGroup("Asset Group 2");
    AssetGroup assetGroup2Saved = this.assetGroupRepository.save(assetGroup2);
    ASSET_GROUP_IDS.add(assetGroup2Saved.getId());
  }

  @AfterAll
  void afterAll() {
    this.assetGroupRepository.deleteAllById(ASSET_GROUP_IDS);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Retrieving asset groups")
  class RetrievingAssetGroups {
    // -- PREPARE --

    @Nested
    @DisplayName("Searching page of asset groups")
    class SearchingPageOfAssetGroups {

      @Test
      @DisplayName("Retrieving first page of asset groups by text search")
      void given_working_search_input_should_return_a_page_of_asset_groups() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("Asset Group").build();

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }

      @Test
      @DisplayName("Not retrieving first page of asset groups by text search")
      void given_not_working_search_input_should_return_a_page_of_asset_groups() throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault().textSearch("wrong").build();

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(0));
      }
    }

    @Nested
    @DisplayName("Sorting page of asset groups")
    class SortingPageOfAssetGroups {

      @Test
      @DisplayName("Sorting page of asset groups by name asc")
      void given_sorting_input_by_name_should_return_a_page_of_asset_groups_sort_by_name_asc()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(List.of(SortField.builder().property("asset_group_name").build()))
                .build();

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].asset_group_name").value("Asset Group 1"))
            .andExpect(jsonPath("$.content.[1].asset_group_name").value("Asset Group 2"));
      }

      @Test
      @DisplayName("Sorting page of asset groups by name desc")
      void given_sorting_input_by_name_should_return_a_page_of_asset_groups_sort_by_name_desc()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.getDefault()
                .sorts(
                    List.of(
                        SortField.builder().property("asset_group_name").direction("desc").build()))
                .build();

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.content.[0].asset_group_name").value("Asset Group 2"))
            .andExpect(jsonPath("$.content.[1].asset_group_name").value("Asset Group 1"));
      }
    }

    @Nested
    @DisplayName("Filtering page of asset groups")
    class FilteringPageOfAssetGroups {

      @Test
      @DisplayName("Filtering page of asset groups by name")
      void given_filter_input_by_name_should_return_a_page_of_asset_groups_filter_by_name()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator("asset_group_name", "Group", contains);

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }

      @Test
      @DisplayName("Filtering page of asset groups by description")
      void given_filter_input_by_sttaus_should_return_a_page_of_asset_groups_filter_by_description()
          throws Exception {
        SearchPaginationInput searchPaginationInput =
            PaginationFixture.simpleSearchWithAndOperator(
                "asset_group_description", valueOf("wrong"), not_contains);

        mvc.perform(
                post(ASSET_GROUP_URI + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(searchPaginationInput)))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.numberOfElements").value(2));
      }
    }
  }
}
