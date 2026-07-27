package io.openaev.rest.asset_group.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for asset groups */
@Setter
@Getter
public class AssetGroupBulkProcessingInput {

  /**
   * The search input, used to select the asset groups to process (select all). Must be provided if
   * assetGroupIdsToProcess is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of asset groups to process. Must be provided if searchPaginationInput is not provided
   */
  @JsonProperty("asset_group_ids_to_process")
  private List<String> assetGroupIdsToProcess;

  /** The list of asset groups to ignore from the search input */
  @JsonProperty("asset_group_ids_to_ignore")
  private List<String> assetGroupIdsToIgnore;
}
