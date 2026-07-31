package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingTriageStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * One entry per finding in a bulk triage request. Successful and failed findings share this same
 * shape so the whole batch can be returned as a single list: {@code status} is populated on
 * success, {@code error} is populated on failure - per-finding validation failures never fail the
 * whole batch (product spec).
 */
@Builder
@Getter
public class FindingTriageBulkItemOutput {

  @JsonProperty("finding_id")
  private String findingId;

  @JsonProperty("success")
  private boolean success;

  @JsonProperty("status")
  private FindingTriageStatus status;

  @JsonProperty("error")
  private String error;
}
