package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContentHistory;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ContentHistoryOutput(
    @JsonProperty("content_history_id") @NotBlank String id,
    @JsonProperty("content_history_action") ContentHistory.ACTION action,
    @JsonProperty("content_history_version") String version,
    @JsonProperty("content_history_from_version") String fromVersion,
    @JsonProperty("content_history_user") String userName,
    @JsonProperty("content_history_created_at") Instant createdAt) {

  public static ContentHistoryOutput of(ContentHistory history) {
    return new ContentHistoryOutput(
        history.getId(),
        history.getAction(),
        history.getVersion(),
        history.getFromVersion(),
        history.getUser() != null ? history.getUser().getName() : null,
        history.getCreatedAt());
  }
}
