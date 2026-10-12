package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A revocation received from the revocation list published with the releases. Platform-level on
 * purpose: a revoked version is revoked for every tenant. Single source of truth for the execution
 * check.
 */
@Getter
@Setter
@Entity
@Table(name = "content_revocations")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class ContentRevocation implements Base, Auditable {

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

  @Column(name = "content_revocation_external_id")
  @JsonProperty("content_revocation_external_id")
  @NotBlank
  private String externalId;

  @Column(name = "content_revocation_version")
  @JsonProperty("content_revocation_version")
  @NotBlank
  private String version;

  @Column(name = "content_revocation_reason")
  @JsonProperty("content_revocation_reason")
  private String reason;

  @Column(name = "content_revocation_list_serial")
  @JsonProperty("content_revocation_list_serial")
  @NotNull
  private Long listSerial;

  @Column(name = "content_revocation_published_at")
  @JsonProperty("content_revocation_published_at")
  private Instant publishedAt;

  // Set when a newer list no longer contains this revocation.
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
