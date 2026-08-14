package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The CURRENT triage status of a {@link Finding} (1:1 - one row per finding). Every transition is
 * additionally recorded, append-only, in {@link FindingTriageHistory}: this entity is only ever
 * updated in place to reflect the latest state, never used as a history source.
 *
 * <p>Status persistence mirrors the newer "native Postgres enum" convention used by {@code
 * Workflow#status} / {@code WorkflowStatus} (see V4_72__Add_workflow_step_entities), not the older
 * plain-VARCHAR {@code @Enumerated(EnumType.STRING)} style used by e.g. {@code InjectStatus}.
 */
@Data
@Entity
@Table(name = "finding_triages")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class FindingTriage implements TenantBase {

  @Id
  @Column(name = "finding_triage_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("finding_triage_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_triage_finding_id", updatable = false, nullable = false)
  @JsonProperty("finding_triage_finding_id")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  @NotNull
  private Finding finding;

  @NotNull
  @Column(name = "finding_triage_status")
  @JsonProperty("finding_triage_status")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Current triage status of the finding")
  private FindingTriageStatus status = FindingTriageStatus.UNTRIAGED;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @CreationTimestamp
  @Column(name = "finding_triage_created_at", updatable = false, nullable = false)
  @JsonProperty("finding_triage_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "finding_triage_updated_at", nullable = false)
  @JsonProperty("finding_triage_updated_at")
  @NotNull
  private Instant updateDate = now();
}
