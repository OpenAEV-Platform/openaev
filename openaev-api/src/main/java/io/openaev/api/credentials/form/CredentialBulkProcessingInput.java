package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Input of a bulk processing (e.g. delete) call for credentials. */
@Setter
@Getter
public class CredentialBulkProcessingInput {

  /**
   * The search input, used to select the credentials to process (select all). Must be provided if
   * credentialIdsToProcess is not provided.
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of credentials to process. Must be provided if searchPaginationInput is not provided.
   */
  @JsonProperty("credential_ids_to_process")
  private List<String> credentialIdsToProcess;

  /** The list of credentials to ignore from the search input (select all with exclusions). */
  @JsonProperty("credential_ids_to_ignore")
  private List<String> credentialIdsToIgnore;
}
