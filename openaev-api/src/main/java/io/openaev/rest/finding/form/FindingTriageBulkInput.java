package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingTriageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingTriageBulkInput {

  @JsonProperty("finding_ids")
  @NotEmpty
  private List<@NotBlank String> findingIds;

  @JsonProperty("status")
  @NotNull
  private FindingTriageStatus status;

  @JsonProperty("justification")
  @NotBlank
  @Size(min = 10, max = 4000)
  private String justification;
}
