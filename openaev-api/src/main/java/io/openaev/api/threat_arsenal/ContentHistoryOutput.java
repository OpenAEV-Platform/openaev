package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContentHistory;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ContentHistoryOutput(
    @JsonProperty("content_history_id") @NotBlank String id,
    @JsonProperty("content_history_action") ContentHistory.ACTION action,
    @JsonProperty("content_history_version") String version,
    @JsonProperty("content_history_from_version") String fromVersion,
    @JsonProperty("content_history_count") Integer count,
    @JsonProperty("content_history_automatic") boolean automatic,
    @JsonProperty("content_history_details") JsonNode details,
    @JsonProperty("content_history_user") String userName,
    @JsonProperty("content_history_created_at") Instant createdAt) {

  public static ContentHistoryOutput of(ContentHistory history) {
    return of(history, history.getDetails());
  }

  // An automatic row was done by the platform, not by the user whose call triggered it.
  public static ContentHistoryOutput of(ContentHistory history, JsonNode details) {
    return new ContentHistoryOutput(
        history.getId(),
        history.getAction(),
        history.getVersion(),
        history.getFromVersion(),
        history.getCount(),
        history.isAutomatic(),
        details,
        history.getUser() != null && !history.isAutomatic() ? history.getUser().getName() : null,
        history.getCreatedAt());
  }

  /** A row read from a repository's revocation list rather than filed in the history. */
  public static ContentHistoryOutput fromRepository(
      String id, ContentHistory.ACTION action, String version, JsonNode details, Instant at) {
    return new ContentHistoryOutput(id, action, version, null, null, true, details, null, at);
  }
}
