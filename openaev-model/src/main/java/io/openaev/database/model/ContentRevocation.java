package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A revoked payload or scenario. From the revocation list published with the releases: no tenant,
 * keyed by external ID and version, revoked for every tenant. From a tenant admin (F34): keyed by
 * the payload, revoked in that tenant only. Single source of truth for the execution check.
 */
@Getter
@Setter
@Entity
@Table(name = "content_revocations")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class ContentRevocation implements DualScopeBase, Auditable {

  @Id
  @Column(name = "content_revocation_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("content_revocation_id")
  @NotBlank
  private String id;

  @Column(name = "content_revocation_type")
  @Enumerated(EnumType.STRING)
  @JsonProperty("content_revocation_type")
  @NotNull
  private ContentType type;

  // Repository whose revocation list issued it: an external ID is unique within one repository.
  @Column(name = "content_revocation_repository")
  @JsonProperty("content_revocation_repository")
  private String repository;

  // A lift of the revocation a variant version inherits from its base: names the base revocation.
  @Column(name = "content_revocation_lifts")
  @JsonProperty("content_revocation_lifts")
  private String liftsRevocationId;

  @Column(name = "content_revocation_external_id")
  @JsonProperty("content_revocation_external_id")
  private String externalId;

  @Column(name = "content_revocation_version")
  @JsonProperty("content_revocation_version")
  private String version;

  @Column(name = "content_revocation_reason")
  @JsonProperty("content_revocation_reason")
  private String reason;

  @Column(name = "content_revocation_list_serial")
  @JsonProperty("content_revocation_list_serial")
  private Long listSerial;

  @Column(name = "content_revocation_published_at")
  @JsonProperty("content_revocation_published_at")
  private Instant publishedAt;

  // -- TENANT REVOCATION --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_revocation_payload", updatable = false)
  @JsonProperty("content_revocation_payload")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Payload payload;

  // Restored on the payload when the tenant lifts its revocation.
  @Column(name = "content_revocation_payload_previous_status", updatable = false)
  @JsonIgnore
  @Enumerated(EnumType.STRING)
  private Payload.PAYLOAD_STATUS payloadPreviousStatus;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_revocation_revoked_by", updatable = false)
  @JsonProperty("content_revocation_revoked_by")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private User revokedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false)
  @JsonIgnore
  @Nullable
  private Tenant tenant;

  // Set when a newer list no longer contains this revocation, or the tenant lifts it.
  @Column(name = "content_revocation_lifted_at")
  @JsonProperty("content_revocation_lifted_at")
  private Instant liftedAt;

  @Column(name = "content_revocation_created_at", updatable = false)
  @JsonProperty("content_revocation_created_at")
  @NotNull
  private Instant createdAt;

  @Column(name = "content_revocation_updated_at")
  @JsonProperty("content_revocation_updated_at")
  @NotNull
  private Instant updatedAt;

  @Override
  public boolean isListened() {
    return false;
  }
}
