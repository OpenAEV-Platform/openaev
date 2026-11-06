package io.openaev.database.model.catalog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** A release received from a content repository; the latest one tells a sync what is new. */
@Getter
@Setter
@Entity
@Table(name = "catalog_releases")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class CatalogRelease implements TenantBase, Auditable {

  @Id
  @Column(name = "catalog_release_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("catalog_release_id")
  @NotBlank
  private String id;

  // -- RELEASE --

  /** Content repository the release comes from, e.g. {@code filigran/scenarios}. */
  @Column(name = "catalog_release_repository", updatable = false)
  @JsonProperty("catalog_release_repository")
  @NotBlank
  private String repository;

  /** Release name, e.g. {@code 1.261104.0}; a release older than the latest one is refused. */
  @Column(name = "catalog_release_name", updatable = false)
  @JsonProperty("catalog_release_name")
  @NotBlank
  private String name;

  // -- AUDIT --

  /** When the release was received. */
  @Column(name = "catalog_release_created_at", updatable = false)
  @JsonProperty("catalog_release_created_at")
  @NotNull
  private Instant createdAt;

  @Column(name = "catalog_release_updated_at")
  @JsonProperty("catalog_release_updated_at")
  @NotNull
  private Instant updatedAt;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  public boolean isListened() {
    return false;
  }
}
