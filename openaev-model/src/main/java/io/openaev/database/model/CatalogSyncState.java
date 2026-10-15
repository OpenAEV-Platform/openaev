package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * Last release applied per repository and channel, so a sync knows what is new. Without signing it
 * is not an anti-rollback control (RFC 0004 §1.1).
 */
@Getter
@Setter
@Entity
@Table(name = "catalog_sync_state")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class CatalogSyncState implements TenantBase, Auditable {

  @Id
  @Column(name = "catalog_sync_state_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("catalog_sync_state_id")
  @NotBlank
  private String id;

  @Column(name = "catalog_sync_state_repository", updatable = false)
  @JsonProperty("catalog_sync_state_repository")
  @NotBlank
  private String repository;

  @Column(name = "catalog_sync_state_channel", updatable = false)
  @JsonProperty("catalog_sync_state_channel")
  @NotBlank
  private String channel;

  @Column(name = "catalog_sync_state_release")
  @JsonProperty("catalog_sync_state_release")
  private String release;

  @Column(name = "catalog_sync_state_created_at", updatable = false)
  @JsonProperty("catalog_sync_state_created_at")
  @NotNull
  private Instant createdAt;

  @Column(name = "catalog_sync_state_updated_at")
  @JsonProperty("catalog_sync_state_updated_at")
  @NotNull
  private Instant updatedAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  public boolean isListened() {
    return false;
  }
}
