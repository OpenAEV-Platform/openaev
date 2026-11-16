package io.openaev.database.model.payload;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.model.CollectorType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogIdentity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** What every version of a payload shares. */
@Getter
@Setter
@Entity
@Table(name = "payload_identities")
@EntityListeners(AuditableListener.class)
public class PayloadIdentity implements CatalogIdentity, Auditable {

  // -- ID --

  @Id
  @Column(name = "payload_identity_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- ORIGIN --

  @Column(name = "payload_external_id")
  private String externalId;

  @Column(name = "payload_source")
  @Enumerated(EnumType.STRING)
  @NotNull
  private Payload.PAYLOAD_SOURCE source = Payload.PAYLOAD_SOURCE.MANUAL;

  // EAGER, as the payload serializes its name after the transaction has closed.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payload_collector_type")
  private CollectorType collectorType;

  // -- CATALOGUE --

  /** The catalogue repository that releases its versions; null for a local payload. */
  @Column(name = "payload_identity_repository", updatable = false)
  private String repository;

  /** The catalogue version shown; null once uninstalled, when the catalogue offers it again. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_identity_latest_version")
  @JsonIgnore
  private Payload latestVersion;

  public boolean isManaged() {
    return repository != null;
  }

  public boolean isUninstalled() {
    return isManaged() && latestVersion == null;
  }

  // -- AUDIT --

  @Column(name = "payload_identity_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "payload_identity_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
