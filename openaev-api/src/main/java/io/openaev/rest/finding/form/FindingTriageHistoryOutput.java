package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.FindingTriageStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Read-facing view of a {@link FindingTriageHistory} row. Mirrors {@code FindingCommentOutput}'s
 * pattern: resolves the actor to a display name instead of exposing the raw user id / entity.
 * {@code actor} is nullable on the entity (null = System, see {@link FindingTriageHistory}'s
 * javadoc); {@code isSystem} makes that explicit for consumers instead of relying on both name
 * fields being null.
 */
@Builder
@Getter
public class FindingTriageHistoryOutput {

  @JsonProperty("finding_triage_history_id")
  private String id;

  @JsonProperty("finding_triage_history_finding_id")
  private String findingId;

  @JsonProperty("finding_triage_history_from_status")
  private FindingTriageStatus fromStatus;

  @JsonProperty("finding_triage_history_to_status")
  private FindingTriageStatus toStatus;

  @JsonProperty("finding_triage_history_justification")
  private String justification;

  @JsonProperty("finding_triage_history_actor_id")
  private String actorId;

  @JsonProperty("finding_triage_history_actor_firstname")
  private String actorFirstname;

  @JsonProperty("finding_triage_history_actor_lastname")
  private String actorLastname;

  @JsonProperty("finding_triage_history_is_system")
  private boolean isSystem;

  @JsonProperty("finding_triage_history_created_at")
  private Instant creationDate;

  public static FindingTriageHistoryOutput from(FindingTriageHistory history) {
    FindingTriageHistoryOutput.FindingTriageHistoryOutputBuilder builder =
        FindingTriageHistoryOutput.builder()
            .id(history.getId())
            .findingId(history.getFinding().getId())
            .fromStatus(history.getFromStatus())
            .toStatus(history.getToStatus())
            .justification(history.getJustification())
            .creationDate(history.getCreationDate());
    if (history.getActor() != null) {
      builder
          .actorId(history.getActor().getId())
          .actorFirstname(history.getActor().getFirstname())
          .actorLastname(history.getActor().getLastname())
          .isSystem(false);
    } else {
      builder.isSystem(true);
    }
    return builder.build();
  }
}
