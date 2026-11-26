package io.openaev.api.threat_arsenal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadApproval;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Optional;

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

  /**
   * A row read from the approval history of a version: a decision on it, with who took it. Entries
   * that only record a submission (a version waiting for approval after a write) are left out: the
   * write already has its own row.
   *
   * <ul>
   *   <li>explicit approve, reject, move back to pending: the approver, and the reason or comment;
   *   <li>automatic approval: its author, or no user with the source (platform, Filigran, or the
   *       upgrade that introduced payload approval);
   *   <li>superseded: the user whose newer version replaced it.
   * </ul>
   */
  public static Optional<ContentHistoryOutput> fromApproval(
      PayloadApproval entry, String version, ObjectMapper mapper) {
    ObjectNode details = mapper.createObjectNode();
    String user = actorName(entry);
    ContentHistory.ACTION action;
    switch (entry.getOrigin()) {
      case APPROVE -> {
        action = ContentHistory.ACTION.APPROVE;
        putIfPresent(details, "comment", entry.getComment());
      }
      case REJECT -> {
        action = ContentHistory.ACTION.REJECT;
        putIfPresent(details, "reason", entry.getComment());
      }
      case REOPEN -> {
        action = ContentHistory.ACTION.REOPEN;
        putIfPresent(details, "comment", entry.getComment());
      }
      case SUPERSEDE -> action = ContentHistory.ACTION.SUPERSEDE;
      default -> {
        if (entry.getStatus() != Payload.PAYLOAD_APPROVAL_STATUS.APPROVED) {
          return Optional.empty();
        }
        action = ContentHistory.ACTION.APPROVE;
        if (entry.getOrigin() == PayloadApproval.ORIGIN.CATALOG) {
          details.put("source", "catalog");
          user = null;
        } else if (entry.getOrigin() == PayloadApproval.ORIGIN.MIGRATION) {
          // Versions that existed before payload approval: approved by the upgrade, not by anyone.
          details.put("source", "upgrade");
          user = null;
        } else if (user == null) {
          details.put("source", "platform");
        }
      }
    }
    return Optional.of(
        new ContentHistoryOutput(
            entry.getId(),
            action,
            version,
            null,
            null,
            entry.isAutomatic(),
            details.isEmpty() ? null : details,
            user,
            entry.getCreatedAt()));
  }

  private static String actorName(PayloadApproval entry) {
    if (entry.getActorName() != null) {
      return entry.getActorName();
    }
    return entry.getActor() != null ? entry.getActor().getName() : null;
  }

  private static void putIfPresent(ObjectNode details, String key, String value) {
    if (value != null && !value.isBlank()) {
      details.put(key, value);
    }
  }
}
