package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * One entry per finding in a bulk archive/un-archive request. Successful and failed findings share
 * this same shape so the whole batch can be returned as a single list: {@code archivedAt} is
 * populated (or null, for un-archive) on success, {@code error} is populated on failure -
 * per-finding validation failures never fail the whole batch (mirrors {@link
 * FindingTriageBulkItemOutput}).
 */
@Builder
@Getter
public class FindingArchiveBulkItemOutput {

  @JsonProperty("finding_id")
  private String findingId;

  @JsonProperty("success")
  private boolean success;

  @JsonProperty("finding_archived_at")
  private Instant archivedAt;

  @JsonProperty("error")
  private String error;
}
