package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InjectIndividualExportRequestInput {
  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();
}
