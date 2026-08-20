package io.openaev.injectors.phishing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Input of a bulk processing (e.g. delete) call for phishing landing pages. */
@Setter
@Getter
public class PhishingLandingPageBulkProcessingInput {

  /**
   * The search input, used to select the landing pages to process (select all). Must be provided if
   * landingPageIdsToProcess is not provided.
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of landing pages to process. Must be provided if searchPaginationInput is not
   * provided.
   */
  @JsonProperty("landing_page_ids_to_process")
  private List<String> landingPageIdsToProcess;

  /** The list of landing pages to ignore from the search input (select all with exclusions). */
  @JsonProperty("landing_page_ids_to_ignore")
  private List<String> landingPageIdsToIgnore;
}
