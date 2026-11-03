package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/** A photo of the content of a catalogue scenario, never of its configuration (ADR-018). */
@Getter
@Setter
@Entity
@Table(name = "scenario_revisions")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ScenarioRevision implements TenantBase {

  public enum Reason {
    INSTALL,
    UPDATE,
    ROLLBACK,
    PAYLOAD_VERSION_CHANGE
  }

  @Id
  @Column(name = "scenario_revision_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scenario_id", updatable = false, nullable = false)
  private Scenario scenario;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Column(name = "scenario_revision_number", updatable = false)
  private int number;

  @Column(name = "scenario_revision_catalog_version", updatable = false)
  private String catalogVersion;

  @Column(name = "scenario_revision_reason", updatable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private Reason reason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "scenario_revision_author", updatable = false)
  private User author;

  @Column(name = "scenario_revision_created_at", updatable = false)
  @NotNull
  private Instant createdAt = Instant.now();

  @Type(JsonType.class)
  @Column(name = "scenario_revision_snapshot", columnDefinition = "jsonb", updatable = false)
  @NotNull
  private JsonNode snapshot;
}
