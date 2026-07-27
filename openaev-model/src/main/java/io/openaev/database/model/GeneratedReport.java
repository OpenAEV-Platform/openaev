package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A structured PDF report generated from one of the two fixed templates (TECHNICAL, EXECUTIVE) for
 * a given simulation (exercise). The PDF itself is assembled client-side (reusing the existing
 * dashboard widget system and pdfmake export pipeline) and, once built, uploaded and stored as a
 * regular {@link Document} (MinIO). This entity only tracks generation metadata, status and
 * traceability (who generated it, when, from which template) so past reports can be listed and
 * re-downloaded.
 */
@Getter
@Setter
@Entity
@Table(name = "generated_reports")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class GeneratedReport implements TenantBase {

  @Id
  @Column(name = "generated_report_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("generated_report_id")
  @NotBlank
  private String id;

  @Column(name = "generated_report_template", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("generated_report_template")
  @NotNull
  private GeneratedReportTemplate template;

  @Column(name = "generated_report_status", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("generated_report_status")
  @NotNull
  private GeneratedReportStatus status = GeneratedReportStatus.PENDING;

  @Column(name = "generated_report_error_message")
  @JsonProperty("generated_report_error_message")
  private String errorMessage;

  @Column(name = "generated_report_trigger_source", nullable = false)
  @Enumerated(EnumType.STRING)
  @JsonProperty("generated_report_trigger_source")
  @NotNull
  private GeneratedReportTriggerSource triggerSource = GeneratedReportTriggerSource.MANUAL;

  @Column(name = "generated_report_label")
  @JsonProperty("generated_report_label")
  private String label;

  // -- RELATIONS --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "generated_report_exercise")
  @JsonProperty("generated_report_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  private Exercise exercise;

  /**
   * Set (instead of {@code exercise}) for a Scenario report, aggregating every run of this scenario
   * within the requested comparison window.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "generated_report_scenario")
  @JsonProperty("generated_report_scenario")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  private Scenario scenario;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "generated_report_document")
  @JsonProperty("generated_report_document")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  private Document document;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "generated_report_created_by")
  @JsonProperty("generated_report_created_by")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(implementation = String.class)
  private User createdBy;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "generated_report_created_at", updatable = false, nullable = false)
  @JsonProperty("generated_report_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "generated_report_updated_at", nullable = false)
  @JsonProperty("generated_report_updated_at")
  @NotNull
  private Instant updateDate = now();

  @Override
  public String toString() {
    if (scenario != null) {
      return template + " report for scenario " + scenario.getId();
    }
    return template + " report for exercise " + (exercise != null ? exercise.getId() : "");
  }
}
