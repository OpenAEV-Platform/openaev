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
 * A draft of a payload version: not published, never usable. Everyone who can update the action
 * sees and changes it; saving it changes no version number. Publishing it makes a new version from
 * its source version, numbered as any update, or, for a draft of a rejected version, sends that
 * same version (same number) for approval again.
 */
@Getter
@Setter
@Entity
@Table(name = "payload_drafts")
@EntityListeners(ModelBaseListener.class)
// payload_drafts is a tenant-v2 active table (inspector + can_access_tenant), like payloads, so no
// v1 @Filter; PayloadDraftService copies the tenant of the payload identity.
public class PayloadDraft implements TenantBase {

  @Id
  @ControlledUuidGeneration
  @Column(name = "payload_draft_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  /** The payload (all its versions) the draft belongs to. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_identity_id", updatable = false, nullable = false)
  @JsonIgnore
  private PayloadIdentity identity;

  /** The version the draft started from; null once that version is deleted. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_draft_source")
  @JsonIgnore
  private Payload source;

  /** The number of the version it started from, kept when that version is deleted. */
  @Column(name = "payload_draft_source_version", updatable = false)
  private String sourceVersion;

  /** Set when the draft reworks a rejected version: publishing it sends that version again. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_draft_rework", updatable = false)
  @JsonIgnore
  private Payload rework;

  /** What the editor shows, as the JSON of an action update (may be incomplete). */
  @Column(name = "payload_draft_content", columnDefinition = "text", nullable = false)
  @NotNull
  private String content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_draft_created_by")
  @JsonIgnore
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_draft_updated_by")
  @JsonIgnore
  private User updatedBy;

  // Name snapshot, so the draft stays readable after the user is deleted.
  @Column(name = "payload_draft_updated_by_name")
  private String updatedByName;

  @Column(name = "payload_draft_created_at", nullable = false, updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "payload_draft_updated_at", nullable = false)
  @NotNull
  private Instant updatedAt = now();
}
