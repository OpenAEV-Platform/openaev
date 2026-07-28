package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiIdListSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Recurring generation of a {@link Reporting} template: fires on a {@link ReportingSchedulePeriod}
 * (+ optional trigger time), runs under the owner's identity and delivers the produced document to
 * recipient users and/or raw external email addresses.
 */
@Getter
@Setter
@Entity
@Table(name = "reporting_schedules")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ReportingSchedule implements TenantBase {

  @Id
  @Column(name = "reporting_schedule_id", updatable = false, nullable = false)
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("reporting_schedule_id")
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reporting_id", nullable = false)
  @JsonProperty("reporting_schedule_reporting")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Reporting reporting;

  @Column(name = "reporting_schedule_name")
  @JsonProperty("reporting_schedule_name")
  private String name;

  @Column(name = "reporting_schedule_period")
  @JsonProperty("reporting_schedule_period")
  @NotNull
  @Enumerated(EnumType.STRING)
  private ReportingSchedulePeriod period;

  /**
   * UTC firing time of the schedule. Formats: DAY = {@code "HH:mm"}, WEEK = {@code "<1-7>-HH:mm"}
   * (ISO day of week), MONTH = {@code "<1-31>-HH:mm"}. HOUR schedules fire on the hour and ignore
   * it.
   */
  @Column(name = "reporting_schedule_time")
  @JsonProperty("reporting_schedule_time")
  private String triggerTime;

  @Column(name = "reporting_schedule_format")
  @JsonProperty("reporting_schedule_format")
  @Enumerated(EnumType.STRING)
  private ReportingFormat format = ReportingFormat.PDF;

  @Column(name = "reporting_schedule_enabled")
  @JsonProperty("reporting_schedule_enabled")
  private boolean enabled = true;

  /** Scheduled generations run under this user's identity. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  @JsonProperty("reporting_schedule_owner")
  @NotNull
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private User owner;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "reporting_schedules_users",
      joinColumns = @JoinColumn(name = "reporting_schedule_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonProperty("reporting_schedule_recipient_users")
  @Schema(implementation = String[].class)
  private List<User> recipientUsers = new ArrayList<>();

  /** Raw external email addresses (recipients without a platform account). */
  @Type(JsonType.class)
  @Column(name = "reporting_schedule_recipient_emails")
  @JsonProperty("reporting_schedule_recipient_emails")
  private List<String> recipientEmails = new ArrayList<>();

  @Column(name = "reporting_schedule_last_run_at")
  @JsonProperty("reporting_schedule_last_run_at")
  private Instant lastRunAt;

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "reporting_schedule_created_at", updatable = false)
  @JsonProperty("reporting_schedule_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "reporting_schedule_updated_at")
  @JsonProperty("reporting_schedule_updated_at")
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
