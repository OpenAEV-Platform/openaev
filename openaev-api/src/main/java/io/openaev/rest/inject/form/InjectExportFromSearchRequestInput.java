package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Represent the input of an export request from a search */
@Setter
@Getter
public class InjectExportFromSearchRequestInput extends InjectBulkProcessingInput {
  /** The export options to alter the shape of the response */
  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();
}
