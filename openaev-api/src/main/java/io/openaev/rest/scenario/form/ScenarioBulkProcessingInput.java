package io.openaev.rest.scenario.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for scenarios */
@Setter
@Getter
public class ScenarioBulkProcessingInput {

  /**
   * The search input, used to select the scenarios to process. Must be provided if
   * scenarioIdsToProcess is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of scenarios to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("scenario_ids_to_process")
  private List<String> scenarioIdsToProcess;

  /** The list of scenarios to ignore from the search input */
  @JsonProperty("scenario_ids_to_ignore")
  private List<String> scenarioIdsToIgnore;
}
