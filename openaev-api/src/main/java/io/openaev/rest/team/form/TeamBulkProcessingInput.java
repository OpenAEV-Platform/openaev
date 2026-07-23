package io.openaev.rest.team.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for teams */
@Setter
@Getter
public class TeamBulkProcessingInput {

  /**
   * The search input, used to select the teams to process. Must be provided if teamIdsToProcess is
   * not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of teams to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("team_ids_to_process")
  private List<String> teamIdsToProcess;

  /** The list of teams to ignore from the search input */
  @JsonProperty("team_ids_to_ignore")
  private List<String> teamIdsToIgnore;
}
