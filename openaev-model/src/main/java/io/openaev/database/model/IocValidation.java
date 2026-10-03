package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

/**
 * An OpenCTI IOC validation request handled by OpenAEV: the indicators disseminated to security
 * platforms, the benign validation scenario built for them, the approval decision and the outcome
 * of every (indicator, platform) pair.
 *
 * <p>Fully on v2 tenant isolation ({@code openaev.tenant.active-tables}): reads are scoped by the
 * statement inspector and every write sets the tenant explicitly, so there is no v1 {@code @Filter}
 * and no {@code TenantBaseListener}.
 *
 * <p>The scenario, simulation and decider are kept as plain ids (with database foreign keys set to
 * null on delete) rather than associations: the record must outlive a deleted scenario, and reading
 * it never needs to load the still-v1 scenario or exercise rows.
 *
 * <p>{@code lifecycleSyncedStatus} is an outbox marker: the status OpenCTI last acknowledged. The
 * background job reports every status that differs from it, so no OpenCTI call ever runs inside a
 * request transaction.
 */
@Entity
@Getter
@Setter
@Table(name = "ioc_validations")
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IocValidation implements TenantBase, Auditable {

  public static final int MAX_INDICATORS = 200;
  public static final int MAX_PLATFORMS = 10;

  /**
   * Category of every validation scenario and of the simulations launched from it. Coverage
   * statistics filter it out: a validation run proves a deployment, it does not measure coverage.
   */
  public static final String SCENARIO_CATEGORY = "ioc-validation";

  /** Tag of every validation scenario, so operators can find and filter them. */
  public static final String SCENARIO_TAG = "opencti: ioc validation";

  @Id
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Column(name = "ioc_validation_id")
  @JsonProperty("ioc_validation_id")
  @EqualsAndHashCode.Include
  @NotBlank
  private String id;

  @Column(name = "ioc_validation_external_id", nullable = false, updatable = false)
  @JsonProperty("ioc_validation_external_id")
  @Queryable(filterable = true)
  @NotBlank
  private String externalId;

  @Column(name = "ioc_validation_name", nullable = false)
  @JsonProperty("ioc_validation_name")
  @Queryable(searchable = true, filterable = true, sortable = true)
  @NotBlank
  private String name;

  @Column(name = "ioc_validation_description")
  @JsonProperty("ioc_validation_description")
  private String description;

  @Column(name = "ioc_validation_requested_by")
  @JsonProperty("ioc_validation_requested_by")
  @Queryable(searchable = true, filterable = true, sortable = true)
  private String requestedBy;

  @Column(name = "ioc_validation_status", nullable = false)
  @JsonProperty("ioc_validation_status")
  @Enumerated(EnumType.STRING)
  @Queryable(filterable = true, sortable = true)
  @NotNull
  private IocValidationStatus status = IocValidationStatus.AWAITING_APPROVAL;

  @Column(name = "ioc_validation_status_message")
  @JsonProperty("ioc_validation_status_message")
  private String statusMessage;

  @Type(JsonType.class)
  @Column(
      name = "ioc_validation_requested_test_kinds",
      columnDefinition = "jsonb",
      nullable = false)
  @JsonProperty("ioc_validation_requested_test_kinds")
  private List<IocValidationTestKind> requestedTestKinds = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "ioc_validation_allowed_test_kinds", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("ioc_validation_allowed_test_kinds")
  private List<IocValidationTestKind> allowedTestKinds = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "ioc_validation_iocs", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("ioc_validation_iocs")
  private List<IocValidationIoc> iocs = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "ioc_validation_pairs", columnDefinition = "jsonb", nullable = false)
  @JsonProperty("ioc_validation_pairs")
  private List<IocValidationPair> pairs = new ArrayList<>();

  @Column(name = "ioc_validation_iocs_count", nullable = false)
  @JsonProperty("ioc_validation_iocs_count")
  @Queryable(sortable = true)
  private int iocsCount;

  @Column(name = "ioc_validation_pairs_count", nullable = false)
  @JsonProperty("ioc_validation_pairs_count")
  @Queryable(sortable = true)
  private int pairsCount;

  @Column(name = "ioc_validation_prevented_count", nullable = false)
  @JsonProperty("ioc_validation_prevented_count")
  private int preventedCount;

  @Column(name = "ioc_validation_detected_count", nullable = false)
  @JsonProperty("ioc_validation_detected_count")
  private int detectedCount;

  @Column(name = "ioc_validation_missed_count", nullable = false)
  @JsonProperty("ioc_validation_missed_count")
  private int missedCount;

  @Column(name = "ioc_validation_error_count", nullable = false)
  @JsonProperty("ioc_validation_error_count")
  private int errorCount;

  @Column(name = "ioc_validation_scenario")
  @JsonProperty("ioc_validation_scenario")
  private String scenarioId;

  @Column(name = "ioc_validation_simulation")
  @JsonProperty("ioc_validation_simulation")
  private String simulationId;

  @Column(name = "ioc_validation_opencti_url")
  @JsonProperty("ioc_validation_opencti_url")
  private String openctiUrl;

  @Column(name = "ioc_validation_decided_by")
  @JsonProperty("ioc_validation_decided_by")
  private String decidedById;

  @Column(name = "ioc_validation_decided_by_name")
  @JsonProperty("ioc_validation_decided_by_name")
  private String decidedByName;

  @Column(name = "ioc_validation_decided_at")
  @JsonProperty("ioc_validation_decided_at")
  private Instant decidedAt;

  @Column(name = "ioc_validation_completed_at")
  @JsonProperty("ioc_validation_completed_at")
  private Instant completedAt;

  @Column(name = "ioc_validation_results_pushed_at")
  @JsonProperty("ioc_validation_results_pushed_at")
  private Instant resultsPushedAt;

  @Column(name = "ioc_validation_opencti_synced_status")
  @JsonProperty("ioc_validation_opencti_synced_status")
  @Enumerated(EnumType.STRING)
  private IocValidationStatus lifecycleSyncedStatus;

  @Column(name = "ioc_validation_created_at", nullable = false, updatable = false)
  @JsonProperty("ioc_validation_created_at")
  @Queryable(sortable = true)
  private Instant createdAt;

  @Column(name = "ioc_validation_updated_at", nullable = false)
  @JsonProperty("ioc_validation_updated_at")
  @Queryable(sortable = true)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.IOC_VALIDATION;

  /** Recomputes the denormalized counters from the IOC and pair lists. */
  public void refreshCounters() {
    this.iocsCount = iocs.size();
    this.pairsCount = pairs.size();
    this.preventedCount = countOutcome(IocValidationOutcome.PREVENTED);
    this.detectedCount = countOutcome(IocValidationOutcome.DETECTED);
    this.missedCount = countOutcome(IocValidationOutcome.MISSED);
    this.errorCount = countOutcome(IocValidationOutcome.ERROR);
  }

  private int countOutcome(IocValidationOutcome outcome) {
    return (int) pairs.stream().filter(pair -> outcome == pair.getOutcome()).count();
  }

  /** Large JSON rows are not streamed to every client; screens refetch the record. */
  @Override
  public boolean isListened() {
    return false;
  }
}
