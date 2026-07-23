package io.openaev.rest.organization.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Represent the input of a bulk processing (e.g. delete) call for organizations. The organizations
 * list is client-side filtered, so the selection is always an explicit list of ids.
 */
@Setter
@Getter
public class OrganizationBulkProcessingInput {

  /** The list of organizations to process */
  @NotEmpty
  @JsonProperty("organization_ids")
  private List<String> organizationIds;
}
