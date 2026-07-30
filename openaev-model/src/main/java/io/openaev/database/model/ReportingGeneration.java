package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UuidGenerator;

/**
 * A single execution of a {@link Reporting} template: tracks its lifecycle ({@link
 * ReportingGenerationStatus}), the requested {@link ReportingFormat}, and on success the produced
 * {@link Document}.
 */
@Getter
@Setter
@Entity
@Table(name = "reporting_generations")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ReportingGeneration implements TenantBase {

  @Id
  @Column(name = "reporting_generation_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("reporting_generation_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reporting_id", nullable = false)
  @JsonProperty("reporting_generation_reporting")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Reporting reporting;

  @Column(name = "reporting_generation_status")
  @JsonProperty("reporting_generation_status")
  @Enumerated(EnumType.STRING)
  private ReportingGenerationStatus status = ReportingGenerationStatus.PENDING;

  @Column(name = "reporting_generation_format")
  @JsonProperty("reporting_generation_format")
  @Enumerated(EnumType.STRING)
  private ReportingFormat format;

  @Column(name = "reporting_generation_trigger")
  @JsonProperty("reporting_generation_trigger")
  @Enumerated(EnumType.STRING)
  private ReportingGenerationTrigger generationTrigger = ReportingGenerationTrigger.MANUAL;

  /** Produced file; set when the generation reaches SUCCESS. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id")
  @JsonProperty("reporting_generation_document")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Document document;

  @Column(name = "reporting_generation_error")
  @JsonProperty("reporting_generation_error")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "reporting_generation_created_at", updatable = false)
  @JsonProperty("reporting_generation_created_at")
  private Instant createdAt;

  @Column(name = "reporting_generation_completed_at")
  @JsonProperty("reporting_generation_completed_at")
  private Instant completedAt;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Override
  @JsonIgnore
  public ResourceType getResourceType() {
    return ResourceType.REPORT;
  }
}
