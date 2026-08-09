package io.openaev.injectors.phishing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Input of a bulk processing (e.g. delete) call for phishing email templates. */
@Setter
@Getter
public class PhishingEmailTemplateBulkProcessingInput {

  /**
   * The search input, used to select the email templates to process (select all). Must be provided
   * if emailTemplateIdsToProcess is not provided.
   */
  @JsonProperty("search_pagination_input")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of email templates to process. Must be provided if searchPaginationInput is not
   * provided.
   */
  @JsonProperty("email_template_ids_to_process")
  private List<String> emailTemplateIdsToProcess;

  /** The list of email templates to ignore from the search input (select all with exclusions). */
  @JsonProperty("email_template_ids_to_ignore")
  private List<String> emailTemplateIdsToIgnore;
}
