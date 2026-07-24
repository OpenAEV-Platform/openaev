package io.openaev.rest.user.form.player;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk processing (e.g. delete) call for players */
@Setter
@Getter
public class PlayerBulkProcessingInput {

  /**
   * The search input, used to select the players to process. Must be provided if userIdsToProcess
   * is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /** The list of players to process. Must be provided if searchPaginationInput is not provided */
  @JsonProperty("user_ids_to_process")
  private List<String> userIdsToProcess;

  /** The list of players to ignore from the search input */
  @JsonProperty("user_ids_to_ignore")
  private List<String> userIdsToIgnore;
}
