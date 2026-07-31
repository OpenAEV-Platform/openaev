package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingTriageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingTriageInput {

  @JsonProperty("status")
  @NotNull
  private FindingTriageStatus status;

  // Min 10 per product spec; max 4000 reuses the baseline established for finding_comment_content
  // (V6_20260730140000000__Add_finding_comments). Enforced again at the DB level, see
  // V6_20260730150000000__Add_finding_triage.
  @JsonProperty("justification")
  @NotBlank
  @Size(min = 10, max = 4000)
  private String justification;
}
