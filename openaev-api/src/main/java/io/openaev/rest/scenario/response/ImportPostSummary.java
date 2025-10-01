package io.openaev.rest.scenario.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ImportPostSummary {

  @JsonProperty("import_id")
  @NotBlank
  private String importId;

  @JsonProperty("available_sheets")
  @NotNull
  private List<String> availableSheets;
}
