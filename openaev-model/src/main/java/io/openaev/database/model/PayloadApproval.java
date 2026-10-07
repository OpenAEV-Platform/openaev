package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One entry of the approval history of a payload: every status change, by whom, how and why. Kept
 * for auditability, including the automatic approvals of authors who hold "Approve content".
 */
@Getter
@Setter
@Entity
@Table(name = "payload_approvals")
@EntityListeners(ModelBaseListener.class)
// payload_approvals is a tenant-v2 active table (inspector + can_access_tenant), like payloads, so
// no v1 @Filter.
public class PayloadApproval implements TenantBase {

  /** What produced the entry. */
  public enum ORIGIN {
    CREATE,
    UPDATE,
    DUPLICATE,
    IMPORT,
    COLLECTOR,
    SYSTEM,
    MIGRATION,
    APPROVE,
    REJECT
  }

  @Id
  @ControlledUuidGeneration
  @Column(name = "payload_approval_id")
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

  @Column(name = "payload_approval_status", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Payload.PAYLOAD_APPROVAL_STATUS status;

  @Column(name = "payload_approval_origin", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private ORIGIN origin;

  // True when the status was set by the rules (auto-approval of an approver's own write), false
  // when it is an explicit approve / reject decision.
  @Column(name = "payload_approval_automatic", nullable = false)
  private boolean automatic;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_approval_actor")
  @JsonIgnore
  private User actor;

  // Name snapshot, so the history stays readable after the user is deleted.
  @Column(name = "payload_approval_actor_name")
  private String actorName;

  @Column(name = "payload_approval_comment")
  private String comment;

  @Column(name = "payload_approval_fingerprint")
  private String fingerprint;

  @Column(name = "payload_approval_created_at", nullable = false, updatable = false)
  @NotNull
  private Instant createdAt = now();
}
