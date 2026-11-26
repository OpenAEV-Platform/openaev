package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for simulations */
@Setter
@Getter
public class ExerciseBulkProcessingInput {

  /**
   * The search input, used to select the simulations to process. Must be provided if
   * exerciseIdsToProcess is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of simulations to process. Must be provided if searchPaginationInput is not provided
   */
  @JsonProperty("exercise_ids_to_process")
  private List<String> exerciseIdsToProcess;

  /** The list of simulations to ignore from the search input */
  @JsonProperty("exercise_ids_to_ignore")
  private List<String> exerciseIdsToIgnore;
}
