package io.openaev.database.model;

import static jakarta.persistence.FetchType.LAZY;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MultiModelSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A report template: an ordered list of {@link ReportingModule}s built around a subject ({@link
 * ReportingContextType} + optional context id), with branding overrides and a default output
 * format. Actual documents are produced as {@link ReportingGeneration}s, either manually or through
 * {@link ReportingSchedule}s.
 */
@Getter
@Setter
@Entity
@Table(name = "reportings")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Reporting implements TenantBase {

  @Id
  @Column(name = "reporting_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("reporting_id")
  @NotBlank
  private String id;

  @Column(name = "reporting_name", nullable = false)
  @JsonProperty("reporting_name")
  @NotBlank
  @Queryable(filterable = true, searchable = true, sortable = true)
  private String name;

  @Column(name = "reporting_description")
  @JsonProperty("reporting_description")
  private String description;

  @Column(name = "reporting_context_type", nullable = false)
  @JsonProperty("reporting_context_type")
  @NotNull
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  private ReportingContextType contextType;

  /** Id of the subject entity; null for PLATFORM-wide reports. */
  @Column(name = "reporting_context_id")
  @JsonProperty("reporting_context_id")
  @Queryable(filterable = true)
  private String contextId;

  @Type(JsonType.class)
  @Column(name = "reporting_modules")
  @JsonProperty("reporting_modules")
  private List<ReportingModule> modules = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "reporting_branding")
  @JsonProperty("reporting_branding")
  private ReportingBranding branding;

  @Column(name = "reporting_default_format")
  @JsonProperty("reporting_default_format")
  @Enumerated(EnumType.STRING)
  private ReportingFormat defaultFormat = ReportingFormat.PDF;

  @Column(name = "reporting_time_range")
  @JsonProperty("reporting_time_range")
  @Enumerated(EnumType.STRING)
  private ReportingTimeRange timeRange = ReportingTimeRange.LAST_30_DAYS;

  @OneToMany(mappedBy = "reporting", fetch = LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonProperty("reporting_generations")
  @JsonSerialize(using = MultiModelSerializer.class)
  @OrderBy("createdAt DESC")
  private List<ReportingGeneration> generations = new ArrayList<>();

  @OneToMany(mappedBy = "reporting", fetch = LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
  @JsonProperty("reporting_schedules")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<ReportingSchedule> schedules = new ArrayList<>();

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "reporting_created_at", updatable = false)
  @JsonProperty("reporting_created_at")
  @Queryable(sortable = true)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "reporting_updated_at")
  @JsonProperty("reporting_updated_at")
  @Queryable(sortable = true)
  private Instant updatedAt;

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
