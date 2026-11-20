package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of a bulk delete call for the injects of a simulation or a scenario */
@Setter
@Getter
public class InjectBulkDeleteInput extends InjectBulkProcessingInput {

  /**
   * True keeps the findings the injects produced, detached from them. False (the default) deletes
   * them with the injects.
   */
  @JsonProperty("keep_findings")
  private boolean keepFindings = false;
}
