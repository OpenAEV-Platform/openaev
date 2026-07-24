package io.openaev.rest.organization.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represent the input of a bulk processing (e.g. delete) call for organizations, mirroring the
 * teams/players bulk inputs: either an explicit list of ids, or a search input (select all) with
 * optional ids to ignore.
 */
@Setter
@Getter
public class OrganizationBulkProcessingInput {

  /**
   * The search input, used to select the organizations to process. Must be provided if
   * organizationIdsToProcess is not provided
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of organizations to process. Must be provided if searchPaginationInput is not provided
   */
  @JsonProperty("organization_ids_to_process")
  private List<String> organizationIdsToProcess;

  /** The list of organizations to ignore from the search input */
  @JsonProperty("organization_ids_to_ignore")
  private List<String> organizationIdsToIgnore;
}
