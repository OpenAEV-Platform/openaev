package io.openaev.database.model.scenario;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogIdentity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/** A catalogue scenario in a tenant: kept once uninstalled, so the catalogue knows it. */
@Getter
@Setter
@Entity
@Table(name = "scenario_identities")
@EntityListeners(AuditableListener.class)
public class ScenarioIdentity implements CatalogIdentity, Auditable {

  // -- ID --

  @Id
  @Column(name = "scenario_identity_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  // -- TENANT --

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- CATALOGUE --

  @Column(name = "scenario_identity_repository", updatable = false)
  @NotNull
  private String repository;

  @Column(name = "scenario_identity_external_id", updatable = false)
  @NotNull
  private String externalId;

  // -- AUDIT --

  @Column(name = "scenario_identity_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "scenario_identity_updated_at")
  @NotNull
  private Instant updatedAt = now();
}
