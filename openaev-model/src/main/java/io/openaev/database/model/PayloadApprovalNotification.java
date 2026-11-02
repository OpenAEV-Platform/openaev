package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An approval change to notify (outbox): written in the same transaction as the change, then sent
 * and marked dispatched by the payload approval notification job (batched every minute).
 *
 * <p>Pending events go to the tenant's approvers (except the actor); outcome events go to their
 * {@link #recipient}, the author of the payload or version.
 */
@Getter
@Setter
@Entity
@Table(name = "payload_approval_notifications")
// payload_approval_notifications is a tenant-v2 active table (inspector + can_access_tenant), like
// payloads, so no v1 @Filter.
public class PayloadApprovalNotification implements TenantBase {

  public enum EVENT {
    /** A payload (never approved) became pending: approvers are notified. */
    PAYLOAD_PENDING,
    /** A new version of an approved payload is pending: approvers are notified. */
    VERSION_PENDING,
    /** The author's payload or version was approved. */
    APPROVED,
    /** The author's payload or version was rejected, with a reason. */
    REJECTED,
    /** The author's pending version was replaced by a newer one. */
    SUPERSEDED;

    /** Whether the event goes to the approvers (otherwise to its recipient). */
    public boolean forApprovers() {
      return this == PAYLOAD_PENDING || this == VERSION_PENDING;
    }
  }

  @Id
  @ControlledUuidGeneration
  @Column(name = "payload_approval_notification_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_id", updatable = false, nullable = false)
  @JsonIgnore
  private Payload payload;

  @Column(name = "payload_approval_notification_event", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private EVENT event;

  // Snapshots, so the message stays right if the payload is renamed or a user deleted meanwhile.
  @Column(name = "payload_approval_notification_payload_name")
  private String payloadName;

  @Column(name = "payload_approval_notification_version_number")
  private Integer versionNumber;

  /** Who caused the event: the author for a pending event, the decider for an outcome. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_approval_notification_actor")
  @JsonIgnore
  private User actor;

  @Column(name = "payload_approval_notification_actor_name")
  private String actorName;

  /** The author told about an outcome; null for the approvers' pending events. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_approval_notification_recipient")
  @JsonIgnore
  private User recipient;

  /** The reason of a rejection. */
  @Column(name = "payload_approval_notification_comment")
  private String comment;

  @Column(name = "payload_approval_notification_created_at", nullable = false, updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "payload_approval_notification_dispatched_at")
  private Instant dispatchedAt;

  @Override
  public boolean isListened() {
    return false;
  }
}
