package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (delete and tests) calls for injects */
@Setter
@Getter
public class InjectBulkProcessingInput {

  /**
   * The search input, used to select the injects to update. Must be provided if injectIDsToDelete
   * is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of injects to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("inject_ids_to_process")
  private List<String> injectIDsToProcess;

  /** The list of injects to ignore from the search input */
  @JsonProperty("inject_ids_to_ignore")
  private List<String> injectIDsToIgnore;

  /** The simulation or scenario ID to which the injects belong. */
  @JsonProperty("simulation_or_scenario_id")
  private String simulationOrScenarioId;
}
