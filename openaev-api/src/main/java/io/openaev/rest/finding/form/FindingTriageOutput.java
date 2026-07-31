package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/** Read-facing view of a {@link FindingTriage} - the finding's CURRENT status, not history. */
@Builder
@Getter
public class FindingTriageOutput {

  @JsonProperty("finding_triage_id")
  private String id;

  @JsonProperty("finding_triage_finding_id")
  private String findingId;

  @JsonProperty("finding_triage_status")
  private FindingTriageStatus status;

  @JsonProperty("finding_triage_created_at")
  private Instant creationDate;

  @JsonProperty("finding_triage_updated_at")
  private Instant updateDate;

  public static FindingTriageOutput from(FindingTriage triage) {
    return FindingTriageOutput.builder()
        .id(triage.getId())
        .findingId(triage.getFinding().getId())
        .status(triage.getStatus())
        .creationDate(triage.getCreationDate())
        .updateDate(triage.getUpdateDate())
        .build();
  }
}
