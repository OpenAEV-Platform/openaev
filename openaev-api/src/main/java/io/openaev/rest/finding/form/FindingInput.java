package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingInput {

  @JsonProperty("finding_field")
  @NotBlank
  private String field;

  @JsonProperty("finding_type")
  @NotNull
  protected ContractOutputType type;

  @JsonProperty("finding_value")
  @NotBlank
  protected String value;

  @JsonProperty("finding_labels")
  private String[] labels;

  // -- RELATION --

  @JsonProperty("finding_inject_id")
  private String injectId;

  // -- METHOD --

  public Finding toFinding(@NotNull Finding finding) {
    finding.setField(this.getField());
    finding.setType(this.getType());
    finding.setValue(this.getValue());
    finding.setLabels(this.getLabels());
    return finding;
  }
}
