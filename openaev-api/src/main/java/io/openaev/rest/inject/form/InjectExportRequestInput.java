package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class InjectExportRequestInput {
  @JsonProperty("injects")
  private List<InjectExportTarget> injects;

  @JsonProperty("options")
  private ExportOptionsInput exportOptions = new ExportOptionsInput();

  @JsonIgnore
  public List<String> getTargetsIds() {
    if (injects == null) {
      return List.of();
    }
    return injects.stream().map(InjectExportTarget::getId).toList();
  }
}
