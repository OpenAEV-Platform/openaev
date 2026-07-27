package io.openaev.rest.asset.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for assets */
@Setter
@Getter
public class AssetBulkProcessingInput {

  /**
   * The search input, used to select the assets to process (select all). Must be provided if
   * assetIdsToProcess is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of assets to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("asset_ids_to_process")
  private List<String> assetIdsToProcess;

  /** The list of assets to ignore from the search input */
  @JsonProperty("asset_ids_to_ignore")
  private List<String> assetIdsToIgnore;
}
