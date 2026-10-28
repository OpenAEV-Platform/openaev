package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * What every version of a payload shares (ADR-018): its namespace and external ID, source,
 * collector, default version and uninstall flag. The versions are the {@code payloads} rows.
 */
@Getter
@Setter
@Entity
@Table(name = "payload_identities")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
public class PayloadIdentity implements TenantBase, Auditable {

  @Id
  @Column(name = "identity_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // Repository the catalogue content comes from; null for a local payload.
  @Column(name = "payload_repository")
  private String repository;

  // On a local identity, an ID claimed by its content (main, zip): only used to promote it.
  @Column(name = "payload_external_id")
  private String externalId;

  @Column(name = "payload_source")
  @Enumerated(EnumType.STRING)
  @NotNull
  private Payload.PAYLOAD_SOURCE source = Payload.PAYLOAD_SOURCE.MANUAL;

  // EAGER, as on the payload: the name is serialized after the transaction has closed.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payload_collector_type")
  private CollectorType collectorType;

  @Column(name = "managed")
  private boolean managed;

  // Written by PayloadVersionService only: the highest non-revoked version, else null.
  @Column(name = "latest_payload_id")
  private String latestPayloadId;

  // F26: hidden, out of auto-install, never deleted.
  @Column(name = "uninstalled")
  private boolean uninstalled;

  // A variant: only the fields changed on its catalogue base. Null for any other payload.
  @Type(JsonType.class)
  @Column(name = "overrides", columnDefinition = "jsonb")
  private Map<String, Object> overrides;

  // The catalogue version a rebase could not merge alone.
  @Column(name = "conflict_target_payload_id")
  private String conflictTargetPayloadId;

  @Column(name = "identity_created_at", updatable = false)
  @NotNull
  private Instant createdAt = now();

  @Column(name = "identity_updated_at")
  @NotNull
  private Instant updatedAt = now();

  public boolean isVariant() {
    return overrides != null;
  }

  @Override
  public boolean isListened() {
    return false;
  }
}
